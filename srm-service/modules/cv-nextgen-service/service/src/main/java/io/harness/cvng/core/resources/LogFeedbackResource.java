package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.LOG_FEEDBACK_RESOURCE_PATH;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.beans.LogRecordDTO;
import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.LogFeedbackService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.NonNull;
import retrofit2.http.Body;

@Api("log-feedback")
@Path(LOG_FEEDBACK_RESOURCE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
public class LogFeedbackResource {
  @Inject private LogFeedbackService logFeedbackService;

  @POST
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "saves log data collected for verification", nickname = "saveLogRecords")
  public RestResponse<LogFeedback> saveLogRecords(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @NotNull @Valid @Body io.harness.cvng.core.beans.LogFeedback logFeedback) {
    ProjectParams.ProjectParamsBuilder projectParamsBuilder = ProjectParams.builder()
                                                                  .accountIdentifier(accountIdentifier)
                                                                  .orgIdentifier(orgIdentifier)
                                                                  .projectIdentifier(projectIdentifier);
    return new RestResponse<>(logFeedbackService.create(projectParamsBuilder.build(), "", logFeedback));
  }
}
