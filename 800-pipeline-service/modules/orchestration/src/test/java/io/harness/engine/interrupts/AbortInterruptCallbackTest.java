/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.helpers.AbortHelper;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.rule.Owner;
import io.harness.waiter.StringNotifyResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class AbortInterruptCallbackTest extends OrchestrationTestBase {
  @Mock AbortHelper abortHelper;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock WaitNotifyEngine waitNotifyEngine;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestNotify() {
    String nodeExecutionId = generateUuid();
    String interruptId = generateUuid();
    AbortInterruptCallback abortCallback =
        AbortInterruptCallback.builder()
            .interruptId(interruptId)
            .interruptType(InterruptType.ABORT)
            .nodeExecutionId(nodeExecutionId)
            .interruptConfig(
                InterruptConfig.newBuilder()
                    .setIssuedBy(
                        IssuedBy.newBuilder()
                            .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("MANUAL").setType("").build())
                            .build())
                    .build())
            .build();
    Reflect.on(abortCallback).set("abortHelper", abortHelper);
    Reflect.on(abortCallback).set("nodeExecutionService", nodeExecutionService);
    Reflect.on(abortCallback).set("waitNotifyEngine", waitNotifyEngine);
    abortCallback.notify(ImmutableMap.of(generateUuid(), StringNotifyResponseData.builder().data("SOMEDATA").build()));

    ArgumentCaptor<String> nExIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> intIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> notifyIdCaptpr = ArgumentCaptor.forClass(String.class);

    verify(nodeExecutionService).get(nExIdCaptor.capture());
    verify(abortHelper).abortDiscontinuingNode(any(), intIdCaptor.capture(), any());
    verify(waitNotifyEngine).doneWith(notifyIdCaptpr.capture(), any());

    assertThat(nExIdCaptor.getValue()).isEqualTo(nodeExecutionId);
    assertThat(intIdCaptor.getValue()).isEqualTo(interruptId);
    assertThat(notifyIdCaptpr.getValue()).isEqualTo(nodeExecutionId + "|" + interruptId);
  }
}
