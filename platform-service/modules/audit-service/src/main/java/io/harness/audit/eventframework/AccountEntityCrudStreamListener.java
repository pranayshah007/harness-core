/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.eventframework;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.impl.AuditServiceImpl;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class AccountEntityCrudStreamListener implements MessageListener {
  private final AuditServiceImpl auditServiceImpl;

  @Inject
  public AccountEntityCrudStreamListener(AuditServiceImpl auditServiceImpl) {
    this.auditServiceImpl = auditServiceImpl;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap.get(ENTITY_TYPE) != null && ACCOUNT_ENTITY.equals(metadataMap.get(ENTITY_TYPE))) {
        AccountEntityChangeDTO entityChangeDTO;
        try {
          entityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
        }
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processAccountEntityChangeEvent(entityChangeDTO, action);
        }
      }
    }
    return true;
  }

  private boolean processAccountEntityChangeEvent(AccountEntityChangeDTO entityChangeDTO, String action) {
    if (DELETE_ACTION.equals(action)) {
      processDeleteEvent(entityChangeDTO);
      log.info("Processed deleting Audit Events, Yaml Diff and Audit settings for account:"
          + entityChangeDTO.getAccountId());
    }
    return true;
  }

  private void processDeleteEvent(AccountEntityChangeDTO entityChangeDTO) {
    auditServiceImpl.deleteAuditInfo(entityChangeDTO.getAccountId());
  }
}
