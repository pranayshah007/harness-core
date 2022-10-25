/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.ChildChainExecutableResponse;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.SyncExecutableResponse;
import io.harness.pms.contracts.execution.TaskChainExecutableResponse;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.MDC;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptEventUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testObtainLogContext() {
    try (AutoLogContext ignored = InterruptEventUtils.obtainLogContext(
             InterruptEvent.newBuilder()
                 .setType(InterruptType.ABORT)
                 .setInterruptUuid("iid")
                 .setNotifyId("nid")
                 .setAmbiance(Ambiance.newBuilder().putSetupAbstractions("k", "v").build())
                 .build())) {
      assertThat(MDC.get("interruptType")).isEqualTo("ABORT");
      assertThat(MDC.get("interruptUuid")).isEqualTo("iid");
      assertThat(MDC.get("notifyId")).isEqualTo("nid");
      assertThat(MDC.get("k")).isEqualTo("v");
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testBuildInterruptEvent() {
    InterruptEvent.Builder builder = InterruptEvent.newBuilder();
    InterruptEvent event = InterruptEventUtils.buildInterruptEvent(builder,
        ExecutableResponse.newBuilder().setAsync(AsyncExecutableResponse.newBuilder().addUnits("u1").build()).build());
    assertThat(event.getAsync()).isNotNull();
    assertThat(event.getAsync().getUnitsList()).containsExactly("u1");

    builder = InterruptEvent.newBuilder();
    event = InterruptEventUtils.buildInterruptEvent(builder,
        ExecutableResponse.newBuilder().setTask(TaskExecutableResponse.newBuilder().setTaskId("tid1").build()).build());
    assertThat(event.getTask()).isNotNull();
    assertThat(event.getTask().getTaskId()).isEqualTo("tid1");

    builder = InterruptEvent.newBuilder();
    event = InterruptEventUtils.buildInterruptEvent(builder,
        ExecutableResponse.newBuilder()
            .setTaskChain(TaskChainExecutableResponse.newBuilder().setTaskId("tid2").build())
            .build());
    assertThat(event.getTaskChain()).isNotNull();
    assertThat(event.getTaskChain().getTaskId()).isEqualTo("tid2");

    assertThatThrownBy(
        ()
            -> InterruptEventUtils.buildInterruptEvent(InterruptEvent.newBuilder(),
                ExecutableResponse.newBuilder().setChild(ChildExecutableResponse.newBuilder().build()).build()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(()
                           -> InterruptEventUtils.buildInterruptEvent(InterruptEvent.newBuilder(),
                               ExecutableResponse.newBuilder()
                                   .setChildChain(ChildChainExecutableResponse.newBuilder().build())
                                   .build()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
        ()
            -> InterruptEventUtils.buildInterruptEvent(InterruptEvent.newBuilder(),
                ExecutableResponse.newBuilder().setChildren(ChildrenExecutableResponse.newBuilder().build()).build()))
        .isInstanceOf(IllegalStateException.class);

    assertThatCode(()
                       -> InterruptEventUtils.buildInterruptEvent(
                           InterruptEvent.newBuilder(), ExecutableResponse.newBuilder().build()))
        .doesNotThrowAnyException();
    assertThatCode(
        ()
            -> InterruptEventUtils.buildInterruptEvent(InterruptEvent.newBuilder(),
                ExecutableResponse.newBuilder().setSync(SyncExecutableResponse.newBuilder().build()).build()))
        .doesNotThrowAnyException();
  }
}
