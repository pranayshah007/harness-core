/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.common.v1;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.core.variables.v1.NGVariableV1Wrapper;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(CDC)
public class StageElementParametersV1 implements StepParameters {
  String uuid;
  String id;
  String name;
  ParameterField<String> desc;

  String when;
  List<FailureConfigV1> failure;
  NGVariableV1Wrapper variables;
  Map<String, String> labels;
  String type;
  SpecParameters spec;
  ParameterField<String> timeout;
  ParameterField<List<String>> delegate;

  @Override
  public List<String> excludeKeysFromStepInputs() {
    if (spec != null) {
      return spec.stepInputsKeyExclude();
    }
    return new LinkedList<>();
  }

  public StageElementParametersV1 cloneParameters() {
    return StageElementParametersV1.builder()
        .uuid(this.uuid)
        .type(this.type)
        .name(this.name)
        .desc(this.desc)
        .id(this.id)
        .failure(this.failure)
        .when(this.when)
        .variables(this.variables)
        .labels(this.labels)
        .delegate(this.delegate)
        .build();
  }
}
