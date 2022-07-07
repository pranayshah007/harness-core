/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.rule.Owner;

import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class DebeziumEngineModuleTest extends CategoryTest {
  @Mock EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetInstance() {
    assertThat(DebeziumEngineModule.getInstance(null)).isInstanceOf(DebeziumEngineModule.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetEventsFrameworkConfiguration() {
    assertNull(
        DebeziumEngineModule.getInstance(DebeziumEngineModuleConfig.builder().build()).eventsFrameworkConfiguration());
    assertThat(
        (new DebeziumEngineModule(
             DebeziumEngineModuleConfig.builder().eventsFrameworkConfiguration(eventsFrameworkConfiguration).build()))
            .eventsFrameworkConfiguration())
        .isInstanceOf(EventsFrameworkConfiguration.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetExecutorService() {
    assertThat(DebeziumEngineModule.getInstance(null).executorService()).isInstanceOf(ExecutorService.class);
  }
}