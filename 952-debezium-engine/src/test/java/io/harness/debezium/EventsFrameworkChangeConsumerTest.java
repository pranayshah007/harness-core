/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.debezium.engine.ChangeEvent;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)

public class EventsFrameworkChangeConsumerTest extends CategoryTest {
  private static final String DEFAULT_STRING = "default";
  private static final String collection = "coll";
  private static final EventsFrameworkChangeConsumer eventsFrameworkChangeConsumer =
      new EventsFrameworkChangeConsumer(collection, null);
  private static final String key = "key";
  private static final String value = "value";
  private static final String destination = "destination";
  ChangeEvent<String, String> testRecord = new ChangeEvent<String, String>() {
    @Override
    public String key() {
      return key;
    }

    @Override
    public String value() {
      return value;
    }

    @Override
    public String destination() {
      return destination;
    }
  };
  ChangeEvent<String, String> emptyRecord = new ChangeEvent<String, String>() {
    @Override
    public String key() {
      return null;
    }

    @Override
    public String value() {
      return null;
    }

    @Override
    public String destination() {
      return null;
    }
  };

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetValueOrDefault() {
    assertEquals(DEFAULT_STRING, eventsFrameworkChangeConsumer.getValueOrDefault(emptyRecord));
    assertEquals(value, eventsFrameworkChangeConsumer.getValueOrDefault(testRecord));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetKeyOrDefault() {
    assertEquals(DEFAULT_STRING, eventsFrameworkChangeConsumer.getKeyOrDefault(emptyRecord));
    assertEquals(key, eventsFrameworkChangeConsumer.getKeyOrDefault(testRecord));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetCollection() {
    assertEquals(collection, eventsFrameworkChangeConsumer.getCollection());
  }
}
