package com.recovertogether.backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.entity.UserDevice;
import com.recovertogether.backend.repository.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Pushes notifications to every active registered device of a user through
 * Firebase Cloud Messaging. Safe no-op when no service-account credentials
 * are configured (fcm.credentials-path), so the rest of the application
 * does not need to know whether push is enabled.
 */
@Service
public class FcmPushService implements PushNotificationService
{
    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);

    private final UserDeviceRepository userDeviceRepository;
    private final String credentialsPath;

    private volatile FirebaseMessaging messaging;

    public FcmPushService(UserDeviceRepository userDeviceRepository,
                          @Value("${fcm.credentials-path:}") String credentialsPath)
    {
        this.userDeviceRepository = userDeviceRepository;
        this.credentialsPath = credentialsPath;
    }

    @Override
    public void push(User receiver, String title, String body)
    {
        try
        {
            List<UserDevice> devices = userDeviceRepository.findByUserAndActiveTrue(receiver);
            if (devices.isEmpty())
            {
                log.debug("No active push devices for user {}", receiver.getEmail());
                return;
            }

            FirebaseMessaging messaging = obtainMessaging();
            if (messaging == null)
            {
                log.info("Push not configured (fcm.credentials-path unset); skipping push to {} device(s) for {}",
                        devices.size(), receiver.getEmail());
                return;
            }

            for (UserDevice device : devices)
            {
                sendToDevice(messaging, device, title, body);
            }
        }
        catch (Exception e)
        {
            // Push is best-effort: never break the caller's transaction.
            log.warn("Push fan-out failed for user {}: {}", receiver.getEmail(), e.getMessage());
        }
    }

    /**
     * Lazily initializes Firebase from the service-account credentials file.
     * Public so tests can override; callers only rely on nullable semantics.
     * Returns null when push is not configured.
     */
    public synchronized FirebaseMessaging obtainMessaging()
    {
        if (messaging != null)
        {
            return messaging;
        }

        if (credentialsPath == null || credentialsPath.isBlank())
        {
            return null;
        }

        try (InputStream credentialsStream = new FileInputStream(credentialsPath))
        {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();

            if (FirebaseApp.getApps().isEmpty())
            {
                FirebaseApp.initializeApp(options);
            }

            messaging = FirebaseMessaging.getInstance();
            log.info("FCM push notifications initialized");
            return messaging;
        }
        catch (Exception e)
        {
            log.error("Failed to initialize FCM from {}: {}", credentialsPath, e.getMessage());
            return null;
        }
    }

    private void sendToDevice(FirebaseMessaging messaging, UserDevice device, String title, String body)
    {
        try
        {
            Message message = Message.builder()
                    .setToken(device.getToken())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("platform", device.getPlatform().name())
                    .build();

            messaging.send(message);
            log.debug("Push sent to device id={} ({})", device.getId(), device.getPlatform());
        }
        catch (FirebaseMessagingException e)
        {
            // Tokens go stale on app uninstall or reinstall: deactivate
            // unregistered devices so they stop wasting rate quota.
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED)
            {
                device.setActive(false);
                userDeviceRepository.save(device);
                log.info("Deactivated stale push device id={} ({}) for user {}",
                        device.getId(), device.getPlatform(), device.getUser().getEmail());
            }
            else
            {
                log.warn("FCM send failed for device id={}: {}", device.getId(), e.getMessage());
            }
        }
        catch (Exception e)
        {
            log.warn("Unexpected push failure for device id={}: {}", device.getId(), e.getMessage());
        }
    }
}
