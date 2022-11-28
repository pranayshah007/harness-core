/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import static io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType.LOG_CLUSTER;

import io.harness.cvng.analysis.beans.LogClusterLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LogClusterLearningEngineTask extends LearningEngineTask {
  private String testDataUrl;
  private String host;
  private LogClusterLevel clusterLevel;
  @Override
  public LearningEngineTaskType getType() {
    return LOG_CLUSTER;
  }
}
