/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STRATEGY;

import io.harness.NGCommonEntityConstants;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.cdng.artifact.mappers.ArtifactResponseToOutcomeMapper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.InfrastructureMapper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.AbstractStageVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.yaml.core.variables.NGVariable;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeploymentStageVariableCreator extends AbstractStageVariableCreator<DeploymentStageNode> {
  private static final String SIDECARS_PREFIX = "artifacts.sidecars";
  private static final String PRIMARY = "primary";

  @Inject private ServiceEntityService serviceEntityService;
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceOverrideService serviceOverrideService;
  @Inject private InfrastructureEntityService infrastructureEntityService;

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    YamlField serviceField =
        config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.SERVICE_CONFIG);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(serviceField)) {
      VariableCreationResponse serviceVariableResponse = ServiceVariableCreator.createVariableResponse(serviceField);
      responseMap.put(serviceField.getNode().getUuid(), serviceVariableResponse);
    }

    YamlField infraField =
        config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.PIPELINE_INFRASTRUCTURE);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(infraField)) {
      VariableCreationResponse infraVariableResponse = InfraVariableCreator.createVariableResponse(infraField);
      responseMap.put(infraField.getNode().getUuid(), infraVariableResponse);
    }

    YamlField executionField =
        config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.EXECUTION);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(executionField)) {
      Map<String, YamlField> executionDependencyMap = new HashMap<>();
      executionDependencyMap.put(executionField.getNode().getUuid(), executionField);
      responseMap.put(executionField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(executionDependencyMap))
              .build());
    }

    YamlField strategyField = config.getNode().getField(STRATEGY);

    if (strategyField != null) {
      Map<String, YamlField> strategyDependencyMap = new HashMap<>();
      strategyDependencyMap.put(strategyField.getNode().getUuid(), strategyField);
      responseMap.put(strategyField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(strategyDependencyMap))
              .build());
    }

    return responseMap;
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, DeploymentStageNode config) {
    YamlField currentField = ctx.getCurrentField();

    LinkedHashMap<String, VariableCreationResponse> responseMap =
        createVariablesForChildrenNodesPipelineV2Yaml(ctx, config);
    // v1
    // add dependencies for provision node
    YamlField infraField = currentField.getNode()
                               .getField(YAMLFieldNameConstants.SPEC)
                               .getNode()
                               .getField(YamlTypes.PIPELINE_INFRASTRUCTURE);

    if (VariableCreatorHelper.isNotYamlFieldEmpty(infraField)) {
      Map<String, YamlField> infraDependencyMap = new LinkedHashMap<>();
      YamlField infraDefNode = infraField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEF);
      if (VariableCreatorHelper.isNotYamlFieldEmpty(infraDefNode)
          && VariableCreatorHelper.isNotYamlFieldEmpty(infraDefNode.getNode().getField(YamlTypes.SPEC))) {
        YamlField provisionerField = infraDefNode.getNode().getField(YAMLFieldNameConstants.PROVISIONER);
        if (provisionerField != null) {
          infraDependencyMap.putAll(InfraVariableCreator.addDependencyForProvisionerSteps(provisionerField));
        }
      }
      responseMap.put(infraField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(infraDependencyMap))
              .build());
    }

    // add dependencies for execution node
    YamlField executionField = currentField.getNode()
                                   .getField(YAMLFieldNameConstants.SPEC)
                                   .getNode()
                                   .getField(YAMLFieldNameConstants.EXECUTION);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(executionField)) {
      Map<String, YamlField> executionDependencyMap = new HashMap<>();
      executionDependencyMap.put(executionField.getNode().getUuid(), executionField);
      responseMap.put(executionField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(executionDependencyMap))
              .build());
    }
    YamlField strategyField = currentField.getNode().getField(STRATEGY);

    if (strategyField != null) {
      Map<String, YamlField> strategyDependencyMap = new HashMap<>();
      strategyDependencyMap.put(strategyField.getNode().getUuid(), strategyField);
      responseMap.put(strategyField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(strategyDependencyMap))
              .build());
    }
    return responseMap;
  }

  private LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesPipelineV2Yaml(
      VariableCreationContext ctx, DeploymentStageNode config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    try {
      final ParameterField<String> serviceRef = getServiceRef(config);
      final ParameterField<String> environmentRef = getEnvironmentRef(config);
      final List<ParameterField<String>> infraDefinitionRefs = getInfraDefinitionRefs(config);
      // for collecting service variables from service/env/service overrides
      Set<String> serviceVariables = new HashSet<>();

      if (environmentRef != null) {
        createVariablesForEnvironment(ctx, environmentRef, responseMap, serviceVariables);
      }
      if (environmentRef != null && infraDefinitionRefs != null) {
        // todo: multi-infra
        createVariablesForInfraDefinitions(ctx, environmentRef, infraDefinitionRefs, responseMap);
      }
      if (serviceRef != null) {
        createVariablesForService(ctx, environmentRef, serviceRef, serviceVariables, responseMap);
      }
    } catch (Exception ex) {
      log.error("Exception during Deployment Stage Node variable creation", ex);
    }
    return responseMap;
  }

  private void createVariablesForService(VariableCreationContext ctx, ParameterField<String> environmentRef,
      ParameterField<String> serviceRef, Set<String> serviceVariables,
      LinkedHashMap<String, VariableCreationResponse> responseMap) {
    final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
    final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
    final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

    YamlField currentField = ctx.getCurrentField();
    List<YamlProperties> outputProperties = new LinkedList<>();
    Map<String, YamlExtraProperties> yamlPropertiesMap = new LinkedHashMap<>();

    // service node in v2 yaml
    YamlField serviceField =
        currentField.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.SERVICE_ENTITY);

    if (!serviceRef.isExpression()) {
      outputProperties.addAll(handleServiceStepOutcome());
      Optional<ServiceEntity> optionalService =
          serviceEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef.getValue(), false);

      NGServiceConfig ngServiceConfig = null;
      if (optionalService.isPresent()) {
        ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(optionalService.get());
      }
      outputProperties.addAll(handleManifestProperties(ngServiceConfig));
      outputProperties.addAll(handleArtifactProperties(ngServiceConfig));
      if (environmentRef != null && !environmentRef.isExpression()) {
        serviceVariables.addAll(getServiceOverridesVariables(ctx, environmentRef, ngServiceConfig));
      }
      outputProperties.addAll(handleServiceVariables(serviceVariables, ngServiceConfig));
    } else {
      outputProperties.addAll(handleServiceStepOutcome());
    }
    yamlPropertiesMap.put(serviceField.getNode().getUuid(),
        YamlExtraProperties.newBuilder().addAllOutputProperties(outputProperties).build());
    responseMap.put(serviceField.getNode().getUuid(),
        VariableCreationResponse.builder().yamlExtraProperties(yamlPropertiesMap).build());
  }

  private void createVariablesForInfraDefinitions(VariableCreationContext ctx, ParameterField<String> environmentRef,
      List<ParameterField<String>> infraDefinitionRefs, LinkedHashMap<String, VariableCreationResponse> responseMap) {
    ParameterField<String> infraRef = infraDefinitionRefs.get(0);
    final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
    final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
    final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

    if (infraRef != null && !infraRef.isExpression()) {
      Optional<InfrastructureEntity> infrastructureEntity = infrastructureEntityService.get(
          accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(), infraRef.getValue());

      infrastructureEntity.ifPresent(entity -> handleInfrastructureOutcome(entity, ctx, responseMap));
    }
  }

  private void handleInfrastructureOutcome(InfrastructureEntity infrastructureEntity, VariableCreationContext ctx,
      LinkedHashMap<String, VariableCreationResponse> responseMap) {
    YamlField currentField = ctx.getCurrentField();
    YamlField envField =
        currentField.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.ENVIRONMENT_YAML);
    YamlField infraDefinitionField = envField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEFS);
    Map<String, YamlExtraProperties> yamlPropertiesMap = new LinkedHashMap<>();
    List<YamlProperties> outputProperties = new LinkedList<>();

    InfrastructureConfig infrastructureConfig =
        InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureEntity);
    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(infrastructureConfig.getInfrastructureDefinitionConfig().getSpec(),
            EnvironmentOutcome.builder().build(), ServiceStepOutcome.builder().build());

    List<String> infraStepOutputExpressions =
        VariableCreatorHelper.getExpressionsInObject(infrastructureOutcome, OutputExpressionConstants.INFRA);

    for (String outputExpression : infraStepOutputExpressions) {
      outputProperties.add(YamlProperties.newBuilder().setLocalName(outputExpression).setVisible(true).build());
    }

    // 1st member of infra definition list
    yamlPropertiesMap.put(infraDefinitionField.getNode().asArray().get(0).getUuid(),
        YamlExtraProperties.newBuilder().addAllOutputProperties(outputProperties).build());
    responseMap.put(infraDefinitionField.getNode().asArray().get(0).getUuid(),
        VariableCreationResponse.builder().yamlExtraProperties(yamlPropertiesMap).build());
  }

  private Set<String> getServiceOverridesVariables(
      VariableCreationContext ctx, ParameterField<String> environmentRef, NGServiceConfig ngServiceConfig) {
    Set<String> variables = new HashSet<>();
    final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
    final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
    final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

    if (ngServiceConfig != null) {
      Optional<NGServiceOverridesEntity> serviceOverridesEntity =
          serviceOverrideService.get(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(),
              ngServiceConfig.getNgServiceV2InfoConfig().getIdentifier());
      if (serviceOverridesEntity.isPresent()) {
        NGServiceOverrideConfig serviceOverrideConfig =
            NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity.get());
        List<NGVariable> variableOverrides = serviceOverrideConfig.getServiceOverrideInfoConfig().getVariables();
        variables.addAll(variableOverrides.stream().map(NGVariable::getName).collect(Collectors.toSet()));
      }
    }
    return variables;
  }

  private void createVariablesForEnvironment(VariableCreationContext ctx, ParameterField<String> environmentRef,
      LinkedHashMap<String, VariableCreationResponse> responseMap, Set<String> serviceVariables) {
    Map<String, YamlExtraProperties> yamlPropertiesMap = new LinkedHashMap<>();
    List<YamlProperties> outputProperties = new LinkedList<>();
    final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
    final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
    final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

    YamlField currentField = ctx.getCurrentField();
    // environment node in v2 yaml
    YamlField environmentField =
        currentField.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.ENVIRONMENT_YAML);

    if (!environmentRef.isExpression()) {
      Optional<Environment> optionalEnvironment =
          environmentService.get(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(), false);
      NGEnvironmentConfig ngEnvironmentConfig = null;
      if (optionalEnvironment.isPresent()) {
        ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(optionalEnvironment.get());
      }
      outputProperties.addAll(handleEnvironmentOutcome(ngEnvironmentConfig));
      // all env.variables also accessed by serviceVariables
      List<NGVariable> envVariables = ngEnvironmentConfig.getNgEnvironmentInfoConfig().getVariables();
      if (EmptyPredicate.isNotEmpty(envVariables)) {
        serviceVariables.addAll(envVariables.stream().map(NGVariable::getName).collect(Collectors.toSet()));
      }
    } else {
      outputProperties.addAll(handleEnvironmentOutcome(null));
    }
    yamlPropertiesMap.put(environmentField.getNode().getUuid(),
        YamlExtraProperties.newBuilder().addAllOutputProperties(outputProperties).build());
    responseMap.put(environmentField.getNode().getUuid(),
        VariableCreationResponse.builder().yamlExtraProperties(yamlPropertiesMap).build());
  }

  private List<YamlProperties> handleEnvironmentOutcome(NGEnvironmentConfig ngEnvironmentConfig) {
    List<YamlProperties> outputProperties = new ArrayList<>();

    List<NGVariable> envVariables = ngEnvironmentConfig == null
        ? new ArrayList<>()
        : ngEnvironmentConfig.getNgEnvironmentInfoConfig().getVariables();
    EnvironmentOutcome environmentOutcome =
        EnvironmentOutcome.builder()
            .variables(EmptyPredicate.isNotEmpty(envVariables)
                    ? envVariables.stream().collect(Collectors.toMap(NGVariable::getName, NGVariable::getCurrentValue))
                    : null)
            .build();
    List<String> envStepOutputExpressions =
        VariableCreatorHelper.getExpressionsInObject(environmentOutcome, OutputExpressionConstants.ENVIRONMENT);

    for (String outputExpression : envStepOutputExpressions) {
      outputProperties.add(YamlProperties.newBuilder().setLocalName(outputExpression).setVisible(true).build());
    }
    return outputProperties;
  }

  private ParameterField<String> getServiceRef(DeploymentStageNode stageNode) {
    ServiceYamlV2 serviceYamlV2 = stageNode.getDeploymentStageConfig().getService();
    if (serviceYamlV2 != null) {
      return serviceYamlV2.getServiceRef();
    }
    return null;
  }

  private ParameterField<String> getEnvironmentRef(DeploymentStageNode stageNode) {
    EnvironmentYamlV2 environmentYamlV2 = stageNode.getDeploymentStageConfig().getEnvironment();
    if (environmentYamlV2 != null) {
      return environmentYamlV2.getEnvironmentRef();
    }
    return null;
  }

  private List<ParameterField<String>> getInfraDefinitionRefs(DeploymentStageNode stageNode) {
    EnvironmentYamlV2 environmentYamlV2 = stageNode.getDeploymentStageConfig().getEnvironment();
    if (environmentYamlV2 != null) {
      List<InfraStructureDefinitionYaml> infraStructureDefinitionYamls =
          environmentYamlV2.getInfrastructureDefinitions();
      if (EmptyPredicate.isNotEmpty(infraStructureDefinitionYamls)) {
        return infraStructureDefinitionYamls.stream()
            .map(InfraStructureDefinitionYaml::getIdentifier)
            .collect(Collectors.toList());
      }
    }
    return null;
  }

  private String getEnvironmentRef(VariableCreationContext ctx) {
    YamlField currentField = ctx.getCurrentField();
    // environment node in v2 yaml
    YamlField envField =
        currentField.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.ENVIRONMENT_YAML);

    if (VariableCreatorHelper.isNotYamlFieldEmpty(envField)) {
      if (VariableCreatorHelper.isNotYamlFieldEmpty(envField.getNode().getField(YamlTypes.ENVIRONMENT_REF))) {
        return envField.getNode().getField(YamlTypes.ENVIRONMENT_REF).getNode().getCurrJsonNode().asText();
      }
    }
    return null;
  }

  private String getInfraDefinitionRef(VariableCreationContext ctx) {
    YamlField currentField = ctx.getCurrentField();
    // environment node in v2 yaml
    YamlField envField =
        currentField.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.ENVIRONMENT_YAML);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(envField)) {
      YamlField infraDefinitionField = envField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEFS);
      if (VariableCreatorHelper.isNotYamlFieldEmpty(infraDefinitionField)) {
        // pick 1st node
        // todo(hinger): multi infra
        return infraDefinitionField.getNode().getCurrJsonNode().get(0).get(YamlTypes.REF).asText();
      }
    }
    return null;
  }

  private List<YamlProperties> handleServiceStepOutcome() {
    List<YamlProperties> outputProperties = new ArrayList<>();
    ServiceStepOutcome serviceStepOutcome = ServiceStepOutcome.builder().build();
    // constance for service
    List<String> serviceStepOutputExpressions =
        VariableCreatorHelper.getExpressionsInObject(serviceStepOutcome, "service");

    for (String outputExpression : serviceStepOutputExpressions) {
      outputProperties.add(YamlProperties.newBuilder().setLocalName(outputExpression).setVisible(true).build());
    }
    return outputProperties;
  }

  private List<YamlProperties> handleArtifactProperties(NGServiceConfig ngServiceConfig) {
    List<YamlProperties> outputProperties = new ArrayList<>();
    if (ngServiceConfig != null) {
      ArtifactListConfig artifactListConfig =
          ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getArtifacts();
      if (artifactListConfig != null) {
        List<SidecarArtifactWrapper> sidecarArtifactWrapperList = artifactListConfig.getSidecars();
        for (SidecarArtifactWrapper sideCarArtifact : sidecarArtifactWrapperList) {
          SidecarArtifact sidecar = sideCarArtifact.getSidecar();
          String identifier = sidecar.getIdentifier();
          ArtifactOutcome sideCarArtifactOutcome =
              ArtifactResponseToOutcomeMapper.toArtifactOutcome(sidecar.getSpec(), null, false);
          List<String> sideCarOutputExpressions =
              VariableCreatorHelper.getExpressionsInObject(sideCarArtifactOutcome, identifier);

          for (String outputExpression : sideCarOutputExpressions) {
            outputProperties.add(YamlProperties.newBuilder()
                                     .setLocalName(SIDECARS_PREFIX + "." + outputExpression)
                                     .setVisible(true)
                                     .build());
          }
        }
        PrimaryArtifact primaryArtifact = artifactListConfig.getPrimary();
        if (primaryArtifact != null) {
          ArtifactOutcome primaryArtifactOutcome =
              ArtifactResponseToOutcomeMapper.toArtifactOutcome(primaryArtifact.getSpec(), null, false);
          List<String> primaryArtifactExpressions =
              VariableCreatorHelper.getExpressionsInObject(primaryArtifactOutcome, PRIMARY);

          for (String outputExpression : primaryArtifactExpressions) {
            outputProperties.add(YamlProperties.newBuilder()
                                     .setLocalName(OutcomeExpressionConstants.ARTIFACTS + "." + outputExpression)
                                     .setVisible(true)
                                     .build());
          }
        }
      }
    }
    return outputProperties;
  }

  private List<YamlProperties> handleManifestProperties(NGServiceConfig ngServiceConfig) {
    List<YamlProperties> outputProperties = new ArrayList<>();
    if (ngServiceConfig != null) {
      List<ManifestConfigWrapper> manifestConfigWrappers =
          ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getManifests();

      for (ManifestConfigWrapper manifestWrapper : manifestConfigWrappers) {
        ManifestConfig manifestConfig = manifestWrapper.getManifest();
        String identifier = manifestConfig.getIdentifier();

        ManifestOutcome outcome = ManifestOutcomeMapper.toManifestOutcome(manifestConfig.getSpec(), 0);

        List<String> manifestOutputExpressions = VariableCreatorHelper.getExpressionsInObject(outcome, identifier);

        for (String outputExpression : manifestOutputExpressions) {
          outputProperties.add(YamlProperties.newBuilder()
                                   .setLocalName(OutcomeExpressionConstants.MANIFESTS + "." + outputExpression)
                                   .setVisible(true)
                                   .build());
        }
      }
    }
    return outputProperties;
  }

  private List<YamlProperties> handleServiceVariables(
      Set<String> existingServiceVariables, NGServiceConfig ngServiceConfig) {
    List<YamlProperties> outputProperties = new ArrayList<>();
    if (ngServiceConfig != null) {
      List<NGVariable> ngVariableList =
          ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getVariables();
      if (EmptyPredicate.isNotEmpty(ngVariableList)) {
        existingServiceVariables.addAll(ngVariableList.stream().map(NGVariable::getName).collect(Collectors.toSet()));
      }

      List<String> outputExpressions = existingServiceVariables.stream()
                                           .map(entry -> YAMLFieldNameConstants.SERVICE_VARIABLES + '.' + entry)
                                           .collect(Collectors.toList());

      for (String outputExpression : outputExpressions) {
        outputProperties.add(YamlProperties.newBuilder().setLocalName(outputExpression).setVisible(true).build());
      }
    }
    return outputProperties;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.DEPLOYMENT_STAGE));
  }

  @Override
  public Class<DeploymentStageNode> getFieldClass() {
    return DeploymentStageNode.class;
  }
}
