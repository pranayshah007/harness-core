/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import java.util.Collections;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CanaryLogAnalysisLearningEngineTask extends LogAnalysisLearningEngineTask {
  private Set<String> controlHosts;

  public Set<String> getControlHosts() {
    if (controlHosts == null) {
      return Collections.emptySet();
    }
    return controlHosts;
  }
  @Override
  public LearningEngineTaskType getType() {
    return LearningEngineTaskType.CANARY_LOG_ANALYSIS;
  }
}
