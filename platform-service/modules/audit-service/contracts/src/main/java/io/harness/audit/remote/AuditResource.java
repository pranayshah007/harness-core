/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.AuditFilterPropertiesDTO;
import io.harness.audit.beans.custom.ActiveProjectMetricsDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@OwnedBy(PL)
@Api("audits")
@Path("audits")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Audit", description = "This contains APIs related to Audit as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
public interface AuditResource {
  @POST
  @Hidden
  @ApiOperation(hidden = true, value = "Create an Audit", nickname = "postAudit")
  @Operation(operationId = "postAuditEvent", summary = "Creates an Audit Event",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns True if an Audit Event is successfully created. Else, it returns false.")
      },
      hidden = true)
  @InternalApi
  ResponseDTO<Boolean>
  create(@RequestBody(description = "This has the information to create an Audit event.",
      required = true) @NotNull @Valid AuditEventDTO auditEventDTO);

  @POST
  @Path("/list")
  @ApiOperation(value = "Get Audit Event list", nickname = "getAuditEventList")
  @Operation(operationId = "getAuditEventList", summary = "List Audit Events",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "This has a paginated list of Audit Events.")
      })
  ResponseDTO<PageResponse<AuditEventDTO>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
       @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @BeanParam PageRequest pageRequest,
      @RequestBody(description = "This has the filter attributes for listing Audit Events")
      AuditFilterPropertiesDTO auditFilterPropertiesDTO);

  @POST
  @Path("/publish-metrics")
  @Hidden
  @ApiOperation(
      hidden = true, value = "publish Metrics for Active Project", nickname = "publishMetricsForActiveProject")
  @Operation(operationId = "publishMetricsForActiveProject", summary = "publish Metrics for Active Project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "This has the information to publish Metrics for Active Project")
      },
      hidden = true)
  @InternalApi
  ResponseDTO<Void>
  publishMetrics(@RequestBody(description = "This has the information to publish Metrics for Active Project",
      required = true) @NotNull @Valid ActiveProjectMetricsDTO activeProjectMetricsDTO);
}
