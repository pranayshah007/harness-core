/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.MOUNIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;
import io.harness.beans.CgEventRule;
import io.harness.beans.Event;
import io.harness.beans.Event.EventCreatorSource;
import io.harness.beans.EventDetail;
import io.harness.beans.EventPayload;
import io.harness.beans.EventStatus;
import io.harness.beans.EventType;
import io.harness.beans.WebHookEventConfig;
import io.harness.beans.WebHookEventDetail;
import io.harness.beans.event.TestEventPayload;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Slf4j
@OwnedBy(CDC)
public class EventServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  public static final String GLOBAL_APP_ID = "__GLOBAL_APP_ID__";
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  @Mock private HPersistence hPersistence;
  @InjectMocks private EventServiceImpl eventService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private EventConfigService eventConfigService;

  @Mock private EventHelper eventHelper;
  @Mock Query<Event> query;
  @Mock UpdateOperations<Event> updateOperations;

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void deliverEventNullValidations() {
    CgEventConfig cgEventConfig = eventConfigSetup();
    EventPayload eventPayload =
        EventPayload.builder().version("v1").eventType(EventType.PIPELINE_START.name()).data(null).build();
    eventService.deliverEvent(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig, eventPayload);
    Mockito.verify(hPersistence, atMost(0)).save(getEvent(eventPayload));
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void deliverEventDisabled() {
    CgEventConfig cgEventConfig = eventConfigSetup();
    EventPayload eventPayload =
        EventPayload.builder().version("v1").eventType(EventType.PIPELINE_START.name()).data(null).build();
    eventPayload.setData(new TestEventPayload());
    eventService.deliverEvent(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig, eventPayload);
    Mockito.verify(hPersistence, atMost(0)).save(getEvent(eventPayload));
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void deliverEventValidation1() {
    CgEventConfig cgEventConfig = eventConfigSetup();
    EventPayload eventPayload = EventPayload.builder()
                                    .version("v1")
                                    .eventType(EventType.PIPELINE_START.name())
                                    .data(new TestEventPayload())
                                    .build();
    when(eventHelper.canSendEvent(any(), any(), any())).thenReturn(true);
    cgEventConfig.setEnabled(true);
    eventService.deliverEvent(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, "uuid1", eventPayload);
    Mockito.verify(hPersistence).save(getEvent(eventPayload));
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void deliverEventValidation2() {
    CgEventConfig cgEventConfig = eventConfigSetup();
    EventPayload eventPayload = EventPayload.builder()
                                    .version("v1")
                                    .eventType(EventType.PIPELINE_START.name())
                                    .data(new TestEventPayload())
                                    .build();
    when(eventHelper.canSendEvent(any(), any(), any())).thenReturn(true);
    cgEventConfig.setEnabled(true);
    eventService.deliverEvent(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, cgEventConfig, eventPayload);
    Mockito.verify(hPersistence).save(getEvent(eventPayload));
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void deliverEventValidation3() {
    CgEventConfig cgEventConfig = eventConfigSetup();
    EventPayload eventPayload = EventPayload.builder()
                                    .version("v1")
                                    .eventType(EventType.PIPELINE_START.name())
                                    .data(new TestEventPayload())
                                    .build();
    when(eventHelper.canSendEvent(any(), any(), any())).thenReturn(true);
    cgEventConfig.setEnabled(true);
    eventService.deliverEvent(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, eventPayload);
    Mockito.verify(hPersistence).save(getEvent(eventPayload));
  }

  private CgEventConfig getEventConfigSample() {
    CgEventRule eventRule = new CgEventRule();
    eventRule.setType(CgEventRule.CgRuleType.ALL);
    CgEventConfig cgEventConfig = CgEventConfig.builder()
                                      .name("config1")
                                      .accountId(GLOBAL_ACCOUNT_ID)
                                      .appId(GLOBAL_APP_ID)
                                      .enabled(false)
                                      .rule(eventRule)
                                      .build();
    WebHookEventConfig config = new WebHookEventConfig();
    cgEventConfig.setConfig(config);
    cgEventConfig.getConfig().setUrl("url1");
    return cgEventConfig;
  }

  private CgEventConfig eventConfigSetup() {
    when(featureFlagService.isEnabled(any(), eq(GLOBAL_ACCOUNT_ID))).thenReturn(true);
    CgEventConfig cgEventConfig = getEventConfigSample();
    cgEventConfig.setUuid("uuid1");
    when(eventConfigService.getEventsConfig(any(), any(), any())).thenReturn(cgEventConfig);
    when(eventConfigService.listAllEventsConfig(any(), any())).thenReturn(Arrays.asList(cgEventConfig));
    return cgEventConfig;
  }

  private Event getEvent(EventPayload eventPayload) {
    return Event.builder()
        .eventConfigId("uuid1")
        .status(EventStatus.QUEUED)
        .maxRetryAllowed(1)
        .accountId(GLOBAL_ACCOUNT_ID)
        .appId(GLOBAL_APP_ID)
        .payload(eventPayload)
        .method("WEBHOOK")
        .source(EventCreatorSource.CD)
        .build();
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void sendTestEventAllValidations() {
    when(featureFlagService.isEnabled(any(), eq(GLOBAL_ACCOUNT_ID))).thenReturn(true);
    CgEventConfig cgEventConfig = getEventConfigSample();
    cgEventConfig.setUuid("uuid1");
    when(eventConfigService.getEventsConfig(any(), any(), any())).thenReturn(cgEventConfig);
    when(featureFlagService.isEnabled(any(), eq(GLOBAL_APP_ID))).thenReturn(true);
    eventService.sendTestEvent(GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID, "uuid1");
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    Mockito.verify(hPersistence).save(eventArgumentCaptor.capture());
    Event event = eventArgumentCaptor.getValue();
    assertThat(event.getEventConfigId()).isEqualTo("uuid1");
    assertThat(event.getStatus()).isEqualTo(EventStatus.QUEUED);
    EventPayload eventPayload = event.getPayload();
    assertThat(eventPayload.getVersion()).isEqualTo("v1");
    assertThat(eventPayload.getEventType()).isEqualTo(EventType.TEST.name());
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void updateEventStatus() {
    when(hPersistence.createUpdateOperations(eq(Event.class))).thenReturn(updateOperations);
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    when(hPersistence.createQuery(Event.class)).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    EventStatus status = EventStatus.QUEUED;
    EventDetail detail = new WebHookEventDetail();
    eventService.updateEventStatus("uuid1", status, detail);
    Mockito.verify(hPersistence).update(eq(query), eq(updateOperations));
  }
}
