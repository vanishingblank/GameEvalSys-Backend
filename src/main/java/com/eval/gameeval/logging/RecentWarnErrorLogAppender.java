package com.eval.gameeval.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class RecentWarnErrorLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_CONTENT_LENGTH = 240;

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (eventObject == null || eventObject.getLevel() == null) {
            return;
        }

        if (!eventObject.getLevel().isGreaterOrEqual(Level.WARN)) {
            return;
        }

        String content = buildContent(eventObject);
        RecentWarnErrorLogStore.add(new RecentWarnErrorLogStore.LogEntry(
                eventObject.getTimeStamp(),
                eventObject.getLevel().toString(),
                content
        ));
    }

    private String buildContent(ILoggingEvent eventObject) {
        StringBuilder builder = new StringBuilder();
        String formattedMessage = eventObject.getFormattedMessage();
        if (formattedMessage != null && !formattedMessage.isBlank()) {
            builder.append(formattedMessage);
        } else {
            builder.append(eventObject.getLoggerName() == null ? "unknown" : eventObject.getLoggerName());
        }

        if (eventObject.getThrowableProxy() != null) {
            builder.append(" | ");
            builder.append(eventObject.getThrowableProxy().getClassName());
            String throwableMessage = eventObject.getThrowableProxy().getMessage();
            if (throwableMessage != null && !throwableMessage.isBlank()) {
                builder.append(": ");
                builder.append(throwableMessage);
            }
        }

        String content = builder.toString();
        return content.length() > MAX_CONTENT_LENGTH
                ? content.substring(0, MAX_CONTENT_LENGTH - 3) + "..."
                : content;
    }
}
