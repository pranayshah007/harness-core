/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.LOG_FEEDBACK_HISTORY_RESOURCE_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.LOG_FEEDBACK_ID_RESOURCE_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.LOG_FEEDBACK_RESOURCE_PATH;

import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.LogFeedbackHistory;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.LogFeedbackService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
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
  @ApiOperation(value = "saves log data collected for verification", nickname = "saveLogFeedback")
  public RestResponse<LogFeedback> saveLogFeedback(
      @BeanParam @Valid ProjectPathParams projectParams, @NotNull @Valid @Body LogFeedback logFeedback) {
    return new RestResponse<>(logFeedbackService.create(projectParams, logFeedback));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @Path(LOG_FEEDBACK_ID_RESOURCE_PATH)
  @ApiOperation(value = "saves log data collected for verification", nickname = "updateLogFeedback")
  public RestResponse<LogFeedback> updateLogFeedback(@BeanParam @Valid ProjectPathParams projectParams,
      @PathParam(CVNextGenConstants.LOG_FEEDBACK_ID) @NonNull String logFeedbackId,
      @NotNull @Valid @Body io.harness.cvng.core.beans.LogFeedback logFeedback) {
    return new RestResponse<>(logFeedbackService.update(projectParams, logFeedbackId, logFeedback));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path(LOG_FEEDBACK_ID_RESOURCE_PATH)
  @ApiOperation(value = "saves log data collected for verification", nickname = "getLogFeedback")
  public RestResponse<LogFeedback> getLogFeedback(@BeanParam @Valid ProjectPathParams projectParams,
      @PathParam(CVNextGenConstants.LOG_FEEDBACK_ID) @NonNull String logFeedbackId) {
    return new RestResponse<>(logFeedbackService.get(projectParams, logFeedbackId));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @Path(LOG_FEEDBACK_ID_RESOURCE_PATH)
  @ApiOperation(value = "saves log data collected for verification", nickname = "deleteLogFeedback")
  public RestResponse<Boolean> deleteLogFeedback(@BeanParam @Valid ProjectPathParams projectParams,
      @PathParam(CVNextGenConstants.LOG_FEEDBACK_ID) @NonNull String logFeedbackId) {
    return new RestResponse<>(logFeedbackService.delete(projectParams, logFeedbackId));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path(LOG_FEEDBACK_HISTORY_RESOURCE_PATH)
  @ApiOperation(value = "saves log data collected for verification", nickname = "getFeedbackHistory")
  public RestResponse<List<LogFeedbackHistory>> getFeedbackHistory(@BeanParam @Valid ProjectPathParams projectParams,
      @PathParam(CVNextGenConstants.LOG_FEEDBACK_ID) @NonNull String logFeedbackId) {
    return new RestResponse<>(logFeedbackService.history(projectParams, logFeedbackId));
  }
}