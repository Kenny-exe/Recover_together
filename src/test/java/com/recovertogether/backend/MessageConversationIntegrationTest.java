package com.recovertogether.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.recovertogether.backend.entity.Message;
import com.recovertogether.backend.entity.PartnerRequest;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.PartnerRequestStatus;
import com.recovertogether.backend.repository.MessageRepository;
import com.recovertogether.backend.repository.PartnerRequestRepository;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-database regression test for conversation history pagination.
 *
 * The old implementation loaded the whole thread into memory, sorted it, and
 * trimmed it afterwards, and marked every received message read via saveAll.
 * The service now pages at the database level with a cursor on id and marks
 * unread messages read with a single bulk update. Running against the real
 * schema (with the V9 composite indexes) is the only way to prove the window,
 * the chronological ordering, and the unread bookkeeping behave correctly.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MessageConversationIntegrationTest {

    private static final int TOTAL_MESSAGES = 120;
    private static final int SENT_BY_ME = 60;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartnerRequestRepository partnerRequestRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private User me;
    private User partner;
    private User stranger;
    private String token;
    private String strangerToken;
    private Long partnerId;
    private List<Long> allMessageIds;

    @BeforeEach
    void setUp() {
        String suffix = System.nanoTime() + "";

        me = new User();
        me.setName("Conversation Me");
        me.setEmail("msg_me_" + suffix + "@example.com");
        me.setPassword(passwordEncoder.encode("Password123!"));
        me = userRepository.save(me);

        partner = new User();
        partner.setName("Conversation Partner");
        partner.setEmail("msg_partner_" + suffix + "@example.com");
        partner.setPassword(passwordEncoder.encode("Password123!"));
        partner = userRepository.save(partner);

        stranger = new User();
        stranger.setName("Conversation Stranger");
        stranger.setEmail("msg_stranger_" + suffix + "@example.com");
        stranger.setPassword(passwordEncoder.encode("Password123!"));
        stranger = userRepository.save(stranger);
        partnerId = partner.getId();

        PartnerRequest accepted = new PartnerRequest();
        accepted.setSender(me);
        accepted.setReceiver(partner);
        accepted.setStatus(PartnerRequestStatus.ACCEPTED);
        partnerRequestRepository.save(accepted);

        List<Message> messages = new ArrayList<>(TOTAL_MESSAGES);
        for (int i = 1; i <= SENT_BY_ME; i++) {
            messages.add(newMessage(me, partner, "me-" + i));
        }
        for (int i = 1; i <= TOTAL_MESSAGES - SENT_BY_ME; i++) {
            messages.add(newMessage(partner, me, "partner-" + i));
        }
        // saveAll returns the persisted entities in insertion order, and
        // identity ids are monotonic, so index k always maps to the k-th
        // inserted message's id.
        allMessageIds = messageRepository.saveAll(messages).stream()
                .map(Message::getId)
                .toList();

        token = jwtService.generateToken(me.getEmail());
        strangerToken = jwtService.generateToken(stranger.getEmail());
    }

    private Message newMessage(User sender, User receiver, String content) {
        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(content);
        return message;
    }

    @AfterEach
    void tearDown() {
        List<Long> userIds = List.of(me, partner, stranger).stream()
                .filter(u -> u != null && u.getId() != null)
                .map(User::getId)
                .toList();
        if (userIds.isEmpty()) {
            return;
        }

        Object[] idArgs = userIds.toArray();
        Object[] twiceIds = new Object[idArgs.length * 2];
        System.arraycopy(idArgs, 0, twiceIds, 0, idArgs.length);
        System.arraycopy(idArgs, 0, twiceIds, idArgs.length, idArgs.length);
        String placeholders = userIds.stream().map(u -> "?").collect(java.util.stream.Collectors.joining(", "));

        jdbcTemplate.update("DELETE FROM messages WHERE sender_id IN (" + placeholders + ") OR "
                + "receiver_id IN (" + placeholders + ")", twiceIds);
        jdbcTemplate.update(
                "DELETE FROM partner_requests WHERE sender_id IN (" + placeholders + ") "
                        + "OR receiver_id IN (" + placeholders + ")",
                twiceIds);
        jdbcTemplate.update("DELETE FROM audit_logs WHERE user_id IN (" + placeholders + ")", idArgs);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (" + placeholders + ")", idArgs);
    }

    /** Fetches a conversation page with the given query parameters. */
    private MvcResult getConversation(String token, String... params) throws Exception {
        return mockMvc.perform(get("/messages/conversation/" + partnerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .params(params(params)))
                .andReturn();
    }

    private org.springframework.util.MultiValueMap<String, String> params(String... params) {
        org.springframework.util.LinkedMultiValueMap<String, String> map =
                new org.springframework.util.LinkedMultiValueMap<>();
        for (int i = 0; i + 1 < params.length; i += 2) {
            map.add(params[i], params[i + 1]);
        }
        return map;
    }

    private List<Map<String, Object>> readPage(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<List<Map<String, Object>>>() {});
    }

    private List<Long> ids(List<Map<String, Object>> page) {
        return page.stream()
                .map(row -> ((Number) row.get("id")).longValue())
                .toList();
    }

    /** The content string for insertion index {@code index}. */
    private String contentForIndex(int index) {
        return index < SENT_BY_ME ? "me-" + (index + 1) : "partner-" + (index - SENT_BY_ME + 1);
    }

    private List<String> expectedContents(int startInclusive, int endExclusive) {
        List<String> expected = new ArrayList<>();
        for (int i = startInclusive; i < endExclusive; i++) {
            expected.add(contentForIndex(i));
        }
        return expected;
    }

    private List<String> contents(List<Map<String, Object>> page) {
        return page.stream().map(row -> (String) row.get("content")).toList();
    }

    private long unreadCount(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/messages/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {});
        return ((Number) body.get("unreadCount")).longValue();
    }

    @Test
    @DisplayName("Default window returns the latest 50 messages chronologically and marks them read")
    void testDefaultWindowIsLatest50ChronologicalAndMarksRead() throws Exception {
        assertEquals(SENT_BY_ME, unreadCount(token), "the partner's messages start unread");

        MvcResult result = mockMvc.perform(get("/messages/conversation/" + partnerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(50)))
                .andExpect(jsonPath("$[0].content").value(contentForIndex(TOTAL_MESSAGES - 50)))
                .andExpect(jsonPath("$[49].content").value(contentForIndex(TOTAL_MESSAGES - 1)))
                .andReturn();

        List<Map<String, Object>> page = readPage(result);
        assertEquals(allMessageIds.subList(TOTAL_MESSAGES - 50, TOTAL_MESSAGES), ids(page),
                "default page must be the newest 50 messages");
        assertEquals(expectedContents(TOTAL_MESSAGES - 50, TOTAL_MESSAGES), contents(page),
                "page must be chronological, oldest to newest");

        assertEquals(0, unreadCount(token),
                "opening the thread marks the partner's messages read");
    }

    @Test
    @DisplayName("Cursor pagination walks older history without revisiting messages")
    void testCursorPaginationWalksHistory() throws Exception {
        List<Long> pageOneIds = ids(readPage(getConversation(token)));

        assertEquals(allMessageIds.subList(70, 120), pageOneIds, "page one is the latest window");

        Long cursor = pageOneIds.get(0);
        List<Map<String, Object>> pageTwo = readPage(getConversation(token,
                "beforeId", String.valueOf(cursor)));
        assertEquals(allMessageIds.subList(20, 70), ids(pageTwo),
                "the second page is the next older window");
        assertEquals(expectedContents(20, 70), contents(pageTwo));

        Long secondCursor = ids(pageTwo).get(0);
        List<Map<String, Object>> pageThree = readPage(getConversation(token,
                "beforeId", String.valueOf(secondCursor)));
        assertEquals(allMessageIds.subList(0, 20), ids(pageThree),
                "the third page holds the oldest remainder");
        assertEquals(expectedContents(0, 20), contents(pageThree));

        // The cursor is exclusive: paging past the oldest message returns nothing.
        mockMvc.perform(get("/messages/conversation/" + partnerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("beforeId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Oversized limit is clamped to 200")
    void testLimitClampReturnsWholeThreadWhenSmall() throws Exception {
        List<Map<String, Object>> clamped = readPage(getConversation(token, "limit", "10000"));
        assertEquals(allMessageIds, ids(clamped),
                "limit is clamped to 200, returning the whole thread here");

        assertEquals(0, unreadCount(token),
                "a fetched thread marks the partner's messages read");
    }

    @Test
    @DisplayName("A zero limit is rejected with 400 and marks nothing read")
    void testZeroLimitRejectedWithoutMarkingRead() throws Exception {
        mockMvc.perform(get("/messages/conversation/" + partnerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Limit must be at least 1"));

        assertEquals(SENT_BY_ME, unreadCount(token),
                "rejected requests must not mark anything read");
    }

    @Test
    @DisplayName("A stranger cannot page through the conversation (403)")
    void testStrangerCannotViewThread() throws Exception {
        mockMvc.perform(get("/messages/conversation/" + partnerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .param("limit", "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You can only view conversations with your partner"));

        assertEquals(SENT_BY_ME, unreadCount(token),
                "denied requests must not mark anything read");
    }
}
