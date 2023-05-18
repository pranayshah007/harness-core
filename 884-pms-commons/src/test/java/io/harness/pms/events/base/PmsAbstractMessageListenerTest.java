/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events.base;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.tools.reflect.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import io.harness.PmsCommonsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.monitoring.EventMonitoringService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.PmsEventFrameworkConstants;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsAbstractMessageListenerTest extends PmsCommonsTestBase {
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;
  @Mock private EventMonitoringService eventMonitoringService;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestIsProcessableForNull() {
    NoopPmsMessageListener noopListener = new NoopPmsMessageListener("RANDOM_SERVICE", new NoopPmsEventHandler());
    boolean processable = noopListener.isProcessable(Message.newBuilder().build());
    assertThat(processable).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestIsProcessableForService() {
    NoopPmsMessageListener noopListener = new NoopPmsMessageListener("RANDOM_SERVICE", new NoopPmsEventHandler());
    boolean processable = noopListener.isProcessable(
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(PmsEventFrameworkConstants.SERVICE_NAME, "RANDOM_SERVICE")
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build());
    assertThat(processable).isTrue();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestIsProcessableForDiffService() {
    NoopPmsMessageListener noopListener = new NoopPmsMessageListener("CD", new NoopPmsEventHandler());
    boolean processable = noopListener.isProcessable(
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(PmsEventFrameworkConstants.SERVICE_NAME, "RANDOM_SERVICE")
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build());
    assertThat(processable).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestExtractEntity() {
    NoopPmsMessageListener noopListener = new NoopPmsMessageListener("CD", new NoopPmsEventHandler());
    InterruptEvent event = noopListener.extractEntity(
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(PmsEventFrameworkConstants.SERVICE_NAME, "CD")
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build());
    assertThat(event).isNotNull();
    assertThat(event.getType()).isEqualTo(InterruptType.ABORT);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldTestHandleMessageWithoutAmbiance() {
    NoopPmsMessageListener noopListener = new NoopPmsMessageListener(
        "RANDOM_SERVICE", new NoopPmsEventHandler(), MoreExecutors.newDirectExecutorService());
    boolean handled = noopListener.handleMessage(
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(PmsEventFrameworkConstants.SERVICE_NAME, "RANDOM_SERVICE")
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build());
    assertThat(handled).isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldTestHandleMessageWithAmbiance() {
    NoopPmsEventHandler eventHandler = new NoopPmsEventHandler();
    on(eventHandler).set("pmsGitSyncHelper", pmsGitSyncHelper);
    on(eventHandler).set("eventMonitoringService", eventMonitoringService);
    when(pmsGitSyncHelper.createGitSyncBranchContextGuard(any(), anyBoolean()))
        .thenReturn(new PmsGitSyncBranchContextGuard(null, false));
    NoopPmsMessageListener noopListener =
        new NoopPmsMessageListener("RANDOM_SERVICE", eventHandler, MoreExecutors.newDirectExecutorService());
    boolean handled = noopListener.handleMessage(
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(PmsEventFrameworkConstants.SERVICE_NAME, "RANDOM_SERVICE")
                            .setData(InterruptEvent.newBuilder()
                                         .setAmbiance(buildAmbiance())
                                         .setType(InterruptType.ABORT)
                                         .build()
                                         .toByteString())
                            .build())
            .build());
    assertThat(handled).isTrue();
  }

  public static Ambiance buildAmbiance() {
    Level level = Level.newBuilder()
                      .setRuntimeId("rid")
                      .setSetupId("sid")
                      .setStepType(StepType.newBuilder().setType("RANDOM").setStepCategory(StepCategory.STEP).build())
                      .setGroup("g")
                      .build();
    List<Level> levels = new ArrayList<>();
    levels.add(level);
    return Ambiance.newBuilder()
        .setPlanExecutionId("peid")
        .putAllSetupAbstractions(ImmutableMap.of("accountId", "aid", "orgId", "oid", "projectId", "pid"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .build();
  }
}
