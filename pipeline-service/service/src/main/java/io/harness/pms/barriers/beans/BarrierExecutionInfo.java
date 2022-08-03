/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.barriers.beans;

import io.harness.steps.barriers.beans.StageDetail;

import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BarrierExecutionInfo {
  String name;
  String identifier;
  long startedAt;
  boolean started;
  long timeoutIn;
  Set<StageDetail> stages;
}
