/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.service;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.rule.OwnerRule.BOOPESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.rule.Owner;
import io.harness.telemetry.CdTelemetryEventListener;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;

@OwnedBy(GTM)
public class CdTelemetryEventListenerTest extends CategoryTest {
  private CdTelemetryPublisher cdTelemetryPublisher;
  @Inject @InjectMocks CdTelemetryEventListener cdTelemetryEventListener;

  @Before
  public void setup() {
    cdTelemetryPublisher = mock(CdTelemetryPublisher.class);
    cdTelemetryEventListener = spy(new CdTelemetryEventListener(cdTelemetryPublisher));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testAccountDeleteEvent() {
    String accountId = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", accountId,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, ACCOUNT_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.DELETE_ACTION))
                                          .setData(getAccountPayload(accountId))
                                          .build())
                          .build();
    final ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
    boolean result = cdTelemetryEventListener.handleMessage(message);
    verify(cdTelemetryPublisher, times(1)).deleteByAccount(idCaptor.capture());
    assertEquals(idCaptor.getValue(), accountId);
    assertTrue(result);
  }

  private ByteString getAccountPayload(String identifier) {
    return AccountEntityChangeDTO.newBuilder().setAccountId(identifier).build().toByteString();
  }
}
