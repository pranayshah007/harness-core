/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.eventframework;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.dao.DataDeletionRecordDao;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStep;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStepRecord;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CE)
@Slf4j
@Singleton
public class AccountEntityCRUDStreamListener implements MessageListener {
  @Inject EntityChangeHandler entityChangeHandler;
  @Inject DataDeletionRecordDao dataDeletionRecordDao;

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
          if (DELETE_ACTION.equals(action)) {
            dataDeletionRecordDao.save(getDataDeletionRecord(entityChangeDTO.getAccountId()));
            log.info("Created Data Deletion Record for account: {}", entityChangeDTO.getAccountId());
          }
          return true;
        }
      }
    }
    return true;
  }

  private DataDeletionRecord getDataDeletionRecord(String accountId) {
    Map<String, DataDeletionStepRecord> emptyRecords = new HashMap<>();
    for (DataDeletionStep dataDeletionStep : DataDeletionStep.values()) {
      emptyRecords.put(dataDeletionStep.name(),
          DataDeletionStepRecord.builder().dataDeletionBucket(dataDeletionStep.getBucket()).build());
    }
    return DataDeletionRecord.builder().accountId(accountId).dryRun(false).records(emptyRecords).build();
  }
}
