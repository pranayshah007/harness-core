/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.FeatureName;
import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceUseFromStageV2;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Contains method useful for service plan creator
 */
@Slf4j
public class ServicePlanCreatorHelper {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private ServicePlanCreator servicePlanCreator;

  @Inject private NGFeatureFlagHelperService featureFlagService;

  /**
   * @param ctx             Plancreation context
   * @param parentSpecField spec field for the parent i.e spec field for the Deployment stage
   * @param stageNode       DeploymentStage node
   * @return Resolved service field ("serviceConfig" for v1, "service" for v2)
   */
  public YamlField getResolvedServiceField(
      PlanCreationContext ctx, YamlField parentSpecField, DeploymentStageNode stageNode) {
    YamlField serviceField = parentSpecField.getNode().getField(YamlTypes.SERVICE_CONFIG);
    if (serviceField != null) {
      return getResolvedServiceFieldForV1(serviceField);
    } else {
      YamlField serviceV2Field = parentSpecField.getNode().getField(YamlTypes.SERVICE_ENTITY);
      return getResolvedServiceFieldForV2(ctx, serviceV2Field, stageNode, parentSpecField);
    }
  }

  public Dependencies getDependenciesForService(YamlField serviceField, DeploymentStageNode stageNode,
      String environmentUuid, String infraSectionUuid, String serviceSpecNodeUuid) {
    final Map<String, YamlField> serviceYamlFieldMap = new HashMap<>();
    final String serviceNodeUuid = serviceField.getNode().getUuid();
    serviceYamlFieldMap.put(serviceNodeUuid, serviceField);

    Map<String, ByteString> serviceDependencyMap = new HashMap<>();
    // v1 serviceField
    if (stageNode.getDeploymentStageConfig().getService() == null
        && stageNode.getDeploymentStageConfig().getServiceConfig() != null) {
      PipelineInfrastructure infraConfig = stageNode.getDeploymentStageConfig().getInfrastructure();
      serviceDependencyMap.put(YamlTypes.INFRASTRUCTURE_STEP_PARAMETERS,
          ByteString.copyFrom(
              kryoSerializer.asDeflatedBytes(InfrastructurePmsPlanCreator.getInfraSectionStepParams(infraConfig, ""))));
      serviceDependencyMap.put(YamlTypes.ENVIRONMENT_NODE_ID,
          ByteString.copyFrom(kryoSerializer.asDeflatedBytes("environment-" + infraConfig.getUuid())));
    } // v2 serviceField
    else {
      serviceDependencyMap.put(
          YamlTypes.ENVIRONMENT_NODE_ID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(environmentUuid)));
      serviceDependencyMap.put(
          YamlTypes.NEXT_UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(infraSectionUuid)));
      serviceDependencyMap.put(PlanCreationConstants.SERVICE_SPEC_NODE_ID,
          ByteString.copyFrom(kryoSerializer.asDeflatedBytes(serviceSpecNodeUuid)));
    }

    Dependency serviceDependency = Dependency.newBuilder().putAllMetadata(serviceDependencyMap).build();
    return DependenciesUtils.toDependenciesProto(serviceYamlFieldMap)
        .toBuilder()
        .putDependencyMetadata(serviceNodeUuid, serviceDependency)
        .build();
  }

  private YamlField getResolvedServiceFieldForV1(YamlField serviceField) {
    ServiceConfig serviceConfig;
    try {
      serviceConfig = YamlUtils.read(serviceField.getNode().toString(), ServiceConfig.class);

      // Resolving service useFromStage.
      if (serviceConfig.getUseFromStage() == null) {
        return serviceField;
      } else {
        ServiceConfig actualServiceConfig = servicePlanCreator.getActualServiceConfig(serviceConfig, serviceField);
        String serviceConfigYaml = YamlPipelineUtils.getYamlString(actualServiceConfig);
        YamlField updatedServiceField = YamlUtils.injectUuidInYamlField(serviceConfigYaml);
        return new YamlField(YamlTypes.SERVICE_CONFIG,
            new YamlNode(YamlTypes.SERVICE_CONFIG, updatedServiceField.getNode().getCurrJsonNode(),
                serviceField.getNode().getParentNode()));
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid service yaml", e);
    }
  }

  @VisibleForTesting
  YamlField getResolvedServiceFieldForV2(
      PlanCreationContext ctx, YamlField serviceV2Field, DeploymentStageNode stageNode, YamlField parentSpecField) {
    final ServiceYamlV2 serviceYamlV2 = stageNode.getDeploymentStageConfig().getService().getUseFromStage() != null
        ? useServiceYamlFromStage(stageNode.getDeploymentStageConfig().getService().getUseFromStage(), serviceV2Field)
        : stageNode.getDeploymentStageConfig().getService();

    if (serviceYamlV2 == null) {
      throw new InvalidRequestException("ServiceRef cannot be absent in a stage - " + stageNode.getIdentifier());
    }

    final String accountIdentifier = ctx.getMetadata().getAccountIdentifier();

    if (serviceYamlV2.getServiceRef().isExpression()) {
      if (featureFlagService.isEnabled(accountIdentifier, FeatureName.SERVICE_V2_EXPRESSION)) {
        ServicePlanCreatorV2Config config = ServicePlanCreatorV2Config.builder()
                                                .identifier(serviceYamlV2.getServiceRef())
                                                .inputs(serviceYamlV2.getServiceInputs())
                                                .build();
        try {
          YamlField yamlField = YamlUtils.injectUuidInYamlField(YamlUtils.write(config));
          return new YamlField(YamlTypes.SERVICE_ENTITY,
              new YamlNode(YamlTypes.SERVICE_ENTITY, yamlField.getNode().getCurrJsonNode(), parentSpecField.getNode()));
        } catch (IOException e) {
          throw new InvalidRequestException("Invalid service yaml", e);
        }
      }
      throw new InvalidRequestException("ServiceRef cannot be expression or runtime input during execution");
    }

    final String orgIdentifier = ctx.getMetadata().getOrgIdentifier();
    final String projectIdentifier = ctx.getMetadata().getProjectIdentifier();
    final String serviceRef = serviceYamlV2.getServiceRef().getValue();
    final Optional<ServiceEntity> serviceEntity =
        serviceEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, false);

    if (serviceEntity.isEmpty()) {
      throw new InvalidRequestException(
          String.format("No service found with %s identifier in %s project in %s org and %s account",
              serviceYamlV2.getServiceRef(), projectIdentifier, orgIdentifier, accountIdentifier));
    }

    String serviceEntityYaml = serviceEntity.get().getYaml();
    if (isEmpty(serviceEntityYaml)) {
      log.error("Service entity is not valid as it doesn't contain yaml.");
      throw new InvalidRequestException("Service Entity is not valid for serviceRef - " + serviceRef);
    }

    try {
      if (serviceYamlV2.getServiceInputs() != null && isNotEmpty(serviceYamlV2.getServiceInputs().getValue())) {
        serviceEntityYaml =
            mergeServiceInputsIntoService(serviceEntityYaml, serviceYamlV2.getServiceInputs().getValue());
      }
      YamlField yamlField = YamlUtils.injectUuidInYamlField(serviceEntityYaml);
      if (yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY).getNode().getField(YamlTypes.SERVICE_DEFINITION)
          == null) {
        throw new InvalidRequestException(
            "Invalid Service being referred as serviceDefinition section is not there in DeploymentStage - "
            + stageNode.getIdentifier() + " for service - " + serviceRef);
      }
      return new YamlField(YamlTypes.SERVICE_ENTITY,
          new YamlNode(YamlTypes.SERVICE_ENTITY,
              yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY).getNode().getCurrJsonNode(),
              parentSpecField.getNode()));
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid service yaml in stage - " + stageNode.getIdentifier(), e);
    }
  }

  private String mergeServiceInputsIntoService(String originalServiceYaml, Map<String, Object> serviceInputs) {
    Map<String, Object> serviceInputsYaml = new HashMap<>();
    serviceInputsYaml.put(YamlTypes.SERVICE_ENTITY, serviceInputs);
    return MergeHelper.mergeInputSetFormatYamlToOriginYaml(
        originalServiceYaml, YamlPipelineUtils.writeYamlString(serviceInputsYaml));
  }

  /**
   * @param serviceField Service Field from a stage yaml
   * @return The node id for the spec field within service.serviceDefinition if present, or else generates a uuid
   */
  public String fetchOrGenerateSpecId(YamlField serviceField) {
    if (serviceField.getNode().getField(YamlTypes.SERVICE_DEFINITION) != null) {
      return serviceField.getNode()
          .getField(YamlTypes.SERVICE_DEFINITION)
          .getNode()
          .getField(YamlTypes.SERVICE_SPEC)
          .getNode()
          .getUuid();
    } else {
      return "serviceSpec-" + UUIDGenerator.generateUuid();
    }
  }

  private ServiceYamlV2 useServiceYamlFromStage(@NotNull ServiceUseFromStageV2 useFromStage, YamlField serviceField) {
    String stage = useFromStage.getStage();
    if (stage == null) {
      throw new InvalidRequestException("Stage identifier not present in useFromStage");
    }

    try {
      //  Add validation for not chaining of stages
      DeploymentStageNode stageElementConfig = YamlUtils.read(
          PlanCreatorUtils.getStageConfig(serviceField, stage).getNode().toString(), DeploymentStageNode.class);
      DeploymentStageConfig deploymentStage = stageElementConfig.getDeploymentStageConfig();
      if (deploymentStage != null) {
        return deploymentStage.getService();
      } else {
        throw new InvalidArgumentsException("Stage identifier given in useFromStage doesn't exist");
      }
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot parse stage: " + stage);
    }
  }
}
