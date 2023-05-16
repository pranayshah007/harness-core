/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.*;
import static io.harness.rule.OwnerRule.SAHIBA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventHandlers.ResourceGroupCRUDEventHandler;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
@Slf4j
@OwnedBy(PL)
public class ResourceGroupEntityCRUDStreamListenerTest extends CategoryTest {
  @Mock private ResourceGroupCRUDEventHandler resourceGroupCRUDEventHandler;
  @Mock private ResourceGroupService resourceGroupService;
  private String accountIdentifier = randomAlphabetic(10);
  @Inject private ResourceGroupEntityCRUDStreamListener resourceGroupEntityCRUDStreamListener;
  @Before
  public void setup() throws IOException {
    initMocks(this);
    resourceGroupCRUDEventHandler = mock(ResourceGroupCRUDEventHandler.class);
    resourceGroupService = mock(ResourceGroupService.class);
    resourceGroupEntityCRUDStreamListener = new ResourceGroupEntityCRUDStreamListener(resourceGroupCRUDEventHandler);
    when(resourceGroupCRUDEventHandler.deleteAssociatedResourceGroup(any(), any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = SAHIBA)
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
    assertTrue(resourceGroupEntityCRUDStreamListener.handleMessage(message));
    verify(resourceGroupCRUDEventHandler, times(1)).deleteAssociatedResourceGroup(idCaptor.capture(), any(), any());
    assertEquals(idCaptor.getValue(), accountId);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testOrganizationDeleteEvent() {
    String ordId = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of(accountIdentifier, accountIdentifier,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, ORGANIZATION_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.DELETE_ACTION))
                                          .setData(getOrganizationPayload(accountIdentifier, ordId))
                                          .build())
                          .build();
    final ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
    assertTrue(resourceGroupEntityCRUDStreamListener.handleMessage(message));
    verify(resourceGroupCRUDEventHandler, times(1)).deleteAssociatedResourceGroup(idCaptor.capture(), any(), any());
    assertEquals(idCaptor.getValue(), accountIdentifier);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testProjectDeleteEvent() {
    String ordId = randomAlphabetic(10);
    String projectId = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of(accountIdentifier, accountIdentifier,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, PROJECT_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.DELETE_ACTION))
                                          .setData(getProjectPayload(accountIdentifier, projectId, ordId))
                                          .build())
                          .build();
    final ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
    assertTrue(resourceGroupEntityCRUDStreamListener.handleMessage(message));
    verify(resourceGroupCRUDEventHandler, times(1)).deleteAssociatedResourceGroup(idCaptor.capture(), any(), any());
    log.info("accountIdentifier{} projectId{} ordId{} idCaptor.getValue(){}", accountIdentifier, projectId, ordId,
        idCaptor.getValue());
    assertEquals(idCaptor.getValue(), accountIdentifier);
  }

  private ByteString getAccountPayload(String identifier) {
    return AccountEntityChangeDTO.newBuilder().setAccountId(identifier).build().toByteString();
  }
  private ByteString getOrganizationPayload(String accountIdentifier, String identifier) {
    return OrganizationEntityChangeDTO.newBuilder()
        .setIdentifier(identifier)
        .setAccountIdentifier(accountIdentifier)
        .build()
        .toByteString();
  }
  private ByteString getProjectPayload(String accountIdentifier, String identifier, String orgId) {
    return ProjectEntityChangeDTO.newBuilder()
        .setIdentifier(identifier)
        .setAccountIdentifier(accountIdentifier)
        .setOrgIdentifier(orgId)
        .build()
        .toByteString();
  }
}
