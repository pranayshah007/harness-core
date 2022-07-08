/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.k8s.cluster.resources.gcp;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.GCP_CONNECTOR_IDENTIFIER;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.k8s.resources.gcp.GcpResponseDTO;
import io.harness.cdng.k8s.resources.gcp.service.GcpResourceService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("gcp")
@Path("/gcp")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
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
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class GcpClusterResource {
  private final GcpResourceService gcpResourceService;
  private final InfrastructureEntityService infrastructureEntityService;

  @GET
  @Path("clusters")
  @ApiOperation(value = "Gets gcp cluster names", nickname = "getClusterNamesForGcp")
  @Operation(operationId = "getClusterNamesForGcp", summary = "Gets gcp cluster names",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns list of gcp cluster names")
      },
      hidden = true)
  public ResponseDTO<GcpResponseDTO>
  getClusterNames(@Parameter(description = GCP_CONNECTOR_IDENTIFIER) @NotNull @QueryParam(
                      "connectorRef") String gcpConnectorIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcpConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    GcpResponseDTO response =
        gcpResourceService.getClusterNames(connectorRef, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(response);
  }

  @GET
  @Path("clustersV2")
  @ApiOperation(
      value = "Gets gcp cluster names for a gcp infrastructure definition", nickname = "getClusterNamesForGcpInfra")
  @Operation(operationId = "getClusterNamesForGcpInfra", summary = "Gets gcp cluster names for infra",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns list of gcp cluster names for a gcp infra definition")
      },
      hidden = true)
  public ResponseDTO<GcpResponseDTO>
  getClusterNamesV2(
      @Parameter(description = GCP_CONNECTOR_IDENTIFIER) @QueryParam("connectorRef") String gcpConnectorIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    if (isEmpty(gcpConnectorIdentifier)) {
      checkArgument(isNotEmpty(envId),
          format("%s must be provided when connectorRef is empty", NGCommonEntityConstants.ENVIRONMENT_KEY));
      checkArgument(isNotEmpty(infraDefinitionId),
          format("%s must be provided when connectorRef is empty", NGCommonEntityConstants.INFRA_DEFINITION_KEY));

      Optional<InfrastructureEntity> entityOptional =
          infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);

      checkArgument(entityOptional.isPresent(),
          format("No infrastructure definition with id %s found in environment %s", infraDefinitionId, envId));

      InfrastructureDefinitionConfig infrastructureConfig =
          InfrastructureEntityConfigMapper.toInfrastructureConfig(entityOptional.get())
              .getInfrastructureDefinitionConfig();

      gcpConnectorIdentifier = infrastructureConfig.getSpec().getConnectorReference().getValue();
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcpConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    GcpResponseDTO response =
        gcpResourceService.getClusterNames(connectorRef, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(response);
  }

  public static void checkArgument(boolean expression, Object errorMessage) {
    if (!expression) {
      throw new InvalidRequestException(String.valueOf(errorMessage));
    }
  }
}
