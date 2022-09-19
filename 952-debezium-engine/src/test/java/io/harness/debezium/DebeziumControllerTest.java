/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.debezium.DebeziumConstants.DEBEZIUM_LOCK_PREFIX;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cf.client.api.CfClient;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.rule.Owner;

import io.debezium.engine.DebeziumEngine;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class DebeziumControllerTest extends CategoryTest {
  @Mock AcquiredLock acquiredLock;
  @Mock PersistentLocker persistentLocker;
  @Mock ExecutorService executorService;
  @Mock CfClient cfClient;
  Properties props = new Properties();
  EventsFrameworkChangeConsumer eventsFrameworkChangeConsumer =
      new EventsFrameworkChangeConsumer(60, "coll1", null, 1000, 1000);
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetLockName() {
    props.setProperty(DebeziumConfiguration.CONNECTOR_NAME, "conn1");
    DebeziumController debeziumController =
        new DebeziumController(props, eventsFrameworkChangeConsumer, persistentLocker, executorService, cfClient);
    assertEquals(debeziumController.getLockName(),
        DEBEZIUM_LOCK_PREFIX + props.get(DebeziumConfiguration.CONNECTOR_NAME) + "-"
            + "coll1");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetEngine() {
    DebeziumController debeziumController =
        new DebeziumController(props, eventsFrameworkChangeConsumer, persistentLocker, executorService, cfClient);
    assertThat(debeziumController.getEngine(props)).isInstanceOf(DebeziumEngine.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testAcquireLock() throws InterruptedException {
    DebeziumController debeziumController =
        new DebeziumController(props, eventsFrameworkChangeConsumer, persistentLocker, executorService, cfClient);
    doReturn(acquiredLock).when(persistentLocker).tryToAcquireInfiniteLockWithPeriodicRefresh(any(), any());
    assertThat(debeziumController.acquireLock(false)).isInstanceOf(AcquiredLock.class);
  }
}