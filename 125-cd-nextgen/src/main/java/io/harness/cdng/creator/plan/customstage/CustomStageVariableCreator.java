/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.customstage;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STRATEGY;

import io.harness.NGCommonEntityConstants;
import io.harness.cdng.creator.plan.stage.CustomStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.InfrastructureMapper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.data.structure.CollectionUtils;
import io.harness.encryption.Scope;
import io.harness.evaluators.ProvisionerExpressionEvaluator;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
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
import io.harness.yaml.core.variables.NGVariable;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomStageVariableCreator extends AbstractStageVariableCreator<CustomStageNode> {
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private InfrastructureMapper infrastructureMapper;
  @Inject private ServiceOverrideService serviceOverrideService;
  @Inject private ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Inject private ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();

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
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.STAGE, Collections.singleton("Custom"));
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, CustomStageNode config) {
    YamlField currentField = ctx.getCurrentField();

    LinkedHashMap<String, VariableCreationResponse> responseMap =
        createVariablesForChildrenNodesPipelineV2Yaml(ctx, config);

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
      VariableCreationContext ctx, CustomStageNode config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    try {
      final EnvironmentYamlV2 environment = config.getCustomStageConfig().getEnvironment();

      if (environment != null) {
        createVariablesForEnvironment(ctx, responseMap, environment);
      }
    } catch (Exception ex) {
      log.error("Exception during Custom Stage Node variable creation", ex);
    }
    return responseMap;
  }

  private void createVariablesForEnvironment(VariableCreationContext ctx,
      LinkedHashMap<String, VariableCreationResponse> responseMap, EnvironmentYamlV2 environmentYamlV2) {
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
      }
    } else {
      outputProperties.addAll(handleEnvironmentOutcome(specField, envVariables));
    }
    yamlPropertiesMap.put(
        environmentYamlV2.getUuid(), YamlExtraProperties.newBuilder().addAllOutputProperties(outputProperties).build());
    // TODO: Not Supporting infra provisioners currently
    // addProvisionerDependencyForSingleEnvironment(responseMap, specField);
    responseMap.put(
        environmentYamlV2.getUuid(), VariableCreationResponse.builder().yamlExtraProperties(yamlPropertiesMap).build());

    // Create variables for infrastructure definitions/infrastructure definition
    if (ParameterField.isNotNull(environmentYamlV2.getInfrastructureDefinitions())) {
      createVariablesForInfraDefinitions(ctx, specField, environmentRef, responseMap, environmentYamlV2);
    } else if (ParameterField.isNotNull(environmentYamlV2.getInfrastructureDefinition())) {
      createVariablesForInfraDefinition(ctx, specField, environmentRef, responseMap, environmentYamlV2);
    }
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

  private List<NGVariable> getInfraVarsList(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ParameterField<String> environmentRef, String infraId) {
    Map<Scope, NGServiceOverridesEntity> infraOverride = serviceOverridesServiceV2.getInfraOverride(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(), infraId, null);
    return getVariablesList(infraOverride);
  }

  private ParameterField<String> getEnvironmentRef(CustomStageNode stageNode) {
    EnvironmentYamlV2 environmentYamlV2 = stageNode.getCustomStageConfig().getEnvironment();
    if (environmentYamlV2 != null) {
      return environmentYamlV2.getEnvironmentRef();
    }
    return null;
  }

  private String getInfraIdentifier(CustomStageNode stageNode) {
    EnvironmentYamlV2 environmentYamlV2 = stageNode.getCustomStageConfig().getEnvironment();
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

  @Override
  public Class<CustomStageNode> getFieldClass() {
    return CustomStageNode.class;
  }
}
