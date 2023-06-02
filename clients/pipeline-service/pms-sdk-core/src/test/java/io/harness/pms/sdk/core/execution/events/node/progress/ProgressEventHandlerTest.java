/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.node.progress;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.contracts.progress.ProgressEvent;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.ExecutableProcessor;
import io.harness.pms.sdk.core.execution.ExecutableProcessorFactory;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ProgressData;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class ProgressEventHandlerTest extends PmsSdkCoreTestBase {
  private static String NOTIFY_ID = "notifyId";

  @Mock ExecutableProcessorFactory executableProcessorFactory;
  @Mock KryoSerializer kryoSerializer;
  @Mock SdkNodeExecutionService sdkNodeExecutionService;

  @InjectMocks ProgressEventHandler progressEventHandler;

  @Inject private Injector injector;

  private ProgressEvent progressEvent;
  private Ambiance ambiance;

  @Before
  public void setup() {
    AdviserType adviserType = AdviserType.newBuilder().setType("Type1").build();
    ExecutableProcessor executableProcessor = mock(ExecutableProcessor.class);
    Mockito.when(executableProcessorFactory.obtainProcessor(ExecutionMode.CHILD)).thenReturn(executableProcessor);
    AdviserObtainment adviserObtainment = AdviserObtainment.newBuilder().setType(adviserType).build();
    ambiance = AmbianceTestUtils.buildAmbiance();
    progressEvent = ProgressEvent.newBuilder().setAmbiance(ambiance).setExecutionMode(ExecutionMode.CHILD).build();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testMetricPrefix() {
    assertThat(progressEventHandler.getMetricPrefix(progressEvent)).isEqualTo("progress_event");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractLogProperties() {
    Map<String, String> metricsMap = progressEventHandler.extraLogProperties(progressEvent);
    assertThat(metricsMap.isEmpty()).isFalse();
    assertThat(metricsMap.size()).isEqualTo(1);
    assertThat(metricsMap.get("eventType")).isEqualTo(NodeExecutionEventType.PROGRESS.name());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractAmbiance() {
    assertThat(progressEventHandler.extractAmbiance(progressEvent)).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventWithContextWithNullResponse() {
    Mockito.when(kryoSerializer.asInflatedObject(any())).thenReturn(DummyProgressData.builder().build());
    progressEventHandler.handleEventWithContext(progressEvent);
  }

  @Data
  @Builder
  private static class DummyProgressData implements ProgressData {
    DummyProgressData() {}
  }
}
