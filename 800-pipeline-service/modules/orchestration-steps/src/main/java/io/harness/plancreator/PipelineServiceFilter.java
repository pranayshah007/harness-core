/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator;

import io.harness.pms.pipeline.filter.PipelineFilter;

import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PipelineServiceFilter implements PipelineFilter {
  public static final String APPROVAL = "approval";
  Set<String> stageTypes;
  int featureFlagStepCount;

  public void mergeStageTypes(Set<String> stageTypes) {
    if (stageTypes == null) {
      return;
    }
    if (this.stageTypes == null) {
      this.stageTypes = new HashSet<>();
    } else if (!(this.stageTypes instanceof HashSet)) {
      this.stageTypes = new HashSet<>(this.stageTypes);
    }

    this.stageTypes.addAll(stageTypes);
  }
}
