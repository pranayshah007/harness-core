/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.resources;

import static io.harness.cvng.analysis.CVAnalysisConstants.LEARNING_RESOURCE;
import static io.harness.cvng.analysis.CVAnalysisConstants.MARK_FAILURE_PATH;

import io.harness.cvng.analysis.beans.ExceptionInfo;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api(LEARNING_RESOURCE)
@Path(LEARNING_RESOURCE)
@Produces("application/json")
public class LearningEngineTaskResource {
  @Inject LearningEngineTaskService learningEngineTaskService;

  @GET
  @Path("next-cv-task")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @ApiOperation(value = "get next LE task to be processed", nickname = "getNextLearningTask")
  public RestResponse<LearningEngineTask> getNextTask(@QueryParam("taskTypes") List<LearningEngineTaskType> taskTypes) {
    if (taskTypes == null) {
      return new RestResponse<>(learningEngineTaskService.getNextAnalysisTask());
    }
    return new RestResponse<>(learningEngineTaskService.getNextAnalysisTask(taskTypes));
  }

  @POST
  @Path(MARK_FAILURE_PATH)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @ApiOperation(value = "mark task failed", nickname = "markLearningTaskFailure")
  public RestResponse<Boolean> markFailure(@QueryParam("taskId") String taskId, @Body ExceptionInfo exceptionInfo) {
    learningEngineTaskService.markFailure(taskId, exceptionInfo);
    return new RestResponse<>(true);
  }
}
