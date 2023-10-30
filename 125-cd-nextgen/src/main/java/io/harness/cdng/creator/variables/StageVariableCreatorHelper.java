/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.InfrastructureMapper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.data.structure.CollectionUtils;
import io.harness.encryption.Scope;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.persistence.HIterator;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlProperties;
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
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_PIPELINE})
public class StageVariableCreatorHelper {
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Inject private ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private InfrastructureMapper infrastructureMapper;

  public void createVariablesForEnvironment(VariableCreationContext ctx,
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
      Optional<Environment> optionalEnvironment = environmentService.get(
          accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(), false, true, false);
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
        if (isNotEmpty(envVariables) && serviceVariables != null) {
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

  private void addInfrastructureProperties(InfrastructureEntity infrastructureEntity, YamlField specField,
      LinkedHashMap<String, VariableCreationResponse> responseMap, String infraNodeUuid) {
    Map<String, YamlExtraProperties> yamlPropertiesMap = new LinkedHashMap<>();
    List<YamlProperties> outputProperties = new LinkedList<>();
    InfrastructureConfig infrastructureConfig =
        InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureEntity);
    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        infrastructureConfig.getInfrastructureDefinitionConfig().getSpec(), EnvironmentOutcome.builder().build(),
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
}
