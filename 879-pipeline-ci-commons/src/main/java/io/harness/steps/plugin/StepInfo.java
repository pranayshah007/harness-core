/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.plugin;

import io.harness.plancreator.execution.ExecutionWrapperConfig;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@Value
@Builder
public class StepInfo {
  @Getter String stepUuid;
  String stepType;
  String moduleType;
  String stepIdentifier;
  ExecutionWrapperConfig executionWrapperConfig;
}
