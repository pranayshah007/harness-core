/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.common;

import io.harness.annotation.RecasterAlias;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("ngSectionStepParameters")
@RecasterAlias("io.harness.steps.common.NGSectionStepParameters")
public class NGSectionStepParameters implements StepParameters {
  String childNodeId;
  String logMessage;
}
