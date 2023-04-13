/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filters;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
public class TestObjectWithSecretRef implements PMSStepInfo, WithSecretRef {
  ParameterField<String> credentialsRef;

  @Override
  public Map<String, ParameterField<String>> extractSecretRefs() {
    Map<String, ParameterField<String>> map = new HashMap<>();
    map.put(YAMLFieldNameConstants.CREDENTIALS_REF, credentialsRef);
    return map;
  }

  @Override
  public StepType getStepType() {
    return StepType.newBuilder().setType("Dummy").setStepCategory(StepCategory.STEP).build();
  }

  @Override
  public String getFacilitatorType() {
    return null;
  }
}
