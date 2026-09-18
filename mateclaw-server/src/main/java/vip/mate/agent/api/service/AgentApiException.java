package vip.mate.agent.api.service;

import lombok.Getter;

import java.util.Map;

/** Stable error contract for unauthenticated public expert API calls. */
@Getter
public class AgentApiException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;
    private final Map<String, Object> details;

    public AgentApiException(int httpStatus, String errorCode, String message) {
        this(httpStatus, errorCode, message, Map.of());
    }

    public AgentApiException(int httpStatus, String errorCode, String message,
                             Map<String, Object> details) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static AgentApiException invalid(String message) {
        return new AgentApiException(400, "agent_api_invalid_request", message);
    }

    public static AgentApiException unauthorized() {
        return new AgentApiException(401, "agent_api_unauthorized", "Invalid API key");
    }

    public static AgentApiException notPublished() {
        return new AgentApiException(403, "agent_api_not_published", "Agent API is not published");
    }

    public static AgentApiException upstream(String message) {
        return new AgentApiException(502, "agent_api_upstream_error", message);
    }
}
