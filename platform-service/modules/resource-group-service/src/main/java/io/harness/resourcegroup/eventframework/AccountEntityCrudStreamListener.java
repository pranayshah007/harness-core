/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.eventframework;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.remote.client.CGRestUtils.getResponse;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.account.AccountClient;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.event.MessageListener;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.v2.model.ResourceGroup.ResourceGroupKeys;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class AccountEntityCrudStreamListener implements MessageListener {
  @Inject AccountClient accountClient;
  @Inject ResourceGroupService resourceGroupService;

  public static final String PUBLIC_RESOURCE_GROUP_IDENTIFIER = "_public_resources";
  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
        String entityType = metadataMap.get(ENTITY_TYPE);
        if (entityType.equals(ACCOUNT_ENTITY)) {
          AccountEntityChangeDTO entityChangeDTO = null;
          try {
            entityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
          } catch (InvalidProtocolBufferException e) {
            log.error(
                "Exception in unpacking AccountEntityChangeDTO for account event with key {}", message.getId(), e);
          }
          if (Objects.isNull(entityChangeDTO)) {
            return true;
          }
          log.debug(String.format("Processing event id: %s Account %s %s", message.getId(),
              stripToNull(entityChangeDTO.getAccountId()), getEventType(message)));
          try {
            if (getEventType(message).equals(UPDATE_ACTION) || getEventType(message).equals(DELETE_ACTION)) {
              return disablePublicAccessOnAccount(entityChangeDTO.getAccountId());
            }
          } catch (Exception e) {
            log.error("Could not process the resource group change event {} due to error", entityChangeDTO, e);
            return false;
          }
        }
      }
    }
    return true;
  }

  private String getEventType(Message message) {
    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    return metadataMap.get(ACTION);
  }

  private boolean disablePublicAccessOnAccount(String account) {
    AccountDTO accountDTO = getResponse(accountClient.getAccountDTO(account));
    boolean isPublic = accountDTO.isPublicAccessEnabled();
    if (Boolean.FALSE.equals(isPublic)) {
      return deletePublicResourceGroups(account);
    }
    return true;
  }

  private boolean deletePublicResourceGroups(String account) {
    try {
      Criteria criteria = Criteria.where(ResourceGroupKeys.accountIdentifier)
                              .is(account)
                              .and(ResourceGroupKeys.identifier)
                              .is(PUBLIC_RESOURCE_GROUP_IDENTIFIER);
      boolean isDeleted = resourceGroupService.deleteMulti(criteria);
      return Boolean.TRUE.equals(isDeleted);
    } catch (Exception e) {
      log.error(
          "There was an error while deleting resource groups for account {} on disabling public access", account, e);
      throw e;
    }
  }
}
