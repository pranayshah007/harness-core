/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.deploymentstage;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.CDStageMetaDataDTO.CDStageMetaDataDTOBuilder;
import io.harness.ng.core.dto.CdDeployStageMetadataRequestDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.security.annotations.NextGenManagerAuth;

import io.fabric8.utils.Lists;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.jetbrains.annotations.Nullable;

@NextGenManagerAuth
@Api("/cdStage")
@Path("/cdStage")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@OwnedBy(HarnessTeam.CDC)
public class DeploymentStageConfigResource {
  @POST
  @Path("metadata")
  @ApiOperation(
      value = "Gets the Service and Environment refs from CD Stage Yaml", nickname = "getCdDeployStageMetadata")
  @Operation(operationId = "getCdDeployStageMetadata",
      summary = "Gets the Service and Environment refs from CD Stage Yaml",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the Service Reference") },
      hidden = true)
  public ResponseDTO<CDStageMetaDataDTO>
  getCdDeployStageMetadata(@RequestBody(required = true,
      description = "Pipeline yaml and stage identifier") @Valid CdDeployStageMetadataRequestDTO requestDTO)
      throws IOException {
    CDStageMetaDataDTOBuilder CDStageMetaDataDTOBuilder = CDStageMetaDataDTO.builder();
    List<YamlNode> stageYamlNodes = getStageYamlNodes(requestDTO.getPipelineYaml());
    DeploymentStageNode actualStage = getDeploymentStageNode(requestDTO.getStageIdentifier(), stageYamlNodes);
    if (actualStage == null) {
      throw new InvalidRequestException(
          format("Could not find stage %s in pipeline yaml", requestDTO.getStageIdentifier()));
    }

    List<String> serviceRefs = null;
    final Optional<String> referredStageForServiceOptional = getReferredStageForService(actualStage);
    if (referredStageForServiceOptional.isPresent()) {
      final String referredStageIdentifier = referredStageForServiceOptional.get();
      DeploymentStageNode referredStage = getDeploymentStageNode(referredStageIdentifier, stageYamlNodes);
      if (referredStage == null) {
        throw new InvalidRequestException(
            format("Could not find referred stage %s in pipeline yaml", referredStageIdentifier));
      }
      serviceRefs = getServiceRefs(referredStage.getDeploymentStageConfig());
    } else {
      serviceRefs = getServiceRefs(actualStage.getDeploymentStageConfig());
    }

    final List<String> environmentRefs = getEnvironmentRefs(actualStage.getDeploymentStageConfig());
    if (serviceRefs.size() == 1 && environmentRefs.size() == 1) {
      CDStageMetaDataDTOBuilder.environmentRef(environmentRefs.get(0));
      CDStageMetaDataDTOBuilder.serviceRef(serviceRefs.get(0));
    }

    for (String serviceRef : serviceRefs) {
      for (String envRef : environmentRefs) {
        CDStageMetaDataDTOBuilder.serviceEnvRef(
            CDStageMetaDataDTO.ServiceEnvRef.builder().serviceRef(serviceRef).environmentRef(envRef).build());
      }
    }

    return ResponseDTO.newResponse(CDStageMetaDataDTOBuilder.build());
  }

  private Optional<String> getReferredStageForService(DeploymentStageNode deploymentStageNode) {
    final DeploymentStageConfig deploymentStageConfig = deploymentStageNode.getDeploymentStageConfig();
    if (deploymentStageConfig.getServiceConfig() != null
        && deploymentStageConfig.getServiceConfig().getUseFromStage() != null) {
      return Optional.of(deploymentStageConfig.getServiceConfig().getUseFromStage().getStage());
    } else if (deploymentStageConfig.getService() != null
        && deploymentStageConfig.getService().getUseFromStage() != null) {
      return Optional.of(deploymentStageConfig.getService().getUseFromStage().getStage());
    }
    return Optional.empty();
  }

