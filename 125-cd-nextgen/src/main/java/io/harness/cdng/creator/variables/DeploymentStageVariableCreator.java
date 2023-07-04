/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STRATEGY;

import io.harness.NGCommonEntityConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.cdng.artifact.mappers.ArtifactResponseToOutcomeMapper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
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
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.evaluators.ProvisionerExpressionEvaluator;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
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
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.persistence.HIterator;
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
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

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
  private static final String OVERRIDE_PROJECT_SETTING_IDENTIFIER = "service_override_v2";
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceOverrideService serviceOverrideService;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private InfrastructureMapper infrastructureMapper;
  @Inject private ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Inject private ServiceOverridesServiceV2 serviceOverridesServiceV2;
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
      final ServicesYaml services = config.getDeploymentStageConfig().getServices();
      final EnvironmentsYaml environmentsYaml = config.getDeploymentStageConfig().getEnvironments();
      final EnvironmentYamlV2 environment = config.getDeploymentStageConfig().getEnvironment();
      final ParameterField<String> environmentRef = getEnvironmentRef(config);
      final String infraIdentifier = getInfraIdentifier(config);
      // for collecting service variables from service/env/service overrides
      Set<String> serviceVariables = new HashSet<>();

      if (environmentsYaml != null) {
        createVariablesForEnvironments(ctx, responseMap, environmentsYaml, serviceVariables);
      }
      if (environment != null) {
        createVariablesForEnvironment(ctx, responseMap, serviceVariables, environment);
      }
      if (serviceRef != null) {
        createVariablesForService(ctx, environmentRef, serviceRef, serviceVariables, responseMap, infraIdentifier);
      }
      if (services != null) {
        createVariablesForServices(ctx, responseMap, services, environmentRef, serviceVariables, infraIdentifier);
      }
    } catch (Exception ex) {
      log.error("Exception during Deployment Stage Node variable creation", ex);
    }
    return responseMap;
  }

  private void createVariablesForServices(VariableCreationContext ctx,
      LinkedHashMap<String, VariableCreationResponse> responseMap, ServicesYaml services,
      ParameterField<String> environmentRef, Set<String> serviceVariables, String infraIdentifier) {
    if (!services.getValues().isExpression()) {
      for (ServiceYamlV2 serviceRefValue : services.getValues().getValue()) {
        createVariablesForService(
            ctx, environmentRef, serviceRefValue.getServiceRef(), serviceVariables, responseMap, infraIdentifier);
      }
    }
  }

  private void createVariablesForEnvironments(VariableCreationContext ctx,
      LinkedHashMap<String, VariableCreationResponse> responseMap, EnvironmentsYaml environmentsYaml,
      Set<String> serviceVariables) {
    if (!environmentsYaml.getValues().isExpression()) {
      for (EnvironmentYamlV2 environmentYamlV2 : environmentsYaml.getValues().getValue()) {
        ParameterField<String> envRef = environmentYamlV2.getEnvironmentRef();
        if (envRef != null) {
          createVariablesForEnvironment(ctx, responseMap, serviceVariables, environmentYamlV2);
        }
      }
    }
  }

  private void createVariablesForService(VariableCreationContext ctx, ParameterField<String> environmentRef,
      ParameterField<String> serviceRef, Set<String> serviceVariables,
      LinkedHashMap<String, VariableCreationResponse> responseMap, String infraIdentifier) {
    final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
    final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
    final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

    YamlField currentField = ctx.getCurrentField();
    List<YamlProperties> outputProperties = new LinkedList<>();
    Map<String, YamlExtraProperties> yamlPropertiesMap = new LinkedHashMap<>();

    YamlField specField = currentField.getNode().getField(YAMLFieldNameConstants.SPEC);
    // service node in v2 yaml
    YamlField serviceField = specField.getNode().getField(YamlTypes.SERVICE_ENTITY);
    if (serviceField == null) {
      serviceField = specField.getNode().getField(YamlTypes.SERVICE_ENTITIES);
    }

    if (isNotEmpty(serviceRef.getValue()) && !serviceRef.isExpression()) {
      outputProperties.addAll(handleServiceStepOutcome(serviceField));

      // scoped service ref used here
      Optional<ServiceEntity> optionalService =
          serviceEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef.getValue(), false);

      NGServiceConfig ngServiceConfig = null;
      if (optionalService.isPresent()) {
        ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(optionalService.get());
      }
      outputProperties.addAll(handleManifestProperties(specField, ngServiceConfig));
      outputProperties.addAll(handleArtifactProperties(specField, ngServiceConfig));
      handleServiceOverridesV2(ctx, environmentRef, serviceRef, serviceVariables, infraIdentifier, accountIdentifier,
          orgIdentifier, projectIdentifier, optionalService);
      outputProperties.addAll(handleServiceVariables(specField, serviceVariables, ngServiceConfig));
    } else {
      outputProperties.addAll(handleServiceStepOutcome(serviceField));
      // handle serviceVariables from env
      outputProperties.addAll(handleServiceVariables(specField, serviceVariables, null));
    }
    yamlPropertiesMap.put(serviceField.getNode().getUuid(),
        YamlExtraProperties.newBuilder().addAllOutputProperties(outputProperties).build());
    responseMap.put(serviceField.getNode().getUuid(),
        VariableCreationResponse.builder().yamlExtraProperties(yamlPropertiesMap).build());
  }

  private void handleServiceOverridesV2(VariableCreationContext ctx, ParameterField<String> environmentRef,
      ParameterField<String> serviceRef, Set<String> serviceVariables, String infraIdentifier, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, Optional<ServiceEntity> optionalService) {
    if (environmentRef != null && !environmentRef.isExpression() && optionalService.isPresent()) {
      if (overrideV2ValidationHelper.isOverridesV2Enabled(accountIdentifier, orgIdentifier, projectIdentifier)) {
        Map<Scope, NGServiceOverridesEntity> envServiceOverride =
            serviceOverridesServiceV2.getEnvServiceOverride(accountIdentifier, orgIdentifier, projectIdentifier,
                environmentRef.getValue(), serviceRef.getValue(), null);
        addOverrideVariablesToSet(serviceVariables, envServiceOverride);
        if (isNotEmpty(infraIdentifier)) {
          Map<Scope, NGServiceOverridesEntity> infraServiceOverride =
              serviceOverridesServiceV2.getInfraServiceOverride(accountIdentifier, orgIdentifier, projectIdentifier,
                  environmentRef.getValue(), serviceRef.getValue(), infraIdentifier, null);
          addOverrideVariablesToSet(serviceVariables, infraServiceOverride);
        }
      } else {
        serviceVariables.addAll(getServiceOverridesVariables(ctx, environmentRef, optionalService.get()));
      }
    }
  }

  private void addOverrideVariablesToSet(
      Set<String> serviceVariables, Map<Scope, NGServiceOverridesEntity> serviceOverride) {
    if (isNotEmpty(serviceOverride)) {
      List<NGServiceOverridesEntity> serviceOverridesEntities = new ArrayList<>(serviceOverride.values());
      serviceOverridesEntities.forEach(
          entity -> serviceVariables.addAll(NGVariablesUtils.getSetOfVars(entity.getSpec().getVariables())));
    }
  }

  private List<NGVariable> getVariablesList(Map<Scope, NGServiceOverridesEntity> serviceOverride) {
    List<NGVariable> ngVariableList = new ArrayList<>();
    if (isNotEmpty(serviceOverride)) {
      List<NGServiceOverridesEntity> serviceOverridesEntities = new ArrayList<>(serviceOverride.values());
      serviceOverridesEntities.forEach(entity -> {
        List<NGVariable> ngVariables = entity.getSpec().getVariables();
        if (isNotEmpty(ngVariables)) {
          ngVariableList.addAll(ngVariables);
        }
      });
    }
    return ngVariableList;
  }

  private void createVariablesForInfraDefinitions(VariableCreationContext ctx, YamlField specField,
      ParameterField<String> environmentRef, LinkedHashMap<String, VariableCreationResponse> responseMap,
      EnvironmentYamlV2 environmentYamlV2) {
    if (!environmentYamlV2.getInfrastructureDefinitions().isExpression()) {
      final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
      final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
      final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

      final List<InfraStructureDefinitionYaml> infrastructures =
          CollectionUtils.emptyIfNull(environmentYamlV2.getInfrastructureDefinitions().getValue())
              .stream()
              .filter(infra -> !infra.getIdentifier().isExpression())
              .collect(Collectors.toList());
      final Map<String, String> identifierToNodeUuid = infrastructures.stream().collect(
          Collectors.toMap(i -> i.getIdentifier().getValue(), InfraStructureDefinitionYaml::getUuid));

      final Set<String> infraIdentifiers = infrastructures.stream()
                                               .map(InfraStructureDefinitionYaml::getIdentifier)
                                               .map(ParameterField::getValue)
                                               .collect(Collectors.toSet());

      try (HIterator<InfrastructureEntity> iterator = infrastructureEntityService.listIterator(
               accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(), infraIdentifiers)) {
        for (InfrastructureEntity entity : iterator) {
          addInfrastructureProperties(entity, specField, responseMap, identifierToNodeUuid.get(entity.getIdentifier()));
        }
      }
    }
  }
  private void createVariablesForInfraDefinition(VariableCreationContext ctx, YamlField specField,
      ParameterField<String> environmentRef, LinkedHashMap<String, VariableCreationResponse> responseMap,
      EnvironmentYamlV2 environmentYamlV2) {
    if (!environmentYamlV2.getInfrastructureDefinition().isExpression()) {
      final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
      final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
      final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

      final InfraStructureDefinitionYaml infraStructureDefinitionYaml =
          environmentYamlV2.getInfrastructureDefinition().getValue();

      if (ParameterField.isNotNull(infraStructureDefinitionYaml.getIdentifier())
          && !infraStructureDefinitionYaml.getIdentifier().isExpression()) {
        Optional<InfrastructureEntity> infrastructureEntityOpt =
            infrastructureEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier,
                environmentRef.getValue(), infraStructureDefinitionYaml.getIdentifier().getValue());
        infrastructureEntityOpt.ifPresent(
            i -> addInfrastructureProperties(i, specField, responseMap, infraStructureDefinitionYaml.getUuid()));
      }
    }
  }

  private void addInfrastructureProperties(InfrastructureEntity infrastructureEntity, YamlField specField,
      LinkedHashMap<String, VariableCreationResponse> responseMap, String infraNodeUuid) {
    Map<String, YamlExtraProperties> yamlPropertiesMap = new LinkedHashMap<>();
    List<YamlProperties> outputProperties = new LinkedList<>();
    InfrastructureConfig infrastructureConfig =
        InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureEntity);
    InfrastructureOutcome infrastructureOutcome =
        infrastructureMapper.toOutcome(infrastructureConfig.getInfrastructureDefinitionConfig().getSpec(),
            new ProvisionerExpressionEvaluator(Collections.emptyMap()), EnvironmentOutcome.builder().build(),
            ServiceStepOutcome.builder().build(), infrastructureEntity.getAccountId(),
            infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier(),
            infrastructureConfig.getInfrastructureDefinitionConfig().getTags());

    List<String> infraStepOutputExpressions =
        VariableCreatorHelper.getExpressionsInObject(infrastructureOutcome, OutputExpressionConstants.INFRA);

    final String stageFqn = YamlUtils.getFullyQualifiedName(specField.getNode());
    for (String outputExpression : infraStepOutputExpressions) {
      String fqn = stageFqn + "." + outputExpression;
      outputProperties.add(
          YamlProperties.newBuilder().setLocalName(outputExpression).setFqn(fqn).setVisible(true).build());
    }

    yamlPropertiesMap.put(
        infraNodeUuid, YamlExtraProperties.newBuilder().addAllOutputProperties(outputProperties).build());
    responseMap.put(infraNodeUuid, VariableCreationResponse.builder().yamlExtraProperties(yamlPropertiesMap).build());
  }

  private Set<String> getServiceOverridesVariables(
      VariableCreationContext ctx, ParameterField<String> environmentRef, ServiceEntity serviceEntity) {
    Set<String> variables = new HashSet<>();
    final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
    final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
    final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

    String scopedServiceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(serviceEntity.getAccountId(),
        serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier());
    Optional<NGServiceOverridesEntity> serviceOverridesEntity = serviceOverrideService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(), scopedServiceRef);
    if (serviceOverridesEntity.isPresent()) {
      NGServiceOverrideConfig serviceOverrideConfig =
          NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity.get());
      List<NGVariable> variableOverrides = serviceOverrideConfig.getServiceOverrideInfoConfig().getVariables();
      if (EmptyPredicate.isNotEmpty(variableOverrides)) {
        variables.addAll(variableOverrides.stream()
                             .map(NGVariable::getName)
                             .filter(EmptyPredicate::isNotEmpty)
                             .collect(Collectors.toSet()));
      }
    }
    return variables;
  }

  private void createVariablesForEnvironment(VariableCreationContext ctx,
      LinkedHashMap<String, VariableCreationResponse> responseMap, Set<String> serviceVariables,
      EnvironmentYamlV2 environmentYamlV2) {
    Map<String, YamlExtraProperties> yamlPropertiesMap = new LinkedHashMap<>();
    List<YamlProperties> outputProperties = new LinkedList<>();
    final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
    final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
    final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

    final ParameterField<String> environmentRef = environmentYamlV2.getEnvironmentRef();

    final YamlField specField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC);
    List<NGVariable> envVariables = new ArrayList<>();
    if (isNotEmpty(environmentRef.getValue()) && !environmentRef.isExpression()) {
      // scoped environment ref provided here
      Optional<Environment> optionalEnvironment =
          environmentService.get(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(), false);
      if (optionalEnvironment.isPresent()) {
        if (overrideV2ValidationHelper.isOverridesV2Enabled(accountIdentifier, orgIdentifier, projectIdentifier)) {
          // add all env global overrides
          Map<Scope, NGServiceOverridesEntity> envOverride = serviceOverridesServiceV2.getEnvOverride(
              accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(), null);
          envVariables.addAll(getVariablesList(envOverride));

          // add all infra global overrides
          if (ParameterField.isNotNull(environmentYamlV2.getInfrastructureDefinitions())) {
            final List<InfraStructureDefinitionYaml> infrastructures =
                CollectionUtils.emptyIfNull(environmentYamlV2.getInfrastructureDefinitions().getValue())
                    .stream()
                    .filter(infra -> !infra.getIdentifier().isExpression())
                    .collect(Collectors.toList());
            final Set<String> infraIdentifiers = infrastructures.stream()
                                                     .map(InfraStructureDefinitionYaml::getIdentifier)
                                                     .map(ParameterField::getValue)
                                                     .collect(Collectors.toSet());
            infraIdentifiers.forEach(infraId
                -> envVariables.addAll(
                    getInfraVarsList(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef, infraId)));
          }

          if (ParameterField.isNotNull(environmentYamlV2.getInfrastructureDefinition())) {
            final InfraStructureDefinitionYaml infraStructureDefinitionYaml =
                environmentYamlV2.getInfrastructureDefinition().getValue();
            if (ParameterField.isNotNull(infraStructureDefinitionYaml.getIdentifier())
                && !infraStructureDefinitionYaml.getIdentifier().isExpression()) {
              String infraId = infraStructureDefinitionYaml.getIdentifier().getValue();
              if (isNotEmpty(infraId)) {
                envVariables.addAll(
                    getInfraVarsList(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef, infraId));
              }
            }
          }
        } else {
          final NGEnvironmentConfig ngEnvironmentConfig =
              EnvironmentMapper.toNGEnvironmentConfig(optionalEnvironment.get());
          // all env.variables also accessed by serviceVariables
          if (ngEnvironmentConfig != null) {
            NGEnvironmentInfoConfig ngEnvironmentInfoConfig = ngEnvironmentConfig.getNgEnvironmentInfoConfig();
            if (ngEnvironmentInfoConfig != null) {
              List<NGVariable> ngVariables = ngEnvironmentInfoConfig.getVariables();
              if (isNotEmpty(ngVariables)) {
                envVariables.addAll(ngVariables);
              }
            }
          }
        }
        outputProperties.addAll(handleEnvironmentOutcome(specField, envVariables));
        if (isNotEmpty(envVariables)) {
          serviceVariables.addAll(envVariables.stream().map(NGVariable::getName).collect(Collectors.toSet()));
        }
      }
    } else {
      outputProperties.addAll(handleEnvironmentOutcome(specField, envVariables));
    }
    yamlPropertiesMap.put(
        environmentYamlV2.getUuid(), YamlExtraProperties.newBuilder().addAllOutputProperties(outputProperties).build());
    addProvisionerDependencyForSingleEnvironment(responseMap, specField);
    responseMap.put(
        environmentYamlV2.getUuid(), VariableCreationResponse.builder().yamlExtraProperties(yamlPropertiesMap).build());

    // Create variables for infrastructure definitions/infrastructure definition
    if (ParameterField.isNotNull(environmentYamlV2.getInfrastructureDefinitions())) {
      createVariablesForInfraDefinitions(ctx, specField, environmentRef, responseMap, environmentYamlV2);
    } else if (ParameterField.isNotNull(environmentYamlV2.getInfrastructureDefinition())) {
      createVariablesForInfraDefinition(ctx, specField, environmentRef, responseMap, environmentYamlV2);
    }
  }

  private List<NGVariable> getInfraVarsList(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ParameterField<String> environmentRef, String infraId) {
    Map<Scope, NGServiceOverridesEntity> infraOverride = serviceOverridesServiceV2.getInfraOverride(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(), infraId, null);
    return getVariablesList(infraOverride);
  }

  private void addProvisionerDependencyForSingleEnvironment(
      LinkedHashMap<String, VariableCreationResponse> responseMap, YamlField specField) {
    final YamlField envField = specField.getNode().getField(YAMLFieldNameConstants.ENVIRONMENT);
    YamlField provisionerField = null;
    if (envField != null) {
      provisionerField = envField.getNode().getField(YAMLFieldNameConstants.PROVISIONER);
    }
    if (provisionerField != null) {
      Map<String, YamlField> provisionerFieldDependency =
          new HashMap<>(InfraVariableCreator.addDependencyForProvisionerSteps(provisionerField));

      responseMap.put(specField.getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(provisionerFieldDependency))
              .build());
    }
  }

  private List<YamlProperties> handleEnvironmentOutcome(YamlField specField, List<NGVariable> envVariables) {
    final String stageFqn = YamlUtils.getFullyQualifiedName(specField.getNode());
    List<YamlProperties> outputProperties = new ArrayList<>();
    EnvironmentOutcome environmentOutcome =
        EnvironmentOutcome.builder()
            .variables(isNotEmpty(envVariables)
                    ? envVariables.stream().collect(Collectors.toMap(NGVariable::getName, NGVariable::getCurrentValue))
                    : null)
            .build();
    List<String> envStepOutputExpressions =
        VariableCreatorHelper.getExpressionsInObject(environmentOutcome, OutputExpressionConstants.ENVIRONMENT);

    for (String outputExpression : envStepOutputExpressions) {
      String fqn = stageFqn + "." + outputExpression;
      outputProperties.add(
          YamlProperties.newBuilder().setLocalName(outputExpression).setFqn(fqn).setVisible(true).build());
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

  private String getInfraIdentifier(DeploymentStageNode stageNode) {
    EnvironmentYamlV2 environmentYamlV2 = stageNode.getDeploymentStageConfig().getEnvironment();
    if (environmentYamlV2 != null) {
      ParameterField<InfraStructureDefinitionYaml> infraStructureDefinitionYaml =
          environmentYamlV2.getInfrastructureDefinition();
      if (infraStructureDefinitionYaml != null && infraStructureDefinitionYaml.getValue() != null) {
        ParameterField<String> infraId = infraStructureDefinitionYaml.getValue().getIdentifier();
        if (infraId != null && infraId.getValue() != null) {
          return infraId.getValue();
        }
      }

      ParameterField<List<InfraStructureDefinitionYaml>> infraStructureDefinitionYamlList =
          environmentYamlV2.getInfrastructureDefinitions();
      if (infraStructureDefinitionYamlList != null && infraStructureDefinitionYamlList.getValue() != null) {
        List<InfraStructureDefinitionYaml> infraStructureDefinitionYamls = infraStructureDefinitionYamlList.getValue();
        InfraStructureDefinitionYaml yaml = infraStructureDefinitionYamls.get(0);
        ParameterField<String> infraId = yaml.getIdentifier();
        if (infraId != null && infraId.getValue() != null) {
          return infraId.getValue();
        }
      }
    }
    return null;
  }

  private List<YamlProperties> handleServiceStepOutcome(YamlField serviceField) {
    List<YamlProperties> outputProperties = new ArrayList<>();
    String fqn = YamlUtils.getFullyQualifiedName(serviceField.getNode());
    ServiceStepOutcome serviceStepOutcome = ServiceStepOutcome.builder().build();
    // constance for service
    List<String> serviceStepOutputExpressions = VariableCreatorHelper.getExpressionsInObject(serviceStepOutcome, "");

    for (String outputExpression : serviceStepOutputExpressions) {
      outputProperties.add(YamlProperties.newBuilder()
                               .setFqn(fqn + "." + outputExpression)
                               .setLocalName("service"
                                   + "." + outputExpression)
                               .setVisible(true)
                               .build());
    }
    return outputProperties;
  }

  private List<YamlProperties> handleArtifactProperties(YamlField specField, NGServiceConfig ngServiceConfig) {
    final String stageFqn = YamlUtils.getFullyQualifiedName(specField.getNode());
    List<YamlProperties> outputProperties = new ArrayList<>();
    if (ngServiceConfig != null) {
      ArtifactListConfig artifactListConfig =
          ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getArtifacts();
      if (artifactListConfig != null) {
        List<SidecarArtifactWrapper> sidecarArtifactWrapperList = artifactListConfig.getSidecars();
        if (isNotEmpty(sidecarArtifactWrapperList)) {
          for (SidecarArtifactWrapper sideCarArtifact : sidecarArtifactWrapperList) {
            SidecarArtifact sidecar = sideCarArtifact.getSidecar();
            String identifier = sidecar.getIdentifier();
            if (sidecar.getSpec() != null) {
              ArtifactOutcome sideCarArtifactOutcome =
                  ArtifactResponseToOutcomeMapper.toArtifactOutcome(sidecar.getSpec(), null, false);
              List<String> sideCarOutputExpressions =
                  VariableCreatorHelper.getExpressionsInObject(sideCarArtifactOutcome, identifier);

              for (String outputExpression : sideCarOutputExpressions) {
                String localFqn = SIDECARS_PREFIX + "." + outputExpression;
                String fqn = stageFqn + "." + localFqn;
                outputProperties.add(
                    YamlProperties.newBuilder().setLocalName(localFqn).setFqn(fqn).setVisible(true).build());
              }
            }
          }
        }
        PrimaryArtifact primaryArtifact = artifactListConfig.getPrimary();
        if (primaryArtifact != null) {
          Set<String> expressions = new HashSet<>();
          if (primaryArtifact.getSpec() != null) {
            populateExpressionsForArtifact(specField, outputProperties, primaryArtifact.getSpec(), expressions);
          }
          if (ParameterField.isNotNull(primaryArtifact.getPrimaryArtifactRef())
              && isNotEmpty(primaryArtifact.getSources())) {
            // If primary artifact ref is fixed, use only that particular artifact source
            if (!primaryArtifact.getPrimaryArtifactRef().isExpression()) {
              Optional<ArtifactSource> source =
                  primaryArtifact.getSources()
                      .stream()
                      .filter(s -> primaryArtifact.getPrimaryArtifactRef().getValue().equals(s.getIdentifier()))
                      .findFirst();
              source.ifPresent(
                  s -> populateExpressionsForArtifact(specField, outputProperties, s.getSpec(), expressions));
            } else {
              primaryArtifact.getSources().forEach(
                  s -> populateExpressionsForArtifact(specField, outputProperties, s.getSpec(), expressions));
            }
          }
        }
      }
    }
    return outputProperties;
  }

  private void populateExpressionsForArtifact(
      YamlField specField, List<YamlProperties> outputProperties, ArtifactConfig spec, Set<String> expressions) {
    // in case of template source, spec will be null
    if (spec != null) {
      final String stageFqn = YamlUtils.getFullyQualifiedName(specField.getNode());
      ArtifactOutcome primaryArtifactOutcome = ArtifactResponseToOutcomeMapper.toArtifactOutcome(spec, null, false);
      List<String> primaryArtifactExpressions =
          VariableCreatorHelper.getExpressionsInObject(primaryArtifactOutcome, PRIMARY);

      for (String outputExpression : primaryArtifactExpressions) {
        if (expressions.add(outputExpression)) {
          String localName = OutcomeExpressionConstants.ARTIFACTS + "." + outputExpression;
          String fqn = stageFqn + "." + localName;
          outputProperties.add(
              YamlProperties.newBuilder().setLocalName(localName).setFqn(fqn).setVisible(true).build());
        }
      }
    }
  }

  private List<YamlProperties> handleManifestProperties(YamlField specField, NGServiceConfig ngServiceConfig) {
    final String stageFqn = YamlUtils.getFullyQualifiedName(specField.getNode());
    List<YamlProperties> outputProperties = new ArrayList<>();
    if (ngServiceConfig != null && ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition() != null) {
      List<ManifestConfigWrapper> manifestConfigWrappers =
          ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getManifests();

      if (isNotEmpty(manifestConfigWrappers)) {
        for (ManifestConfigWrapper manifestWrapper : manifestConfigWrappers) {
          ManifestConfig manifestConfig = manifestWrapper.getManifest();
          String identifier = manifestConfig.getIdentifier();

          ManifestOutcome outcome = ManifestOutcomeMapper.toManifestOutcome(manifestConfig.getSpec(), 0);

          List<String> manifestOutputExpressions = VariableCreatorHelper.getExpressionsInObject(outcome, identifier);

          for (String outputExpression : manifestOutputExpressions) {
            String localFqn = OutcomeExpressionConstants.MANIFESTS + "." + outputExpression;
            String fqn = stageFqn + "." + localFqn;
            outputProperties.add(
                YamlProperties.newBuilder().setLocalName(localFqn).setFqn(fqn).setVisible(true).build());
          }
        }
      }
    }
    return outputProperties;
  }

  private List<YamlProperties> handleServiceVariables(
      YamlField specField, Set<String> existingServiceVariables, NGServiceConfig ngServiceConfig) {
    final String stageFqn = YamlUtils.getFullyQualifiedName(specField.getNode());
    List<YamlProperties> outputProperties = new ArrayList<>();
    if (ngServiceConfig != null) {
      List<NGVariable> ngVariableList =
          ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getVariables();
      if (isNotEmpty(ngVariableList)) {
        existingServiceVariables.addAll(ngVariableList.stream().map(NGVariable::getName).collect(Collectors.toSet()));
      }
    }
    if (isNotEmpty(existingServiceVariables)) {
      List<String> outputExpressions = existingServiceVariables.stream()
                                           .map(entry -> YAMLFieldNameConstants.SERVICE_VARIABLES + '.' + entry)
                                           .collect(Collectors.toList());

      for (String outputExpression : outputExpressions) {
        String fqn = stageFqn + "." + outputExpression;
        outputProperties.add(
            YamlProperties.newBuilder().setLocalName(outputExpression).setFqn(fqn).setVisible(true).build());
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
