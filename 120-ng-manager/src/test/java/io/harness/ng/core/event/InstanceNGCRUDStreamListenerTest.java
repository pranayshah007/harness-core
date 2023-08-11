/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.rule.OwnerRule.BUHA;

import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(CDP)
public class InstanceNGCRUDStreamListenerTest extends CategoryTest {
  private InstanceService instanceService;
  @Inject @InjectMocks InstanceNGCRUDStreamListener instanceNGCRUDStreamListener;

  @Before
  public void setup() {
    instanceService = mock(InstanceService.class);
    instanceNGCRUDStreamListener = spy(new InstanceNGCRUDStreamListener(instanceService));
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testProjectDeleteEvent() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, PROJECT_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.DELETE_ACTION))
                                          .setData(getProjectPayload(accountIdentifier, orgIdentifier, identifier))
                                          .build())
                          .build();
    when(instanceService.deleteForProject(accountIdentifier, orgIdentifier, identifier)).thenReturn(true);
    boolean result = instanceNGCRUDStreamListener.handleMessage(message);
    verify(instanceService).deleteForProject(accountIdentifier, orgIdentifier, identifier);
    assertTrue(result);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testProjectChangeEvenWithoutAnyActionSetInMetadata() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, PROJECT_ENTITY))
                                          .setData(getProjectPayload(accountIdentifier, orgIdentifier, identifier))
                                          .build())
                          .build();
    boolean result = instanceNGCRUDStreamListener.handleMessage(message);
    verifyNoInteractions(instanceService);
    assertTrue(result);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testAccountChangeEvenShouldBeSkipped() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, ACCOUNT_ENTITY))
                                          .setData(getProjectPayload(accountIdentifier, orgIdentifier, identifier))
                                          .build())
                          .build();
    boolean result = instanceNGCRUDStreamListener.handleMessage(message);
    verifyNoInteractions(instanceService);
    assertTrue(result);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testProjectChangeEventUpdateShouldBeSkipped() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                                              EventsFrameworkMetadataConstants.ENTITY_TYPE, PROJECT_ENTITY,
                                              EventsFrameworkMetadataConstants.ACTION,
                                              EventsFrameworkMetadataConstants.UPDATE_ACTION))
                                          .setData(getProjectPayload(accountIdentifier, orgIdentifier, identifier))
                                          .build())
                          .build();
    boolean result = instanceNGCRUDStreamListener.handleMessage(message);
    verifyNoInteractions(instanceService);
    assertTrue(result);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testWithoutAnyEntitySetInMetadata() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .setData(getProjectPayload(accountIdentifier, orgIdentifier, identifier))
                                          .build())
                          .build();
    boolean result = instanceNGCRUDStreamListener.handleMessage(message);
    assertTrue(result);
  }

  private ByteString getProjectPayload(String accountIdentifier, String orgIdentifier, String identifier) {
    return ProjectEntityChangeDTO.newBuilder()
        .setIdentifier(identifier)
        .setOrgIdentifier(orgIdentifier)
        .setAccountIdentifier(accountIdentifier)
        .build()
        .toByteString();
  }
}
