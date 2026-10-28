-- V9__add_message_conversation_indexes.sql
-- Conversation history is paginated at the database level with a cursor on
-- (direction, id). These composite indexes back the conversation window query
-- and the thread-scoped mark-as-read update in both directions.

CREATE INDEX idx_message_conversation_sent
    ON messages (sender_id, receiver_id, id);

CREATE INDEX idx_message_conversation_received
    ON messages (receiver_id, sender_id, id);
