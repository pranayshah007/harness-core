/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.interrupts.InterruptService;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class AllInterruptCallbackTest extends OrchestrationTestBase {
  @Mock InterruptService interruptService;

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestNotify() {
    String interruptId = generateUuid();
    AllInterruptCallback allInterruptCallbackTest = AllInterruptCallback.builder()
                                                        .interrupt(Interrupt.builder()
                                                                       .uuid(interruptId)
                                                                       .type(InterruptType.EXPIRE_ALL)
                                                                       .planExecutionId(generateUuid())
                                                                       .build())
                                                        .build();
    Reflect.on(allInterruptCallbackTest).set("interruptService", interruptService);
    allInterruptCallbackTest.notify(ImmutableMap.of());
    verify(interruptService).markProcessed(eq(interruptId), eq(PROCESSED_SUCCESSFULLY));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestNotifyError() {
    String interruptId = generateUuid();
    AllInterruptCallback allInterruptCallback = AllInterruptCallback.builder()
                                                    .interrupt(Interrupt.builder()
                                                                   .uuid(interruptId)
                                                                   .type(InterruptType.EXPIRE_ALL)
                                                                   .planExecutionId(generateUuid())
                                                                   .build())
                                                    .build();
    Reflect.on(allInterruptCallback).set("interruptService", interruptService);
    allInterruptCallback.notifyError(ImmutableMap.of());
    verify(interruptService).markProcessed(eq(interruptId), eq(PROCESSED_UNSUCCESSFULLY));
  }
}
