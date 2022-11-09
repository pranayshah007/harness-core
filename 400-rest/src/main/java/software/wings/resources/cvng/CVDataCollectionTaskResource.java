/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.cvng;

import static io.harness.cvng.core.services.CVNextGenConstants.CV_DATA_COLLECTION_PATH;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.beans.CVNGPerpetualTaskDTO;
import io.harness.cvng.beans.DataCollectionConnectorBundle;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.perpetualtask.CVDataCollectionTaskService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@Api(CV_DATA_COLLECTION_PATH)
@Path(CV_DATA_COLLECTION_PATH)
@Produces("application/json")
@Slf4j
@LearningEngineAuth
@ExposeInternalException(withStackTrace = true)
public class CVDataCollectionTaskResource {
  @Inject private CVDataCollectionTaskService dataCollectionTaskService;

  @POST
  @Path("create-task")
  @Timed
  @ExceptionMetered
  public RestResponse<String> createTask(@NotNull @QueryParam("accountId") String accountId,
      @NotNull @QueryParam("orgIdentifier") String orgIdentifier,
      @NotNull @QueryParam("projectIdentifier") String projectIdentifier, @Body DataCollectionConnectorBundle bundle) {
    return new RestResponse<>(dataCollectionTaskService.create(accountId, orgIdentifier, projectIdentifier, bundle));
  }

  @POST
  @Path("reset-task")
  @Timed
  @ExceptionMetered
  public RestResponse<Void> resetTask(@NotNull @QueryParam("accountId") String accountId,
      @NotNull @QueryParam("orgIdentifier") String orgIdentifier,
      @NotNull @QueryParam("projectIdentifier") String projectIdentifier, @NotNull @QueryParam("taskId") String taskId,
      @Body DataCollectionConnectorBundle bundle) {
    dataCollectionTaskService.resetTask(accountId, orgIdentifier, projectIdentifier, taskId, bundle);
    return new RestResponse<>(null);
  }

  @DELETE
  @Path("delete-task")
  @Timed
  @ExceptionMetered
  public void deleteTask(
      @NotNull @QueryParam("accountId") String accountId, @NotNull @QueryParam("taskId") String taskId) {
    dataCollectionTaskService.delete(accountId, taskId);
  }

  @GET
  @Path("task-status")
  @Timed
  @ExceptionMetered
  public RestResponse<CVNGPerpetualTaskDTO> getTaskStatus(@NotNull @QueryParam("taskId") String taskId) {
    return new RestResponse<>(dataCollectionTaskService.getCVNGPerpetualTaskDTO(taskId));
  }

  @POST
  @Path("get-data-collection-result")
  @Timed
  @ExceptionMetered
  public RestResponse<String> getDataCollectionResult(@NotNull @QueryParam("accountId") String accountId,
      @QueryParam("orgIdentifier") String orgIdentifier, @QueryParam("projectIdentifier") String projectIdentifier,
      @Body DataCollectionRequest dataCollectionRequest) {
    return new RestResponse<>(dataCollectionTaskService.getDataCollectionResult(
        accountId, orgIdentifier, projectIdentifier, dataCollectionRequest));
  }
}
