/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP_V2;

import io.harness.pms.plan.creation.PlanCreatorUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

// todo(abhinav): delete later
public class StepGroupPmsFilterJsonCreatorV2 extends StepGroupPmsFilterJsonCreator {
  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP_GROUP_V2, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }
}
