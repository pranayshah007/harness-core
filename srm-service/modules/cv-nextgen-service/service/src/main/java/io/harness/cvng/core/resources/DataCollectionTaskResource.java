/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.DELEGATE_DATA_COLLECTION_TASK;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api(DELEGATE_DATA_COLLECTION_TASK)
@Path(DELEGATE_DATA_COLLECTION_TASK)
@Produces("application/json")
@ExposeInternalException
public class DataCollectionTaskResource {
  @Inject private DataCollectionTaskService dataCollectionTaskService;

  @GET
  @Path("next-task")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "gets next task for data collection", nickname = "getNextDataCollectionTask")
  public RestResponse<Optional<DataCollectionTaskDTO>> getNextTask(@QueryParam("accountId") @NotNull String accountId,
      @QueryParam("dataCollectionWorkerId") String dataCollectionWorkerId) {
    return new RestResponse<>(dataCollectionTaskService.getNextTaskDTO(accountId, dataCollectionWorkerId));
  }

  @GET
  @Path("next-tasks")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "gets next tasks for data collection", nickname = "getNextDataCollectionTasks")
  public RestResponse<List<DataCollectionTaskDTO>> getNextTasks(@NotNull @QueryParam("accountId") String accountId,
      @NotNull @QueryParam("dataCollectionWorkerId") String dataCollectionWorkerId) {
    return new RestResponse<>(dataCollectionTaskService.getNextTaskDTOs(accountId, dataCollectionWorkerId));
  }

  @POST
  @Path("update-status")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "updates status for data collection task", nickname = "updateDataCollectionTask")
  public RestResponse<Void> updateTaskStatus(
      @QueryParam("accountId") @NotNull String accountId, DataCollectionTaskResult dataCollectionTaskResult) {
    dataCollectionTaskService.updateTaskStatus(dataCollectionTaskResult);
    return new RestResponse<>(null);
  }
}
