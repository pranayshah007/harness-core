package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.LOG_FEEDBACK_HISTORY_RESOURCE_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.LOG_FEEDBACK_ID_RESOURCE_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.LOG_FEEDBACK_RESOURCE_PATH;

import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.LogFeedbackHistory;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectParams.ProjectParamsBuilder;
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
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
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
  @ApiOperation(value = "saves log data collected for verification", nickname = "saveLogFeedback")
  public RestResponse<LogFeedback> saveLogFeedback(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @NotNull @Valid @Body LogFeedback logFeedback) {
    ProjectParamsBuilder projectParamsBuilder = ProjectParams.builder()
                                                    .accountIdentifier(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier);
    return new RestResponse<>(logFeedbackService.create(projectParamsBuilder.build(), "", logFeedback));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @Path(LOG_FEEDBACK_ID_RESOURCE_PATH)
  @ApiOperation(value = "saves log data collected for verification", nickname = "updateLogFeedback")
  public RestResponse<LogFeedback> updateLogFeedback(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @PathParam(CVNextGenConstants.LOG_FEEDBACK_ID) @NonNull String logFeedbackId,
      @NotNull @Valid @Body io.harness.cvng.core.beans.LogFeedback logFeedback) {
    ProjectParamsBuilder projectParamsBuilder = ProjectParams.builder()
                                                    .accountIdentifier(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier);
    return new RestResponse<>(logFeedbackService.update(projectParamsBuilder.build(), logFeedbackId, logFeedback));
  }

  @GET
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @Path(LOG_FEEDBACK_ID_RESOURCE_PATH)
  @ApiOperation(value = "saves log data collected for verification", nickname = "getLogFeedback")
  public RestResponse<LogFeedback> getLogFeedback(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @PathParam(CVNextGenConstants.LOG_FEEDBACK_ID) @NonNull String logFeedbackId) {
    ProjectParamsBuilder projectParamsBuilder = ProjectParams.builder()
                                                    .accountIdentifier(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier);
    return new RestResponse<>(logFeedbackService.get(projectParamsBuilder.build(), logFeedbackId));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @Path(LOG_FEEDBACK_ID_RESOURCE_PATH)
  @ApiOperation(value = "saves log data collected for verification", nickname = "deleteLogFeedback")
  public RestResponse<Boolean> deleteLogFeedback(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @PathParam(CVNextGenConstants.LOG_FEEDBACK_ID) @NonNull String logFeedbackId) {
    ProjectParamsBuilder projectParamsBuilder = ProjectParams.builder()
                                                    .accountIdentifier(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier);
    return new RestResponse<>(logFeedbackService.delete(projectParamsBuilder.build(), "", logFeedbackId));
  }

  @GET
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @Path(LOG_FEEDBACK_HISTORY_RESOURCE_PATH)
  @ApiOperation(value = "saves log data collected for verification", nickname = "getFeedbackHistory")
  public RestResponse<List<LogFeedbackHistory>> getFeedbackHistory(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @PathParam(CVNextGenConstants.LOG_FEEDBACK_ID) @NonNull String logFeedbackId) {
    ProjectParamsBuilder projectParamsBuilder = ProjectParams.builder()
                                                    .accountIdentifier(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier);
    return new RestResponse<>(logFeedbackService.history(projectParamsBuilder.build(), logFeedbackId));
  }
}
