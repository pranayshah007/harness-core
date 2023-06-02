/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.gcp.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleCloudStorageArtifactConfig;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.k8s.resources.gcp.dtos.GcpProjectDetails;
import io.harness.cdng.k8s.resources.gcp.dtos.GcpProjectResponseDTO;
import io.harness.cdng.k8s.resources.gcp.service.GcpResourceService;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
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
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Api("gcp")
@Path("/gcp")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GcpResource {
  private final ArtifactResourceUtils artifactResourceUtils;
  private final GcpResourceService gcpResourceService;
  private final InfrastructureEntityService infrastructureEntityService;

  @POST
  @Path("project")
  @ApiOperation(value = "Get list of projects from gcp", nickname = "getProjects")
  public ResponseDTO<GcpProjectResponseDTO> getProjects(@QueryParam("connectorRef") String gcpConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @QueryParam(NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @QueryParam(NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    String resolvedGcpConnectorRef = gcpConnectorRef;
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
          (GoogleCloudStorageArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(gcpConnectorRef)) {
        resolvedGcpConnectorRef = (String) googleCloudStorageArtifactConfig.getConnectorRef().fetchFinalValue();
      }
      // Getting the resolved connectorRef in case of expressions
      resolvedGcpConnectorRef = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, resolvedGcpConnectorRef, fqnPath, gitEntityBasicInfo, serviceRef);
    } else if (isNotEmpty(envId) && isNotEmpty(infraDefinitionId)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      if (StringUtils.isBlank(gcpConnectorRef)) {
        resolvedGcpConnectorRef = infrastructureDefinitionConfig.getSpec().getConnectorReference().getValue();
      }
    }
    if (StringUtils.isBlank(resolvedGcpConnectorRef)) {
      throw new InvalidRequestException(
          String.valueOf(format("%s must be provided", NGCommonEntityConstants.CONNECTOR_IDENTIFIER_REF)));
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedGcpConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<GcpProjectDetails> projects =
        gcpResourceService.getProjectNames(connectorRef, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(GcpProjectResponseDTO.builder().projects(projects).build());
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
