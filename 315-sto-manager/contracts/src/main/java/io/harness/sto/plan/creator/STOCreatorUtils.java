/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.plan.creator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.sto.STOStepType;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.STO)
public class STOCreatorUtils {
  public Set<String> getSupportedSteps() {
    return Sets.newHashSet();
  }

  public Set<String> getSupportedStepsV2() {
    SortedSet<String> steps = new TreeSet<>();
    steps.add("liteEngineTask");
    steps.add("Plugin");
    steps.add("Security");
    steps.add("Run");
    steps.add("Background");

    steps.addAll(STOStepType.getSupportedSteps());

    return Sets.newTreeSet(steps);
  }
}
