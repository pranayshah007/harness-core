/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.observers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.NodeExecution;
import io.harness.logging.AutoLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeUpdateInfo {
  @NonNull NodeExecution nodeExecution;
  List<String> timeoutInstanceIds;
  @Builder.Default long updatedTs = System.currentTimeMillis();

  public String getNodeExecutionId() {
    return nodeExecution.getUuid();
  }

  public String getPlanExecutionId() {
    return nodeExecution.getAmbiance().getPlanExecutionId();
  }

  public Status getStatus() {
    return nodeExecution.getStatus();
  }

  public AutoLogContext autoLogContext() {
    Map<String, String> logContextMap = AmbianceUtils.logContextMap(nodeExecution.getAmbiance());
    logContextMap.put("observerDataType", this.getClass().getSimpleName());
    return new AutoLogContext(logContextMap, OverrideBehavior.OVERRIDE_NESTS);
  }
}
