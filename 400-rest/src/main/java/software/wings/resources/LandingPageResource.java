/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.resources;

import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.PermissionType.WORKFLOW;

import io.harness.rest.RestResponse;

import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.service.intfc.WorkflowExecutionService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import lombok.extern.slf4j.Slf4j;

@Api("landingPage")
@Path("landingPage")
@Produces("application/json")
@Slf4j
public class LandingPageResource {
  @Inject private WorkflowExecutionService workflowExecutionService;

  @GET
  @Path("/deploymentCount")
  @Timed
  @ExceptionMetered
  @ApiKeyAuthorized(permissionType = WORKFLOW, action = READ, skipAuth = true)
  public RestResponse<Integer> getDeploymentCount() {
    return new RestResponse<>(workflowExecutionService.getDeploymentCount());
  }
}
