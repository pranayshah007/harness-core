/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public abstract class LogAnalysisLearningEngineTask extends LearningEngineTask {
  private String controlDataUrl;
  private String testDataUrl;
  private String previousAnalysisUrl;
  Set<String> controlHosts;
  Set<String> testHosts;
}
