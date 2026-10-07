package net.onelitefeather.otis.problem;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import org.zalando.problem.Problem;
import org.zalando.problem.StatusType;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire representation of a problem. Writes the extension members as top-level members of the JSON
 * object, as RFC 9457 requires, instead of nesting them under {@code parameters}.
 */
@Serdeable.Serializable
public final class ProblemResponse implements Problem {

    private final Problem problem;
    private final Map<String, Object> extensions;

    /**
     * @param problem    the problem to render
     * @param extensions the extension members to render, replacing the problem's own parameters
     */
    public ProblemResponse(Problem problem, Map<String, Object> extensions) {
        this.problem = problem;
        this.extensions = Map.copyOf(new LinkedHashMap<>(extensions));
    }

    @Override
    public URI getType() {
        return problem.getType();
    }

    @Override
    public String getTitle() {
        return problem.getTitle();
    }

    @JsonIgnore
    @Override
    public StatusType getStatus() {
        return problem.getStatus();
    }

    /**
     * @return the numeric HTTP status code, written as the {@code status} member
     */
    @JsonProperty("status")
    public Integer getStatusCode() {
        StatusType status = problem.getStatus();
        return status == null ? null : status.getStatusCode();
    }

    @Override
    public String getDetail() {
        return problem.getDetail();
    }

    @Override
    public URI getInstance() {
        return problem.getInstance();
    }

    @JsonAnyGetter
    @Override
    public Map<String, Object> getParameters() {
        return extensions;
    }
}
