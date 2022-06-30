package uk.gov.di.ipv.cri.passport.library.helpers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.lambda.powertools.logging.LoggingUtils;
import uk.gov.di.ipv.cri.passport.library.annotations.ExcludeFromGeneratedCoverageReport;

@ExcludeFromGeneratedCoverageReport
public class LogHelper {
    private static final Logger LOGGER = LogManager.getLogger();

    private LogHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static final String CLIENT_ID_LOG_FIELD = "client-id";
    public static final String SESSION_ID_LOG_FIELD = "session-id";
    public static final String COMPONENT_ID_LOG_FIELD = "component-id";
    public static final String COMPONENT_ID = "passport-cri";

    public static void attachComponentIdToLogs() {
        attachFieldToLogs(COMPONENT_ID_LOG_FIELD, COMPONENT_ID);
    }

    public static void attachClientIdToLogs(String clientId) {
        attachFieldToLogs(CLIENT_ID_LOG_FIELD, clientId);
    }

    public static void attachSessionIdToLogs(String sessionId) {
        attachFieldToLogs(SESSION_ID_LOG_FIELD, sessionId);
    }

    private static void attachFieldToLogs(String field, String value) {
        LoggingUtils.appendKey(field, value);
        LOGGER.info("{} attached to logs", field);
    }
}
