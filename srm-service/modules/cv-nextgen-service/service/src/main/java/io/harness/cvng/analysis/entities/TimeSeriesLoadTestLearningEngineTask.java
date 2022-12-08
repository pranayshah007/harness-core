/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

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
public class TimeSeriesLoadTestLearningEngineTask extends LearningEngineTask {
  private String testDataUrl;
  private String controlDataUrl;
  private String metricTemplateUrl;

  private int dataLength;
  private int tolerance;
  private Long baselineStartTime;

  @Override
  public LearningEngineTaskType getType() {
    return LearningEngineTaskType.TIME_SERIES_LOAD_TEST;
  }
}
