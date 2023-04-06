/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.variables;

import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
// todo(abhinav): delete later
public class StepGroupVariableCreatorV2 extends StepGroupVariableCreator {
  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STEP_GROUP_V2, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }
}
