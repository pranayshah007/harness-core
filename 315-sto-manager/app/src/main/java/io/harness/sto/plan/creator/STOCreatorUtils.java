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
import java.util.Arrays;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.STO)
public class STOCreatorUtils {
  public Set<String> getSupportedSteps() {
    // These are internal steps does not need to be in V2
    return Sets.newHashSet("Test", "SaveCache", "liteEngineTask", "Cleanup", "PublishArtifacts");
  }

  public Set<String> getSupportedStepsV2() {
    SortedSet<String> steps = new TreeSet<>();
    steps.add("Security");
    steps.add("Run");

    steps.addAll(Arrays.stream(STOStepType.values()).map(e -> e.getName()).collect(Collectors.toSet()));

    return Sets.newTreeSet(steps);
  }
}
