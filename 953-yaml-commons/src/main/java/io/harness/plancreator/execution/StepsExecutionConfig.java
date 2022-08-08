/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.execution;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@TypeAlias("stepsExecutionConfig")
@RecasterAlias("io.harness.plancreator.execution.StepsExecutionConfig")
public class StepsExecutionConfig {
  List<ExecutionWrapperConfig> steps;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public StepsExecutionConfig(List<ExecutionWrapperConfig> steps) {
    this.steps = steps;
  }
}