  @Nullable
  private DeploymentStageNode getDeploymentStageNode(String stageIdentifier, List<YamlNode> stageYamlNodes)
      throws IOException {
    DeploymentStageNode actualStage = null;
    for (YamlNode stageYamlNode : stageYamlNodes) {
      if (stageYamlNode.getField(YamlTypes.STAGE).getNode().getIdentifier().equals(stageIdentifier)) {
        final String stage = YamlUtils.writeYamlString(stageYamlNode.getField(YamlTypes.STAGE));
        actualStage = YamlUtils.read(stage, DeploymentStageNode.class);
      }
    }
    return actualStage;
  }

  private List<YamlNode> getStageYamlNodes(String pipelineYaml) throws IOException {
    final YamlField pipelineYamlField = YamlUtils.readTree(pipelineYaml);
    final List<YamlNode> stageGroupNodes = pipelineYamlField.getNode()
                                               .getField(YamlTypes.PIPELINE)
                                               .getNode()
                                               .getField(YamlTypes.STAGES)
                                               .getNode()
                                               .asArray();
    return stageGroupNodes.stream()
        .map(this::getStageYamlNodesFromStageGroup)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  private List<YamlNode> getStageYamlNodesFromStageGroup(YamlNode stageGroupNode) {
    if (stageGroupNode.getField(YamlTypes.PARALLEL_STAGE) != null) {
      return stageGroupNode.getField(YamlTypes.PARALLEL_STAGE).getNode().asArray();
    } else if (stageGroupNode.getField(YamlTypes.STAGE) != null) {
      return Arrays.asList(stageGroupNode);
    }
    return Collections.emptyList();
  }

  private List<String> getEnvironmentRefs(DeploymentStageConfig deploymentStageConfig) {
    final PipelineInfrastructure infrastructure = deploymentStageConfig.getInfrastructure();
    if (infrastructure != null) {
      if (infrastructure.getEnvironmentRef() != null) {
        return Lists.newArrayList(getReferenceValue(infrastructure.getEnvironmentRef()));
      } else {
        if (infrastructure.getEnvironment() != null) {
          return Lists.newArrayList(infrastructure.getEnvironment().getIdentifier());
        }
      }
    } else {
      if (deploymentStageConfig.getEnvironment() != null
          && deploymentStageConfig.getEnvironment().getEnvironmentRef() != null) {
        return Lists.newArrayList(getReferenceValue(deploymentStageConfig.getEnvironment().getEnvironmentRef()));
      }

      if (deploymentStageConfig.getEnvironments() != null
          && ParameterField.isNotNull(deploymentStageConfig.getEnvironments().getValues())
          && !deploymentStageConfig.getEnvironments().getValues().isExpression()) {
        return deploymentStageConfig.getEnvironments()
            .getValues()
            .getValue()
            .stream()
            .map(EnvironmentYamlV2::getEnvironmentRef)
            .map(this::getReferenceValue)
            .collect(Collectors.toList());
      }
    }
    return new ArrayList<>();
  }

  private List<String> getServiceRefs(DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getServiceConfig() != null) {
      if (deploymentStageConfig.getServiceConfig().getServiceRef() != null) {
        return Lists.newArrayList(getReferenceValue(deploymentStageConfig.getServiceConfig().getServiceRef()));
      } else {
        if (deploymentStageConfig.getServiceConfig().getService() != null) {
          return Lists.newArrayList(deploymentStageConfig.getServiceConfig().getService().getIdentifier());
        }
      }
    } else {
      if (deploymentStageConfig.getService() != null && deploymentStageConfig.getService().getServiceRef() != null) {
        return Lists.newArrayList(getReferenceValue(deploymentStageConfig.getService().getServiceRef()));
      }
      if (deploymentStageConfig.getServices() != null) {
        if (ParameterField.isNotNull(deploymentStageConfig.getServices().getValues())
            && !deploymentStageConfig.getServices().getValues().isExpression()) {
          return deploymentStageConfig.getServices()
              .getValues()
              .getValue()
              .stream()
              .map(ServiceYamlV2::getServiceRef)
              .map(this::getReferenceValue)
              .collect(Collectors.toList());
        }
      }
    }
    return new ArrayList<>();
  }

  private String getReferenceValue(ParameterField<String> parameterField) {
    return Objects.nonNull(parameterField.getValue()) ? parameterField.getValue() : parameterField.getExpressionValue();
  }
}
