/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.response;

import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TargetExecutionSummary {
  String triggerId;
  String targetId;
  String runtimeInput;
  String planExecutionId;
  @Nullable Integer runSequence;
  String executionStatus;
  Long startTs;
}
