/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.customstage;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.cdng.creator.plan.stage.CustomStageConfig;
import io.harness.cdng.creator.plan.stage.CustomStageNode;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.filters.GenericStageFilterJsonCreatorV2;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.cdng.sample.cd.creator.filters.CdFilter;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CustomStageFilterCreator extends GenericStageFilterJsonCreatorV2<CustomStageNode> {
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureEntityService infraService;
  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton("Custom");
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, CustomStageNode customStageNode) {
    CdFilter.CdFilterBuilder filterBuilder = CdFilter.builder();
    final CustomStageConfig customStageConfig = customStageNode.getCustomStageConfig();
    addInfraFilters(filterCreationContext, filterBuilder, customStageConfig);
    return filterBuilder.build();
  }

  private void addInfraFilters(FilterCreationContext filterCreationContext, CdFilter.CdFilterBuilder filterBuilder,
      CustomStageConfig customStageConfig) {
    if (customStageConfig.getEnvironment() != null) {
      addFiltersFromEnvironment(filterCreationContext, filterBuilder, customStageConfig.getEnvironment());
    }
  }

  private void addFiltersFromEnvironment(
      FilterCreationContext filterCreationContext, CdFilter.CdFilterBuilder filterBuilder, EnvironmentYamlV2 env) {
    final ParameterField<String> environmentRef = env.getEnvironmentRef();
    if (ParameterField.isNull(environmentRef)) {
      throw new InvalidYamlRuntimeException(
          format("environmentRef should be present in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (ParameterField.isNotNull(env.getFilters()) && !env.getFilters().isExpression()
        && isNotEmpty(env.getFilters().getValue())) {
      Set<Entity> unsupportedEntities = env.getFilters()
                                            .getValue()
                                            .stream()
                                            .map(FilterYaml::getEntities)
                                            .flatMap(Set::stream)
                                            .filter(e -> Entity.gitOpsClusters != e && Entity.infrastructures != e)
                                            .collect(Collectors.toSet());
      if (!unsupportedEntities.isEmpty()) {
        throw new InvalidYamlRuntimeException(
            format("Environment filters can only support [%s]. Please add the correct filters in stage [%s]",
                HarnessStringUtils.join(",", Entity.infrastructures.name(), Entity.gitOpsClusters.name()),
                YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
      }
    }

    if (!environmentRef.isExpression()) {
      Optional<Environment> environmentEntityOptional = environmentService.get(
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId(), environmentRef.getValue(), false);
      environmentEntityOptional.ifPresent(environment -> {
        filterBuilder.environmentName(environment.getName());
        final List<InfraStructureDefinitionYaml> infraList = getInfraStructureDefinitionYamlsList(env);
        addFiltersForInfraYamlList(filterCreationContext, filterBuilder, environment, infraList);
      });
    }
  }

  private void addFiltersForInfraYamlList(FilterCreationContext filterCreationContext,
      CdFilter.CdFilterBuilder filterBuilder, Environment entity, List<InfraStructureDefinitionYaml> infraList) {
    if (isEmpty(infraList)) {
      return;
    }
    List<InfrastructureEntity> infrastructureEntities = infraService.getAllInfrastructureFromIdentifierList(
        filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
        filterCreationContext.getSetupMetadata().getProjectId(), entity.getIdentifier(),
        infraList.stream()
            .map(InfraStructureDefinitionYaml::getIdentifier)
            .filter(field -> !field.isExpression())
            .map(ParameterField::getValue)
            .collect(Collectors.toList()));
    for (InfrastructureEntity infrastructureEntity : infrastructureEntities) {
      if (infrastructureEntity.getType() == null) {
        throw new InvalidRequestException(format(
            "Infrastructure Definition [%s] in environment [%s] does not have an associated type. Please select a type for the infrastructure and try again",
            infrastructureEntity.getIdentifier(), infrastructureEntity.getEnvIdentifier()));
      }
      filterBuilder.infrastructureType(infrastructureEntity.getType().getDisplayName());
    }
  }

  private List<InfraStructureDefinitionYaml> getInfraStructureDefinitionYamlsList(EnvironmentYamlV2 env) {
    List<InfraStructureDefinitionYaml> infraList = new ArrayList<>();
    if (ParameterField.isNotNull(env.getInfrastructureDefinitions())) {
      if (!env.getInfrastructureDefinitions().isExpression()) {
        infraList.addAll(env.getInfrastructureDefinitions().getValue());
      }
    } else if (ParameterField.isNotNull(env.getInfrastructureDefinition())) {
      if (!env.getInfrastructureDefinition().isExpression()) {
        infraList.add(env.getInfrastructureDefinition().getValue());
      }
    }
    return infraList;
  }

  @Override
  public Class<CustomStageNode> getFieldClass() {
    return CustomStageNode.class;
  }
}
