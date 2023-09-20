/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.filters;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CUSTOM;

import io.harness.cdng.creator.plan.stage.CustomStageConfig;
import io.harness.cdng.creator.plan.stage.CustomStageNode;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.filters.GenericStageFilterJsonCreatorV2;
import io.harness.pms.cdng.sample.cd.creator.filters.CdFilter;
import io.harness.pms.cdng.sample.cd.creator.filters.CdFilter.CdFilterBuilder;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class CustomStageFilterCreator extends GenericStageFilterJsonCreatorV2<CustomStageNode> {
  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton(CUSTOM);
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, CustomStageNode customStageNode) {
    CdFilterBuilder filterBuilder = CdFilter.builder();
    final CustomStageConfig customStageConfig = customStageNode.getCustomStageConfig();
    addEnvAndInfraFilters(filterCreationContext, filterBuilder, customStageConfig);
    return filterBuilder.build();
  }

  private void addEnvAndInfraFilters(
      FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder, CustomStageConfig customStageConfig) {
    if (customStageConfig.getEnvironment() != null) {
      EnvironmentYamlV2 env = customStageConfig.getEnvironment();
      final ParameterField<String> environmentRef = env.getEnvironmentRef();
      if (ParameterField.isNull(environmentRef)) {
        throw new InvalidYamlRuntimeException(
            String.format("environmentRef should be present in stage [%s]. Please add it and try again",
                YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
      }

      if (ParameterField.isNotNull(env.getFilters()) && !env.getFilters().isExpression()
          && isNotEmpty(env.getFilters().getValue())) {
        Set<Entity> unsupportedEntities = env.getFilters()
                                              .getValue()
                                              .stream()
                                              .map(FilterYaml::getEntities)
                                              .flatMap(Set::stream)
                                              .filter(e -> Entity.infrastructures != e)
                                              .collect(Collectors.toSet());
        if (!unsupportedEntities.isEmpty()) {
          throw new InvalidYamlRuntimeException(
              String.format("Environment filters can only support [%s]. Please add the correct filters in stage [%s]",
                  HarnessStringUtils.join(",", Entity.infrastructures.name()),
                  YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
        }
      }
    }
  }

  @Override
  public Class<CustomStageNode> getFieldClass() {
    return CustomStageNode.class;
  }
}
