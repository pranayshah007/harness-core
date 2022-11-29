/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.source.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.KUBERNETES_RESOURCE;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.activity.source.services.api.KubernetesActivitySourceService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api(KUBERNETES_RESOURCE)
@Path(KUBERNETES_RESOURCE)
@Produces("application/json")
@ExposeInternalException
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class KubernetesActivityResource {
  @Inject private KubernetesActivitySourceService kubernetesActivitySourceService;

  @GET
  @Timed
  @ExceptionMetered
  @NextGenManagerAuth
  @Path("/namespaces")
  @ApiOperation(value = "gets a list of kubernetes namespaces", nickname = "getNamespaces")
  public ResponseDTO<PageResponse<String>> getNamespaces(@QueryParam("accountId") @NotNull String accountId,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @QueryParam("connectorIdentifier") @NotNull String connectorIdentifier,
      @QueryParam("offset") @NotNull Integer offset, @QueryParam("pageSize") @NotNull Integer pageSize,
      @QueryParam("filter") String filter) {
    return ResponseDTO.newResponse(kubernetesActivitySourceService.getKubernetesNamespaces(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, offset, pageSize, filter));
  }

  @GET
  @Timed
  @ExceptionMetered
  @NextGenManagerAuth
  @Path("/workloads")
  @ApiOperation(value = "gets a list of kubernetes workloads", nickname = "getWorkloads")
  public ResponseDTO<PageResponse<String>> getWorkloads(@QueryParam("accountId") @NotNull String accountId,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @QueryParam("connectorIdentifier") @NotNull String connectorIdentifier,
      @QueryParam("namespace") @NotNull String namespace, @QueryParam("offset") @NotNull Integer offset,
      @QueryParam("pageSize") @NotNull Integer pageSize, @QueryParam("filter") String filter) {
    return ResponseDTO.newResponse(kubernetesActivitySourceService.getKubernetesWorkloads(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, namespace, offset, pageSize, filter));
  }

  @GET
  @Timed
  @ExceptionMetered
  @NextGenManagerAuth
  @Path("/validate")
  @ApiOperation(value = "validate permissions of a k8s connector for events", nickname = "validateK8sConnectivity")
  public ResponseDTO<Boolean> validateConnectivity(@QueryParam("accountId") @NotNull String accountId,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @QueryParam("connectorIdentifier") @NotNull String connectorIdentifier,
      @QueryParam("tracingId") @NotNull String tracingId) {
    return ResponseDTO.newResponse(kubernetesActivitySourceService.checkConnectivity(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, tracingId));
  }
}
