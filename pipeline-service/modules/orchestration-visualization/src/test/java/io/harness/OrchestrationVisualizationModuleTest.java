/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.rule.Owner;
import io.harness.threading.ThreadPoolConfig;

import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationVisualizationModuleTest extends OrchestrationVisualizationTestBase {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetInstance() {
    assertThat(OrchestrationVisualizationModule.getInstance(
                   EventsFrameworkConfiguration.builder().build(), ThreadPoolConfig.builder().build(), 200))
        .isInstanceOf(OrchestrationVisualizationModule.class);
  }
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testOrchestrationVisualizationExecutorService() {
    OrchestrationVisualizationModule orchestrationVisualizationModule = OrchestrationVisualizationModule.getInstance(
        EventsFrameworkConfiguration.builder().build(), ThreadPoolConfig.builder().build(), 200);
    assertThat(orchestrationVisualizationModule.orchestrationVisualizationExecutorService())
        .isInstanceOf(ExecutorService.class);
  }
}
