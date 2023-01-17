/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.dto.Log;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.analysis.ContinuousVerificationService;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.log.CustomLogSetupTestNodeData;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.analysis.LogVerificationService;
import software.wings.sm.StateType;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * Created by peeyushaggarwal on 1/9/17.
 */
@Api("logs")
@Path("/logs")
@Produces("application/json")
@Scope(ResourceType.APPLICATION)
public class LogResource {
  @Inject private LogService logService;
  @Inject private LogVerificationService logVerificationService;
  @Inject private ContinuousVerificationService cvManagerService;
  @Inject private KryoSerializer kryoSerializer;

  @DelegateAuth
  @POST
  @Path("activity/{activityId}/unit/{unitName}/batched")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> batchSave(
      @PathParam("activityId") String activityId, @PathParam("unitName") String unitName, byte[] logSerialized) {
    Log logObject = (Log) kryoSerializer.asObject(logSerialized);

    return new RestResponse<>(logService.batchedSaveCommandUnitLogs(activityId, unitName, logObject));
  }

  @POST
  @Path("/node-data")
  @Timed
  @AuthRule(permissionType = LOGGED_IN)
  @ExceptionMetered
  public RestResponse<VerificationNodeDataSetupResponse> getMetricsWithDataForNode(
      @QueryParam("accountId") final String accountId, @QueryParam("serverConfigId") String serverConfigId,
      @QueryParam("stateType") final StateType stateType, CustomLogSetupTestNodeData fetchConfig) {
    return new RestResponse<>(
        cvManagerService.getDataForNode(accountId, serverConfigId, fetchConfig, DelegateStateType.LOG_VERIFICATION));
  }
}
