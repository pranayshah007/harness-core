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
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class ChangeConsumerFactoryTest extends CategoryTest {
  @Mock DebeziumProducerFactory debeziumProducerFactory;
  @InjectMocks ChangeConsumerFactory changeConsumerFactory;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetConsumer() {
    String collectionName = "coll";
    EventsFrameworkChangeConsumerStreaming eventsFrameworkChangeConsumerStreaming =
        changeConsumerFactory.get(ChangeConsumerConfig.builder()
                                      .sleepInterval(10)
                                      .producingCountPerBatch(10)
                                      .redisStreamSize(10)
                                      .consumerType(ConsumerType.EVENTS_FRAMEWORK)
                                      .eventsFrameworkConfiguration(null)
                                      .build(),
            null, "coll");
    assertNotNull(eventsFrameworkChangeConsumerStreaming);
    assertThat(eventsFrameworkChangeConsumerStreaming).isInstanceOf(EventsFrameworkChangeConsumerStreaming.class);
    assertThatThrownBy(()
                           -> changeConsumerFactory.get(ChangeConsumerConfig.builder()
                                                            .sleepInterval(10)
                                                            .producingCountPerBatch(10)
                                                            .redisStreamSize(10)
                                                            .consumerType(null)
                                                            .eventsFrameworkConfiguration(null)
                                                            .build(),
                               null, "coll"))
        .isInstanceOf(InvalidRequestException.class);
  }
}
