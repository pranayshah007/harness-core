package io.harness.delegate.task.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenExceptionUtils.throwRefreshTokenException;

import io.harness.annotations.dev.OwnedBy;
import io.harness.jackson.JsonNodeUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class ServiceNowErrorResponse {
  String status;
  String errorMessage;

  public ServiceNowErrorResponse(JsonNode node) throws JsonProcessingException {
    String status = JsonNodeUtils.getString(node, "status");
    JsonNode jsonNode2 = node.get("error");
    ServiceNowError serviceNowErrorResponse = new ServiceNowError(jsonNode2);
    String parsedErrorDetails = serviceNowErrorResponse.getErrorMessage();
    if (!StringUtils.isBlank(status)) {
      this.status = status;
      this.errorMessage = parsedErrorDetails;
      return;
    }

    throwRefreshTokenException("error response doesn't have \"error\" or \"errorCode\" field");
  }

  public String getFormattedError() {
    String formattedError = this.getStatus();
    if (!StringUtils.isBlank(this.getErrorMessage())) {
      formattedError = String.format("[%s] : %s", this.getStatus(), this.getErrorMessage());
    }
    return formattedError;
  }
}