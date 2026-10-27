package com.groupcc2.recruitment.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NotificationDeliveryDisabledLogger {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryDisabledLogger.class);

    @EventListener(ApplicationReadyEvent.class)
    public void reportDisabled() {
        log.warn("Application email delivery is disabled; notification events will remain queued");
    }
}
