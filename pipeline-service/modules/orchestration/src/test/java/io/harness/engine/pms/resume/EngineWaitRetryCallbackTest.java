/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.resume;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.interrupts.InterruptManager;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.interrupts.AdviserIssuer;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.RetryInterruptConfig;
import io.harness.rule.Owner;

import java.util.HashMap;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class EngineWaitRetryCallbackTest extends OrchestrationTestBase {
  @Mock InterruptManager interruptManager;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testNotify() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    EngineWaitRetryCallback callback =
        EngineWaitRetryCallback.builder().planExecutionId(planExecutionId).nodeExecutionId(nodeExecutionId).build();

    Reflect.on(callback).set("interruptManager", interruptManager);
    callback.notify(new HashMap<>());
    verify(interruptManager)
        .register(
            eq(InterruptPackage.builder()
                    .planExecutionId(planExecutionId)
                    .nodeExecutionId(nodeExecutionId)
                    .interruptType(InterruptType.RETRY)
                    .interruptConfig(
                        InterruptConfig.newBuilder()
                            .setIssuedBy(IssuedBy.newBuilder()
                                             .setAdviserIssuer(
                                                 AdviserIssuer.newBuilder().setAdviserType(AdviseType.RETRY).build())
                                             .build())
                            .setRetryInterruptConfig(RetryInterruptConfig.newBuilder().build())
                            .build())
                    .build()));
  }
}
