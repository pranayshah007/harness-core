/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.beans;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptProvisionerOutput implements JexlContext {
  private StepOutput stepOutput;
  private String currentStageIdentifier;
  private String key;

  @Override
  public String toString() {
    return toString(key);
  }

  @Override
  public Object get(String s) {
    return toString(String.format("%s.%s", key, s));
  }

  @Override
  public void set(String s, Object o) {
    // Do nothing
  }

  @Override
  public boolean has(String s) {
    return true;
  }

  private String toString(String s) {
    if (StringUtils.equals(stepOutput.getStageIdentifier(), getCurrentStageIdentifier())) {
      return String.format("<+execution.steps.%s.steps.%s.output.%s>", stepOutput.getStepGroupIdentifier(),
          stepOutput.getStepIdentifier(), s);
    }

    return String.format("<+pipeline.stages.%s.spec.execution.steps.%s.steps.%s.output.%s>",
        stepOutput.getStageIdentifier(), stepOutput.getStepGroupIdentifier(), stepOutput.getStepIdentifier(), s);
  }
}
