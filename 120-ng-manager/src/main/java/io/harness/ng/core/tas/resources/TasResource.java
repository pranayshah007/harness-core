/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.tas.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.yaml.TanzuApplicationServiceInfrastructure;
import io.harness.cdng.tas.service.TasResourceService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@NextGenManagerAuth
@Api("/tas")
@Path("/tas")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "tas", description = "This contains APIs related to tas")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
public class TasResource {
  @Inject TasResourceService tasResourceService;
  @Inject InfrastructureEntityService infrastructureEntityService;

  @GET
  @Path("/organizations")
  @ApiOperation(value = "Gets tas organizations ", nickname = "getTasOrganizations")
  @Operation(operationId = "getTasOrganizations", summary = "Return the Tas organizations",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the Tas organizations")
      })
  public ResponseDTO<List<String>>
  getTasOrganizations(@Parameter(description = "Identifier for tas connector") @QueryParam(
                          "connectorRef") String tasConnectorIdentifier,
      @AccountIdentifier @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @OrgIdentifier @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @ProjectIdentifier @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    if (isEmpty(tasConnectorIdentifier)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      tasConnectorIdentifier = infrastructureDefinitionConfig.getSpec().getConnectorReference().getValue();
    }
    log.info("retrieving organization for tas");
    return ResponseDTO.newResponse(
        tasResourceService.listOrganizations(tasConnectorIdentifier, accountId, orgIdentifier, projectIdentifier));
  }

  @GET
  @Path("/space")
  @ApiOperation(value = "Gets tas spaces ", nickname = "getTasSpaces")
  @Operation(operationId = "getTasSpaces", summary = "Return the Tas spaces",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the Tas spaces")
      })
  public ResponseDTO<List<String>>
  getTasSpaces(@Parameter(description = "Identifier for tas connector") @NotNull @QueryParam(
                   "connectorRef") String tasConnectorIdentifier,
      @Parameter(description = "organization for tas") @NotNull @QueryParam("organization") String organization,
      @AccountIdentifier @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @OrgIdentifier @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @ProjectIdentifier @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    log.info("retrieving spaces for tas");
    return ResponseDTO.newResponse(tasResourceService.listSpaces(
        tasConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, organization));
  }

  @GET
  @Path("v2/space")
  @ApiOperation(value = "Gets tas spaces V2 ", nickname = "getTasSpacesV2")
  @Operation(operationId = "getTasSpacesV2", summary = "Return the Tas spaces",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the Tas spaces")
      })
  public ResponseDTO<List<String>>
  getTasSpacesV2(@AccountIdentifier @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @OrgIdentifier @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @ProjectIdentifier @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("organization") String organization,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    InfrastructureDefinitionConfig infrastructureDefinitionConfig =
        getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
    TanzuApplicationServiceInfrastructure infrastructure =
        (TanzuApplicationServiceInfrastructure) infrastructureDefinitionConfig.getSpec();
    String tasConnectorIdentifier = infrastructure.getConnectorRef().getValue();
    if (tasConnectorIdentifier == null) {
      throw new InvalidRequestException("Connector ref is null in Infrastructure for Infra Id");
    }
    if (isEmpty(organization)) {
      organization = infrastructure.getOrganization().getValue();
    }
    log.info("retrieving spaces for tas");
    return ResponseDTO.newResponse(tasResourceService.listSpaces(
        tasConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, organization));
  }
  private InfrastructureDefinitionConfig getInfrastructureDefinitionConfig(
      String accountId, String orgIdentifier, String projectIdentifier, String envId, String infraDefinitionId) {
    InfrastructureEntity infrastructureEntity =
        infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId)
            .orElseThrow(() -> {
              throw new NotFoundException(String.format(
                  "Infrastructure with identifier [%s] in project [%s], org [%s], environment [%s] not found",
                  infraDefinitionId, projectIdentifier, orgIdentifier, envId));
            });

    return InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureEntity)
        .getInfrastructureDefinitionConfig();
  }
}
