/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.user.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.user.beans.entity.UserEventEntity;
import io.harness.idp.user.repositories.UserEventRepository;

import com.google.protobuf.StringValue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class UserRefreshServiceImplTest {
  @Mock private UserEventRepository userEventRepository;

  @Mock private NamespaceService namespaceService;

  @InjectMocks private UserRefreshServiceImpl userRefreshService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testProcessEntityUpdate() {
    String accountIdentifier = "example-account";
    Message message = mock(Message.class);
    EntityChangeDTO entityChangeDTO =
        EntityChangeDTO.newBuilder().setAccountIdentifier(StringValue.of(accountIdentifier)).build();
    when(namespaceService.getAccountIdpStatus(accountIdentifier)).thenReturn(true);

    userRefreshService.processEntityUpdate(message, entityChangeDTO);

    verify(userEventRepository, times(1))
        .saveOrUpdate(UserEventEntity.builder().accountIdentifier(accountIdentifier).hasEvent(true).build());
  }
}
