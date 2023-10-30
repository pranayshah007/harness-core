/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepCategory;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@FieldNameConstants(innerTypeName = "ExecutionSummaryUpdateInfoKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExecutionSummaryUpdateInfo {
  String stageUuid;
  StepCategory stepCategory;
  Map<String, LinkedHashMap<String, Object>> moduleInfo;
}
