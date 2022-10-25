/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.interrupts.InterruptManager;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.pms.advise.AdviserResponseHandler;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.interrupts.AdviserIssuer;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.serializer.ProtoUtils;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.PIPELINE)
public class IgnoreFailureAdviseHandler implements AdviserResponseHandler {
  @Inject private InterruptManager interruptManager;

  @Override
  public void handleAdvise(NodeExecution nodeExecution, AdviserResponse advise) {
    InterruptPackage interruptPackage =
        InterruptPackage.builder()
            .planExecutionId(nodeExecution.getAmbiance().getPlanExecutionId())
            .nodeExecutionId(nodeExecution.getUuid())
            .interruptType(InterruptType.IGNORE)
            .interruptConfig(
                InterruptConfig.newBuilder()
                    .setIssuedBy(IssuedBy.newBuilder()
                                     .setAdviserIssuer(
                                         AdviserIssuer.newBuilder().setAdviserType(AdviseType.IGNORE_FAILURE).build())
                                     .setIssueTime(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                                     .build())
                    .build())
            .build();
    interruptManager.register(interruptPackage);
  }
}
