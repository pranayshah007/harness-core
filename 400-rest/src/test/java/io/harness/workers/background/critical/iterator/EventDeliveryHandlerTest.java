/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.workers.background.critical.iterator;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.MOUNIK;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Event;
import io.harness.beans.EventPayload;
import io.harness.beans.EventStatus;
import io.harness.beans.EventType;
import io.harness.beans.event.TestEventPayload;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.EventDeliveryService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@Slf4j
@OwnedBy(CDC)
public class EventDeliveryHandlerTest extends WingsBaseTest {
  @InjectMocks @Inject private EventDeliveryHandler eventDeliveryHandler;
  @Mock private EventDeliveryService eventDeliveryService;
  public static final String GLOBAL_APP_ID = "__GLOBAL_APP_ID__";
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void handleEvent() {
    Event event =
        Event.builder().eventConfigId("evcid1").accountId(GLOBAL_ACCOUNT_ID).appId(GLOBAL_APP_ID).uuid("uuid1").build();
    eventDeliveryHandler.handle(event);
    EventPayload eventPayload =
        EventPayload.builder().version("v1").eventType(EventType.TEST.name()).data(new TestEventPayload()).build();
    Event event2 = Event.builder()
                       .eventConfigId("evcid1")
                       .accountId(GLOBAL_ACCOUNT_ID)
                       .appId(GLOBAL_APP_ID)
                       .uuid("uuid1")
                       .payload(eventPayload)
                       .status(EventStatus.SKIPPED)
                       .build();
    eventDeliveryHandler.handle(event2);
    Mockito.verify(eventDeliveryService).deliveryEvent(eq(event), any());
  }
}
