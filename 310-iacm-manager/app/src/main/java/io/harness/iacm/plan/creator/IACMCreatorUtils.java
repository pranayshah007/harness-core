/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.plan.creator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iacm.IACMStepType;

import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IACM)
public class IACMCreatorUtils {
  Set<String> supportedSteps =
      Sets.newHashSet("Plugin", "Action", "Run", "IACMTerraformPlan", "IACMTemplate", "liteEngineTask");
  Set<String> supportedV1Filters = Sets.newHashSet("plugin", "test", "background", "action", "script");
  public Set<String> getSupportedSteps() {
    return supportedSteps;
  }
  public Set<String> getSupportedStepsV2() {
    SortedSet<String> steps = new TreeSet<>();
    steps.addAll(supportedSteps);
    steps.addAll(Arrays.stream(IACMStepType.values()).map(IACMStepType::getName).collect(Collectors.toSet()));
    return Sets.newTreeSet(steps);
  }

  public Set<String> getSupportedStepsV3() {
    SortedSet<String> steps = new TreeSet<>();
    steps.addAll(supportedV1Filters);
    return steps;
  }
}