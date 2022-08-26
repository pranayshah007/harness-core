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

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Properties;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class DebeziumControllerTest extends CategoryTest {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetLockName() {
    Properties props = new Properties();
    props.setProperty(DebeziumConfiguration.CONNECTOR_NAME, "conn1");
    EventsFrameworkChangeConsumer eventsFrameworkChangeConsumer =
        new EventsFrameworkChangeConsumer(60, "coll1", null, 1000, 1000);
    DebeziumController debeziumController =
        new DebeziumController(props, eventsFrameworkChangeConsumer, null, null, null);
    assertEquals(debeziumController.getLockName(),
        DEBEZIUM_LOCK_PREFIX + props.get(DebeziumConfiguration.CONNECTOR_NAME) + "-"
            + "coll1");
  }
}