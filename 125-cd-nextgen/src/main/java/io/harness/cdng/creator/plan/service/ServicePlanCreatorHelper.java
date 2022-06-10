/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Contains method useful for service plan creator
 */
@UtilityClass
@Slf4j
public class ServicePlanCreatorHelper {
  public YamlField getResolvedServiceField(YamlField parentSpecField, DeploymentStageNode stageNode,
      ServicePlanCreator servicePlanCreator, ServiceEntityService serviceEntityService, PlanCreationContext ctx) {
    YamlField serviceField = parentSpecField.getNode().getField(YamlTypes.SERVICE_CONFIG);
    if (serviceField != null) {
      return getResolvedServiceFieldForV1(serviceField, servicePlanCreator);
    } else {
      return getResolvedServiceFieldForV2(stageNode, serviceEntityService, parentSpecField, ctx);
    }
  }

  public Dependencies getDependenciesForService(YamlField serviceField, DeploymentStageNode stageNode,
      String environmentUuid, String infraSectionUuid, KryoSerializer kryoSerializer) {
    Map<String, YamlField> serviceYamlFieldMap = new HashMap<>();
    String serviceNodeUuid = serviceField.getNode().getUuid();
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
    }

    Dependency serviceDependency = Dependency.newBuilder().putAllMetadata(serviceDependencyMap).build();
    return DependenciesUtils.toDependenciesProto(serviceYamlFieldMap)
        .toBuilder()
        .putDependencyMetadata(serviceNodeUuid, serviceDependency)
        .build();
  }

  private YamlField getResolvedServiceFieldForV1(YamlField serviceField, ServicePlanCreator servicePlanCreator) {
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
  YamlField getResolvedServiceFieldForV2(DeploymentStageNode stageNode, ServiceEntityService serviceEntityService,
      YamlField parentSpecField, PlanCreationContext ctx) {
    ServiceYamlV2 serviceYamlV2 = stageNode.getDeploymentStageConfig().getService();
    if (serviceYamlV2 == null) {
      throw new InvalidRequestException("ServiceRef cannot be absent in a stage - " + stageNode.getIdentifier());
    }
    if (serviceYamlV2.getServiceRef().isExpression()) {
      throw new InvalidRequestException("ServiceRef cannot be expression or runtime input during execution");
    }

    String accountIdentifier = ctx.getMetadata().getAccountIdentifier();
    String orgIdentifier = ctx.getMetadata().getOrgIdentifier();
    String projectIdentifier = ctx.getMetadata().getProjectIdentifier();
    String serviceRef = serviceYamlV2.getServiceRef().getValue();
    Optional<ServiceEntity> serviceEntity =
        serviceEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, false);

    if (!serviceEntity.isPresent()) {
      throw new InvalidRequestException(
          String.format("No service found with %s identifier in %s project in %s org and %s account",
              serviceYamlV2.getServiceRef(), projectIdentifier, orgIdentifier, accountIdentifier));
    }

    String serviceYaml = serviceEntity.get().getYaml();
    if (EmptyPredicate.isEmpty(serviceYaml)) {
      log.error("Service entity is not valid as it doesn't contain yaml.");
      throw new InvalidRequestException("Service Entity is not valid for serviceRef - " + serviceRef);
    }

    try {
      if (EmptyPredicate.isNotEmpty(serviceYamlV2.getServiceInputs())) {
        serviceYaml = mergeServiceInputsIntoService(serviceYaml, serviceYamlV2.getServiceInputs());
      }
      YamlField yamlField = YamlUtils.injectUuidInYamlField(serviceYaml);
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

  public String fetchServiceSpecUuid(YamlField serviceField) {
    return serviceField.getNode()
        .getField(YamlTypes.SERVICE_DEFINITION)
        .getNode()
        .getField(YamlTypes.SERVICE_SPEC)
        .getNode()
        .getUuid();
  }
}
