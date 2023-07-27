/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.DeploymentAccounts;
import io.harness.entities.DeploymentAccounts.DeploymentAccountsKeys;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(CDP)
@Slf4j
@Singleton
public class DeploymentAccountsCRUDStreamListener implements MessageListener {
  private final MongoTemplate mongoTemplate;
  @Inject
  public DeploymentAccountsCRUDStreamListener(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null
          && ACCOUNT_ENTITY.equals(metadataMap.get(ENTITY_TYPE))) {
        AccountEntityChangeDTO entityChangeDTO;
        try {
          entityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
        }
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processProjectEntityChangeEvent(entityChangeDTO, action);
        }
      }
    }
    return true;
  }

  private boolean processProjectEntityChangeEvent(AccountEntityChangeDTO entityChangeDTO, String action) {
    return !DELETE_ACTION.equals(action) || processDeleteEvent(entityChangeDTO);
  }

  private boolean processDeleteEvent(AccountEntityChangeDTO entityChangeDTO) {
    return deleteDeploymentAccount(entityChangeDTO.getAccountId());
  }

  private boolean deleteDeploymentAccount(String accountId) {
    try {
      Criteria criteria = new Criteria();
      criteria.and(DeploymentAccountsKeys.accountIdentifier).is(accountId);

      Failsafe.with(getDeleteRetryPolicy(accountId))
          .get(() -> mongoTemplate.remove(new Query(criteria), DeploymentAccounts.class));
      return true;
    } catch (Exception e) {
      log.warn(format("Error while deleting DeploymentAccounts for Account [%s] : %s", accountId,
                   ExceptionUtils.getMessage(e)),
          e);
      return false;
    }
  }

  private RetryPolicy<Object> getDeleteRetryPolicy(String accountId) {
    return PersistenceUtils.getRetryPolicy(
        format("[Retrying]: Failed deleting DeploymentAccounts for account: [%s]; attempt: {}", accountId),
        format("[Failed]: Failed DeploymentAccounts for account: [%s]; attempt: {}", accountId));
  }
}
