/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.node.advise;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviseEvent;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.registries.AdviserRegistry;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeAdviseEventHandlerTest extends PmsSdkCoreTestBase {
  private static String NOTIFY_ID = "notifyId";

  @Mock AdviserRegistry adviserRegistry;
  @Mock SdkNodeExecutionService sdkNodeExecutionService;

  @InjectMocks NodeAdviseEventHandler nodeAdviseEventHandler;

  @Inject private Injector injector;

  private AdviseEvent adviseEvent;
  private Ambiance ambiance;

  @Before
  public void setup() {
    AdviserType adviserType = AdviserType.newBuilder().setType("Type1").build();
    Mockito.when(adviserRegistry.obtain(adviserType)).thenReturn(new Type1Adviser());
    AdviserObtainment adviserObtainment = AdviserObtainment.newBuilder().setType(adviserType).build();
    ambiance = AmbianceTestUtils.buildAmbiance();
    adviseEvent =
        AdviseEvent.newBuilder()
            .setAmbiance(ambiance)
            .setFailureInfo(
                FailureInfo.newBuilder().addFailureData(FailureData.newBuilder().setCode("code").build()).build())
            .setIsPreviousAdviserExpired(true)
            .setToStatus(Status.ABORTED)
            .addAdviserObtainments(adviserObtainment)
            .setNotifyId(NOTIFY_ID)
            .build();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testMetricPrefix() {
    assertThat(nodeAdviseEventHandler.getMetricPrefix(adviseEvent)).isEqualTo("advise_event");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractLogProperties() {
    Map<String, String> metricsMap = nodeAdviseEventHandler.extraLogProperties(adviseEvent);
    assertThat(metricsMap.isEmpty()).isFalse();
    assertThat(metricsMap.size()).isEqualTo(2);
    assertThat(metricsMap.get("eventType")).isEqualTo(NodeExecutionEventType.ADVISE.name());
    assertThat(metricsMap.get("notifyId")).isEqualTo(NOTIFY_ID);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractAmbiance() {
    assertThat(nodeAdviseEventHandler.extractAmbiance(adviseEvent)).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventWithContextWithNullResponse() {
    nodeAdviseEventHandler.handleEventWithContext(adviseEvent);
    Mockito.verify(sdkNodeExecutionService)
        .handleAdviserResponse(ambiance, NOTIFY_ID, AdviserResponse.newBuilder().setType(AdviseType.UNKNOWN).build());
  }

  private class Type1Adviser implements Adviser {
    Type1Adviser() {}

    @Override
    public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
      return null;
    }

    @Override
    public boolean canAdvise(AdvisingEvent advisingEvent) {
      return true;
    }
  }
}
