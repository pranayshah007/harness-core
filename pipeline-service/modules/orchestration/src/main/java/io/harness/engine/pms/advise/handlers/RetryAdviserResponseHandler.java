/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.interrupts.InterruptManager;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.pms.advise.AdviserResponseHandler;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.RetryAdvise;
import io.harness.pms.contracts.interrupts.AdviserIssuer;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.RetryInterruptConfig;
import io.harness.serializer.ProtoUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Slf4j
public class RetryAdviserResponseHandler implements AdviserResponseHandler {
  @Inject private InterruptManager interruptManager;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName;

  @Override
  public void handleAdvise(NodeExecution nodeExecution, AdviserResponse adviserResponse) {
    RetryAdvise advise = adviserResponse.getRetryAdvise();
    InterruptPackage interruptPackage =
        InterruptPackage.builder()
            .nodeExecutionId(nodeExecution.getUuid())
            .planExecutionId(nodeExecution.getAmbiance().getPlanExecutionId())
            .interruptType(InterruptType.RETRY)
            .interruptConfig(
                InterruptConfig.newBuilder()
                    .setIssuedBy(
                        IssuedBy.newBuilder()
                            .setAdviserIssuer(AdviserIssuer.newBuilder().setAdviserType(AdviseType.RETRY).build())
                            .setIssueTime(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                            .build())
                    .setRetryInterruptConfig(
                        RetryInterruptConfig.newBuilder().setWaitInterval(advise.getWaitInterval()).build())
                    .build())
            .build();
    interruptManager.register(interruptPackage);
  }
}
