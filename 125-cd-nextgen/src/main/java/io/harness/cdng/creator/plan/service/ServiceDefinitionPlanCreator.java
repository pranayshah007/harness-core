/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.azure.config.yaml.ApplicationSettingsConfiguration;
import io.harness.cdng.azure.config.yaml.ConnectionStringsConfiguration;
import io.harness.cdng.azure.config.yaml.StartupCommandConfiguration;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.service.ServiceSpec;
import io.harness.cdng.service.beans.AzureWebAppServiceSpec;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.steps.ServiceDefinitionStep;
import io.harness.cdng.service.steps.ServiceDefinitionStepParameters;
import io.harness.cdng.service.steps.ServiceSpecStep;
import io.harness.cdng.service.steps.ServiceSpecStepParameters;
import io.harness.cdng.utilities.ConfigFileUtility;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse.PlanCreationResponseBuilder;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ServiceDefinitionPlanCreator extends ChildrenPlanCreator<YamlField> {
  @Inject KryoSerializer kryoSerializer;
  @Inject ServiceOverrideService serviceOverrideService;
  @Inject EnvironmentService environmentService;

  /*
  TODO: currently we are using many yaml updates. For ex - if we do not have service definition and we need to call plan
  creators for either of artifacts or manifests we are using yamlUpdates which contains dummy artifact and manifests
  yaml node. The best way is to pre calculate the overridesets and override stage and create the resolved
  serviceDefinition and do the yaml updates in service plan creator.
   */

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField serviceDefField) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    try {
      YamlNode serviceConfigNode = YamlUtils.findParentNode(serviceDefField.getNode(), YamlTypes.SERVICE_CONFIG);
      // Adding children for v1 service
      if (serviceConfigNode != null) {
        addChildrenForServiceV1(planCreationResponseMap, serviceConfigNode);
      } else {
        YamlNode serviceV2Node = YamlUtils.findParentNode(serviceDefField.getNode(), YamlTypes.SERVICE_ENTITY);
        addChildrenForServiceV2(planCreationResponseMap, serviceV2Node, ctx);
      }

      return planCreationResponseMap;
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid ServiceDefinition node", e);
    }
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, YamlField serviceDefField, List<String> childrenNodeIds) {
    String envNodeUuid = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.ENVIRONMENT_NODE_ID).toByteArray());
    String type = serviceDefField.getNode().getField(YAMLFieldNameConstants.TYPE).getNode().asText();
    ServiceDefinitionStepParameters stepParameters =
        ServiceDefinitionStepParameters.builder().type(type).childNodeId(envNodeUuid).build();
    return PlanNode.builder()
        .uuid(serviceDefField.getNode().getUuid())
        .stepType(ServiceDefinitionStep.STEP_TYPE)
        .name(PlanCreatorConstants.SERVICE_DEFINITION_NODE_NAME)
        .identifier(YamlTypes.SERVICE_DEFINITION)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipExpressionChain(false)
        .skipGraphType(SkipType.SKIP_TREE)
        .build();
  }

  private void addChildrenForServiceV1(
      Map<String, PlanCreationResponse> planCreationResponseMap, YamlNode serviceConfigNode) throws IOException {
    ServiceConfig serviceConfig = YamlUtils.read(serviceConfigNode.toString(), ServiceConfig.class);

    List<String> serviceSpecChildrenIds = new ArrayList<>();
    boolean createPlanForArtifacts =
        ServiceDefinitionPlanCreatorHelper.validateCreatePlanNodeForArtifacts(serviceConfig);
    if (createPlanForArtifacts) {
      String artifactNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForArtifacts(
          serviceConfigNode, planCreationResponseMap, serviceConfig, kryoSerializer);
      serviceSpecChildrenIds.add(artifactNodeId);
    }

    if (ServiceDefinitionPlanCreatorHelper.shouldCreatePlanNodeForManifests(serviceConfig)) {
      String manifestPlanNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForManifests(
          serviceConfigNode, planCreationResponseMap, serviceConfig, kryoSerializer);
      serviceSpecChildrenIds.add(manifestPlanNodeId);
    }

    if (shouldCreatePlanNodeForConfigFiles(serviceConfig)) {
      String configFilesPlanNodeId =
          addDependenciesForConfigFiles(serviceConfigNode, planCreationResponseMap, serviceConfig);
      serviceSpecChildrenIds.add(configFilesPlanNodeId);
    }

    if (serviceConfig.getServiceDefinition().getServiceSpec() instanceof AzureWebAppServiceSpec) {
      AzureWebAppServiceSpec azureWebAppServiceSpec =
          (AzureWebAppServiceSpec) serviceConfig.getServiceDefinition().getServiceSpec();

      StartupCommandConfiguration startupCommand = azureWebAppServiceSpec.getStartupCommand();
      if (startupCommand != null) {
        String startupCommandPlanNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForStartupCommand(
            serviceConfigNode, planCreationResponseMap, serviceConfig, kryoSerializer);
        serviceSpecChildrenIds.add(startupCommandPlanNodeId);
      }

      ApplicationSettingsConfiguration applicationSettings = azureWebAppServiceSpec.getApplicationSettings();
      if (applicationSettings != null) {
        String applicationSettingsPlanNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForApplicationSettings(
            serviceConfigNode, planCreationResponseMap, serviceConfig, kryoSerializer);
        serviceSpecChildrenIds.add(applicationSettingsPlanNodeId);
      }

      ConnectionStringsConfiguration connectionStrings = azureWebAppServiceSpec.getConnectionStrings();
      if (connectionStrings != null) {
        String connectionStringsPlanNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForConnectionStrings(
            serviceConfigNode, planCreationResponseMap, serviceConfig, kryoSerializer);
        serviceSpecChildrenIds.add(connectionStringsPlanNodeId);
      }
    }

    // Add serviceSpec node
    addServiceSpecNode(serviceConfig, planCreationResponseMap, serviceSpecChildrenIds);
  }

  private void addChildrenForServiceV2(Map<String, PlanCreationResponse> planCreationResponseMap,
      YamlNode serviceV2Node, PlanCreationContext ctx) throws IOException {
    NGServiceV2InfoConfig config = YamlUtils.read(serviceV2Node.toString(), NGServiceV2InfoConfig.class);

    List<String> serviceSpecChildrenIds = new ArrayList<>();
    boolean createPlanForArtifacts = ServiceDefinitionPlanCreatorHelper.validateCreatePlanNodeForArtifactsV2(config);
    if (createPlanForArtifacts) {
      String artifactNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForArtifactsV2(
          serviceV2Node, planCreationResponseMap, config, kryoSerializer);
      serviceSpecChildrenIds.add(artifactNodeId);
    }

    NGEnvironmentConfig ngEnvironmentConfig = fetchEnvironmentConfig(ctx, kryoSerializer);
    NGServiceOverrideConfig serviceOverrideConfig =
        fetchServiceOverrideConfig(ctx, config, ngEnvironmentConfig.getNgEnvironmentInfoConfig().getIdentifier());

    if (isSvcOverridesManifestPresent(serviceOverrideConfig)
        || isEnvGlobalManifestOverridesPresent(ngEnvironmentConfig)) {
      String manifestPlanNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForSvcAndSvcOverrideManifestsV2(
          serviceV2Node, planCreationResponseMap, config, serviceOverrideConfig, ngEnvironmentConfig, kryoSerializer);
      // handling no manifest present case
      if (isNotBlank(manifestPlanNodeId)) {
        serviceSpecChildrenIds.add(manifestPlanNodeId);
      }
    } else if (ServiceDefinitionPlanCreatorHelper.shouldCreatePlanNodeForManifestsV2(config)) {
      String manifestPlanNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForServiceManifestsV2(
          serviceV2Node, planCreationResponseMap, config, kryoSerializer);
      serviceSpecChildrenIds.add(manifestPlanNodeId);
    }

    if (isSvcOverridesConfigFilesPresent(serviceOverrideConfig)
        || isEnvGlobalConfigFileOverridesPresent(ngEnvironmentConfig)) {
      String configFilesPlanNodeId =
          ServiceDefinitionPlanCreatorHelper.addDependenciesForSvcAndSvcOverrideConfigFilesV2(serviceV2Node,
              planCreationResponseMap, config, serviceOverrideConfig, ngEnvironmentConfig, kryoSerializer);
      if (isNotBlank(configFilesPlanNodeId)) {
        serviceSpecChildrenIds.add(configFilesPlanNodeId);
      }

    } else if (ServiceDefinitionPlanCreatorHelper.shouldCreatePlanNodeForConfigFilesV2(config)) {
      String configFilesPlanNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForConfigFilesV2(
          serviceV2Node, planCreationResponseMap, config, kryoSerializer);
      serviceSpecChildrenIds.add(configFilesPlanNodeId);
    }

    if (config.getServiceDefinition().getServiceSpec() instanceof AzureWebAppServiceSpec) {
      AzureWebAppServiceSpec azureWebAppServiceSpec =
          (AzureWebAppServiceSpec) config.getServiceDefinition().getServiceSpec();

      StartupCommandConfiguration startupCommand = azureWebAppServiceSpec.getStartupCommand();
      if (startupCommand != null) {
        String configFilesPlanNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForStartupCommandV2(
            serviceV2Node, planCreationResponseMap, config, kryoSerializer);
        serviceSpecChildrenIds.add(configFilesPlanNodeId);
      }

      ApplicationSettingsConfiguration applicationSettings = azureWebAppServiceSpec.getApplicationSettings();
      if (applicationSettings != null) {
        String applicationSettingsPlanNodeId =
            ServiceDefinitionPlanCreatorHelper.addDependenciesForApplicationSettingsV2(
                serviceV2Node, planCreationResponseMap, config, kryoSerializer);
        serviceSpecChildrenIds.add(applicationSettingsPlanNodeId);
      }

      ConnectionStringsConfiguration connectionStrings = azureWebAppServiceSpec.getConnectionStrings();
      if (connectionStrings != null) {
        String connectionStringsPlanNodeId = ServiceDefinitionPlanCreatorHelper.addDependenciesForConnectionStringsV2(
            serviceV2Node, planCreationResponseMap, config, kryoSerializer);
        serviceSpecChildrenIds.add(connectionStringsPlanNodeId);
      }
    }

    // Add serviceSpec node
    addServiceSpecNodeV2(config, planCreationResponseMap, serviceSpecChildrenIds);
  }

  private boolean isServiceOverridePresent(NGServiceOverrideConfig serviceOverrideConfig) {
    return serviceOverrideConfig != null && serviceOverrideConfig.getServiceOverrideInfoConfig() != null;
  }

  private boolean isEnvGlobalOverridesPresent(NGEnvironmentConfig ngEnvironmentConfig) {
    return ngEnvironmentConfig != null && ngEnvironmentConfig.getNgEnvironmentInfoConfig() != null
        && ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride() != null;
  }

  private boolean isEnvGlobalConfigFileOverridesPresent(NGEnvironmentConfig ngEnvironmentConfig) {
    if (!isEnvGlobalOverridesPresent(ngEnvironmentConfig)) {
      return false;
    }
    return isNotEmpty(
        ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getConfigFiles());
  }

  private boolean isSvcOverridesConfigFilesPresent(NGServiceOverrideConfig serviceOverrideConfig) {
    if (!isServiceOverridePresent(serviceOverrideConfig)) {
      return false;
    }
    return isNotEmpty(serviceOverrideConfig.getServiceOverrideInfoConfig().getConfigFiles());
  }

  private boolean isEnvGlobalManifestOverridesPresent(NGEnvironmentConfig ngEnvironmentConfig) {
    if (!isEnvGlobalOverridesPresent(ngEnvironmentConfig)) {
      return false;
    }
    return isNotEmpty(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getManifests());
  }

  private boolean isSvcOverridesManifestPresent(NGServiceOverrideConfig serviceOverrideConfig) {
    if (!isServiceOverridePresent(serviceOverrideConfig)) {
      return false;
    }
    return isNotEmpty(serviceOverrideConfig.getServiceOverrideInfoConfig().getManifests());
  }

  @VisibleForTesting
  NGServiceOverrideConfig fetchServiceOverrideConfig(
      PlanCreationContext ctx, NGServiceV2InfoConfig serviceV2InfoConfig, String envRef) {
    PlanCreationContextValue metadata = ctx.getMetadata();
    final Optional<NGServiceOverridesEntity> serviceOverridesEntity =
        serviceOverrideService.get(metadata.getAccountIdentifier(), metadata.getOrgIdentifier(),
            metadata.getProjectIdentifier(), envRef, serviceV2InfoConfig.getIdentifier());

    return serviceOverridesEntity.map(NGServiceOverrideEntityConfigMapper::toNGServiceOverrideConfig).orElse(null);
  }

  @VisibleForTesting
  NGEnvironmentConfig fetchEnvironmentConfig(PlanCreationContext ctx, KryoSerializer kryoSerializer) {
    ParameterField<String> envRefField = (ParameterField<String>) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.ENVIRONMENT_REF).toByteArray());
    PlanCreationContextValue metadata = ctx.getMetadata();
    Optional<Environment> environment = environmentService.get(metadata.getAccountIdentifier(),
        metadata.getOrgIdentifier(), metadata.getProjectIdentifier(), envRefField.getValue(), false);
    return EnvironmentMapper.toNGEnvironmentConfig(environment.get());
  }

  private void addServiceSpecNode(ServiceConfig serviceConfig,
      Map<String, PlanCreationResponse> planCreationResponseMap, List<String> serviceSpecChildrenIds) {
    ServiceSpec serviceSpec = serviceConfig.getServiceDefinition().getServiceSpec();
    ServiceSpecStepParameters stepParameters =
        ServiceSpecStepParameters.builder()
            .originalVariables(ParameterField.createValueField(serviceSpec.getVariables()))
            .stageOverrideVariables(serviceConfig.getStageOverrides() == null
                    ? null
                    : ParameterField.createValueField(serviceConfig.getStageOverrides().getVariables()))
            .childrenNodeIds(serviceSpecChildrenIds)
            .build();
    PlanNode node =
        PlanNode.builder()
            .uuid(serviceConfig.getServiceDefinition().getServiceSpec().getUuid())
            .stepType(ServiceSpecStep.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_SPEC_NODE_NAME)
            .identifier(YamlTypes.SERVICE_SPEC)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(EmptyPredicate.isEmpty(serviceSpecChildrenIds)
                            ? FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build()
                            : FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .skipExpressionChain(false)
            .build();
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().node(node.getUuid(), node).build());
  }

  private void addServiceSpecNodeV2(NGServiceV2InfoConfig serviceV2InfoConfig,
      Map<String, PlanCreationResponse> planCreationResponseMap, List<String> serviceSpecChildrenIds) {
    ServiceSpec serviceSpec = serviceV2InfoConfig.getServiceDefinition().getServiceSpec();
    ServiceSpecStepParameters stepParameters =
        ServiceSpecStepParameters.builder()
            .originalVariables(ParameterField.createValueField(serviceSpec.getVariables()))
            .childrenNodeIds(serviceSpecChildrenIds)
            .build();
    PlanNode node =
        PlanNode.builder()
            .uuid(serviceV2InfoConfig.getServiceDefinition().getServiceSpec().getUuid())
            .stepType(ServiceSpecStep.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_SPEC_NODE_NAME)
            .identifier(YamlTypes.SERVICE_SPEC)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(EmptyPredicate.isEmpty(serviceSpecChildrenIds)
                            ? FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build()
                            : FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .skipExpressionChain(false)
            .build();
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().node(node.getUuid(), node).build());
  }

  String addDependenciesForConfigFiles(YamlNode serviceConfigNode,
      Map<String, PlanCreationResponse> planCreationResponseMap, ServiceConfig serviceConfig) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    boolean isUseFromStage = serviceConfig.getUseFromStage() != null;
    YamlField configFilesYamlField =
        ConfigFileUtility.fetchConfigFilesYamlFieldAndSetYamlUpdates(serviceConfigNode, isUseFromStage, yamlUpdates);
    String configFilesPlanNodeId = "configFiles-" + UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        ServiceDefinitionPlanCreatorHelper.prepareMetadata(configFilesPlanNodeId, serviceConfig, kryoSerializer);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(configFilesPlanNodeId, configFilesYamlField);
    PlanCreationResponseBuilder configFilesPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                configFilesPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      configFilesPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(configFilesPlanNodeId, configFilesPlanCreationResponse.build());
    return configFilesPlanNodeId;
  }

  boolean shouldCreatePlanNodeForConfigFiles(ServiceConfig actualServiceConfig) {
    List<ConfigFileWrapper> configFiles = actualServiceConfig.getServiceDefinition().getServiceSpec().getConfigFiles();

    if (isNotEmpty(configFiles)) {
      return true;
    }

    return actualServiceConfig.getStageOverrides() != null
        && actualServiceConfig.getStageOverrides().getConfigFiles() != null
        && isNotEmpty(actualServiceConfig.getStageOverrides().getConfigFiles());
  }

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.SERVICE_DEFINITION,
        ImmutableSet.of(ServiceSpecType.KUBERNETES, ServiceSpecType.SSH, ServiceSpecType.WINRM,
            ServiceSpecType.NATIVE_HELM, ServiceSpecType.SERVERLESS_AWS_LAMBDA, ServiceSpecType.AZURE_WEBAPP));
  }
}
