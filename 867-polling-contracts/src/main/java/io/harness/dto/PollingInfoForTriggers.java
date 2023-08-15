/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dto;

import io.harness.pms.triggers.PerpetualTaskInfoForTriggers;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PollingInfoForTriggers {
  String perpetualTaskId;
  PerpetualTaskInfoForTriggers perpetualTaskInfoForTriggers;
  PolledResponse polledResponse;
}
