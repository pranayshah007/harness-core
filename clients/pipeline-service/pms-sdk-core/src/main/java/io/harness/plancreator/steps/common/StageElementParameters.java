/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.common;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.when.beans.StageWhenCondition;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("stageElementParameters")
@OwnedBy(CDC)
@RecasterAlias("io.harness.plancreator.steps.common.StageElementParameters")
public class StageElementParameters implements StepParameters {
  String uuid;
  String identifier;
  String name;
  ParameterField<String> description;

  ParameterField<String> skipCondition;
  StageWhenCondition when;
  List<FailureStrategyConfig> failureStrategies;
  @SkipAutoEvaluation ParameterField<Map<String, Object>> variables;
  Map<String, String> tags;
  String type;
  SpecParameters specConfig;
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @Override
  public String toViewJson() {
    StageElementParameters stageElementParameters = cloneParameters();
    stageElementParameters.setSpecConfig(specConfig.getViewJsonObject());
    return RecastOrchestrationUtils.toJson(stageElementParameters);
  }

  public StageElementParameters cloneParameters() {
    return StageElementParameters.builder()
        .uuid(this.uuid)
        .type(this.type)
        .name(this.name)
        .description(this.description)
        .identifier(this.identifier)
        .failureStrategies(this.failureStrategies)
        .when(this.when)
        .skipCondition(this.skipCondition)
        .variables(this.variables)
        .tags(this.tags)
        .delegateSelectors(this.delegateSelectors)
        .build();
  }
}
