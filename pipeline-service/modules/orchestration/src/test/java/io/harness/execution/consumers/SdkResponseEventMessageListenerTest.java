/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.consumers;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.execution.SdkResponseHandler;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class SdkResponseEventMessageListenerTest {
  private SdkResponseHandler eventHandler;

  @Before
  public void setup() {
    eventHandler = new SdkResponseHandler();
  }
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testSdkResponseEventMessageListener() {
    SdkResponseEventMessageListener messageListener =
        Mockito.spy(new SdkResponseEventMessageListener("RANDOM_SERVICE", eventHandler));

    Boolean listenerProcessable = messageListener.isProcessable(Message.newBuilder().build());
    assertThat(listenerProcessable).isEqualTo(true);
  }
}
