/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)

public class ChangeConsumerFactoryTest extends CategoryTest {
  ChangeConsumerFactory changeConsumerFactory = new ChangeConsumerFactory();
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetConsumer() {
    String collectionName = "coll";
    EventsFrameworkChangeConsumer eventsFrameworkChangeConsumer =
        changeConsumerFactory.get(collectionName, new ChangeConsumerConfig(ConsumerType.EVENTS_FRAMEWORK, null));
    assertNotNull(eventsFrameworkChangeConsumer);
    assertThat(eventsFrameworkChangeConsumer).isInstanceOf(EventsFrameworkChangeConsumer.class);
    assertThatThrownBy(() -> changeConsumerFactory.get(collectionName, new ChangeConsumerConfig(null, null)))
        .isInstanceOf(InvalidRequestException.class);
  }
}
