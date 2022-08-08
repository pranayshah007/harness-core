/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.grpc;

import static io.harness.event.payloads.Lifecycle.EventType.EVENT_TYPE_START;
import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.event.payloads.EcsTaskLifecycle;
import io.harness.event.payloads.Lifecycle;
import io.harness.rule.Owner;

import com.google.protobuf.Any;
import com.google.protobuf.util.Timestamps;
import java.time.OffsetDateTime;
import java.util.Date;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PublishedMessageTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testPostLoad() {
    EcsTaskLifecycle ecsTaskLifecycle =
        EcsTaskLifecycle.newBuilder()
            .setLifecycle(Lifecycle.newBuilder()
                              .setInstanceId("instanceId-123")
                              .setType(EVENT_TYPE_START)
                              .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis())))
            .build();
    Any payload = Any.pack(ecsTaskLifecycle);
    String accountId = "accountId-123";
    Date validUntil = Date.from(OffsetDateTime.now().plusDays(14).toInstant());
    PublishedMessage publishedMessage = PublishedMessage.builder()
                                            .accountId(accountId)
                                            .data(payload.toByteArray())
                                            .type(ecsTaskLifecycle.getClass().getName())
                                            .validUntil(validUntil)
                                            .build();
    assertThat(publishedMessage.getMessage()).isEqualTo(ecsTaskLifecycle);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldSetValidUntil() {
    Date validUntil = Date.from(OffsetDateTime.now().plusDays(14).toInstant());
    PublishedMessage message = PublishedMessage.builder().validUntil(validUntil).build();
    assertThat(message.getValidUntil()).isNotNull().isAfterOrEqualTo(new Date());
  }
}
