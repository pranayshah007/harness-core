/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.barriers.service;

import io.harness.pms.barriers.beans.BarrierExecutionInfo;
import io.harness.steps.barriers.beans.BarrierSetupInfo;

import java.util.List;

public interface PMSBarrierService {
  List<BarrierSetupInfo> getBarrierSetupInfoList(String yaml);
  List<BarrierExecutionInfo> getBarrierExecutionInfoList(String stageSetupId, String planExecutionId);
  BarrierExecutionInfo getBarrierExecutionInfo(String barrierSetupId, String planExecutionId);
}
