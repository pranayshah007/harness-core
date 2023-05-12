/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.eventHandlers.ConnectorEntityCRUDEventHandler;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.event.ConnectorEntityCRUDStreamListener;
import io.harness.ng.core.event.HarnessSMManager;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.rule.Owner;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class ConnectorEntityCRUDStreamListenerTest extends CategoryTest {
  private HarnessSMManager harnessSMManager;
  private ConnectorEntityCRUDEventHandler connectorEntityCRUDEventHandler;
  private NGSettingsClient settingsClient;
  @Inject private ConnectorEntityCRUDStreamListener connectorEntityCRUDStreamListener;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  private String accountIdentifier = randomAlphabetic(10);

  @Before
  public void setup() throws IOException {
    initMocks(this);
    harnessSMManager = mock(HarnessSMManager.class);
    connectorEntityCRUDEventHandler = mock(ConnectorEntityCRUDEventHandler.class);
    settingsClient = mock(NGSettingsClient.class);
    connectorEntityCRUDStreamListener = spy(new ConnectorEntityCRUDStreamListener(
        harnessSMManager, connectorEntityCRUDEventHandler, settingsClient, featureFlagHelperService));
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testAccountDeleteEvent() {
    String accountId = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of(accountIdentifier, accountId,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, ACCOUNT_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.DELETE_ACTION))
                                          .setData(getAccountPayload(accountId))
                                          .build())
                          .build();
    final ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
    when(connectorEntityCRUDEventHandler.deleteAssociatedConnectors(any(), any(), any())).thenReturn(true);
    boolean result = connectorEntityCRUDStreamListener.handleMessage(message);
    verify(connectorEntityCRUDEventHandler, times(1)).deleteAssociatedConnectors(idCaptor.capture(), any(), any());
    assertEquals(idCaptor.getValue(), accountId);
    assertTrue(result);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testAccountChangeEvent_withoutAnyActionSetInMetadata() {
    String accountId = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of(accountIdentifier, accountId,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, ACCOUNT_ENTITY))
                                          .setData(getAccountPayload(accountId))
                                          .build())
                          .build();
    boolean result = connectorEntityCRUDStreamListener.handleMessage(message);
    verify(connectorEntityCRUDEventHandler, times(0)).deleteAssociatedConnectors(any(), any(), any());
    assertTrue(result);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testOrganizationDeleteEvent() {
    String identifier = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of(accountIdentifier, accountIdentifier,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, ORGANIZATION_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.DELETE_ACTION))
                                          .setData(getOrganizationPayload(accountIdentifier, identifier))
                                          .build())
                          .build();
    final ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
    when(connectorEntityCRUDEventHandler.deleteAssociatedConnectors(any(), any(), any())).thenReturn(true);
    connectorEntityCRUDStreamListener.handleMessage(message);
    verify(connectorEntityCRUDEventHandler, times(1)).deleteAssociatedConnectors(any(), idCaptor.capture(), any());
    assertEquals(idCaptor.getValue(), identifier);
  }

  private ByteString getOrganizationPayload(String accountIdentifier, String identifier) {
    return OrganizationEntityChangeDTO.newBuilder()
        .setIdentifier(identifier)
        .setAccountIdentifier(accountIdentifier)
        .build()
        .toByteString();
  }

  private ByteString getAccountPayload(String identifier) {
    return AccountEntityChangeDTO.newBuilder().setAccountId(identifier).build().toByteString();
  }
}
