/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.exception.InvalidArgumentsException;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.execution.ExecutionStatusResponse;
import software.wings.security.annotations.ExternalFacingApiAuth;
import software.wings.service.intfc.WorkflowExecutionService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;

@Api("/external")
@Path("/external/{version}")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@ExternalFacingApiAuth
public class ExternalFacingApiResource {
  private WorkflowExecutionService workflowExecutionService;

  @Inject
  public ExternalFacingApiResource(WorkflowExecutionService workflowExecutionService) {
    this.workflowExecutionService = workflowExecutionService;
  }

  @GET
  @Path("/executions/{workflowExecutionId}/status")
  @Timed
  @ExceptionMetered
  public ExecutionStatusResponse getWorkflowExecutionStatus(
      @NotEmpty @PathParam("workflowExecutionId") String workflowExecutionId,
      @NotEmpty @QueryParam("accountId") String accountId, @NotEmpty @QueryParam("appId") String appId) {
    WorkflowExecution execution = workflowExecutionService.getExecutionDetailsWithoutGraph(appId, workflowExecutionId);
    if (execution == null) {
      throw new InvalidArgumentsException(Pair.of("Application Id", appId),
          Pair.of("Workflow Execution Id", workflowExecutionId),
          new IllegalArgumentException("Invalid App Id Or Workflow execution Id"));
    }
    return ExecutionStatusResponse.builder().status(execution.getStatus().name()).build();
  }
}
