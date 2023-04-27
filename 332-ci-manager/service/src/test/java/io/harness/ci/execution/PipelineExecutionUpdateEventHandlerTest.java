/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.rule.OwnerRule.HARSH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.rule.Owner;

import io.fabric8.utils.Lists;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineExecutionUpdateEventHandlerTest extends CategoryTest {
  @Mock private GitBuildStatusUtility gitBuildStatusUtility;
  @InjectMocks private PipelineExecutionUpdateEventHandler pipelineExecutionUpdateEventHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testHandleEvent() {
    OrchestrationEvent orchestrationEvent =
        OrchestrationEvent.builder()
            .ambiance(Ambiance.newBuilder()
                          .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                              "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                          .addAllLevels(Lists.newArrayList(Level.newBuilder().setRuntimeId("node1").build()))
                          .build())
            .build();
    when(gitBuildStatusUtility.shouldSendStatus(any())).thenReturn(true);
    pipelineExecutionUpdateEventHandler.handleEvent(orchestrationEvent);

    verify(gitBuildStatusUtility).sendStatusToGit(any(), any(), any(), any());
  }
}
