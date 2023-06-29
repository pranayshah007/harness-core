/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.NG_USER_CLEANUP_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SYNC_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.services.OrganizationService;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class AccountSetupListener implements MessageListener {
  private final OrganizationService organizationService;
  private final NGAccountSetupService ngAccountSetupService;
  private final AccountClient accountClient;

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
        String entityType = metadataMap.get(ENTITY_TYPE);
        if (ACCOUNT_ENTITY.equals(entityType)) {
          AccountEntityChangeDTO accountEntityChangeDTO;
          try {
            accountEntityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
          } catch (InvalidProtocolBufferException e) {
            throw new InvalidRequestException(
                String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
          }
          String action = metadataMap.get(ACTION);
          log.info("[AccountSetupListener]: Received Account Entity Change message for account- {} for action- {}",
              accountEntityChangeDTO.getAccountId(), action);
          if (action != null) {
            return processAccountEntityChangeEvent(accountEntityChangeDTO, action);
          }
        }
      }
    }
    return true;
  }

  private boolean processAccountEntityChangeEvent(AccountEntityChangeDTO accountEntityChangeDTO, String action) {
    switch (action) {
      case DELETE_ACTION:
        return processAccountDeleteEvent(accountEntityChangeDTO);
      case SYNC_ACTION:
        return processSyncNGWithCG(accountEntityChangeDTO);
      case RESTORE_ACTION:
        return processAccountRestoreEvent(accountEntityChangeDTO);
      case UPDATE_ACTION:
        return processAccountUpdateEvent(accountEntityChangeDTO);
      case NG_USER_CLEANUP_ACTION:
        return processNGUserCleanupEvent(accountEntityChangeDTO);
      default:
    }
    return true;
  }

  private boolean processAccountUpdateEvent(AccountEntityChangeDTO accountEntityChangeDTO) {
    log.info(String.format(
        "[AccountSetupListener]: Received account update event for account %s", accountEntityChangeDTO.getAccountId()));
    AccountDTO account = CGRestUtils.getResponse(accountClient.getAccountDTO(accountEntityChangeDTO.getAccountId()));
    if (account.isNextGenEnabled()) {
      log.info("Starting to setup account- {} for NG", accountEntityChangeDTO.getAccountId());
      ngAccountSetupService.setupAccountForNG(accountEntityChangeDTO.getAccountId());
    }
    return true;
  }
  private boolean processSyncNGWithCG(AccountEntityChangeDTO accountEntityChangeDTO) {
    ngAccountSetupService.setupAccountForNG(accountEntityChangeDTO.getAccountId());
    return true;
  }

  private boolean processNGUserCleanupEvent(AccountEntityChangeDTO accountEntityChangeDTO) {
    log.info(String.format(
        "[AccountSetupListener]: Received account cleanup event for account %s. Staring the cleanUp event",
        accountEntityChangeDTO.getAccountId()));
    ngAccountSetupService.cleanUsersFromAccountForNg(accountEntityChangeDTO.getAccountId());
    return true;
  }

  private boolean processAccountDeleteEvent(AccountEntityChangeDTO accountEntityChangeDTO) {
    log.info(String.format(
        "[AccountSetupListener]: Received account delete event for account %s", accountEntityChangeDTO.getAccountId()));
    String accountIdentifier = accountEntityChangeDTO.getAccountId();
    Criteria criteria = Criteria.where(OrganizationKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(OrganizationKeys.deleted)
                            .ne(Boolean.TRUE);
    List<Organization> organizations = organizationService.list(criteria);
    AtomicBoolean success = new AtomicBoolean(true);
    organizations.forEach(organization -> {
      if (!organizationService.delete(organization.getAccountIdentifier(), organization.getIdentifier(), null)) {
        log.error(String.format("Delete operation failed for organization with accountIdentifier %s and identifier %s",
            organization.getAccountIdentifier(), organization.getIdentifier()));
        success.set(false);
      }
    });
    if (success.get()) {
      log.info(String.format(
          "Successfully completed deletion for organizations in account with identifier %s", accountIdentifier));
    }
    return success.get();
  }

  private boolean processAccountRestoreEvent(AccountEntityChangeDTO accountEntityChangeDTO) {
    log.info(String.format("[AccountSetupListener]: Received account restore event for account %s",
        accountEntityChangeDTO.getAccountId()));
    String accountIdentifier = accountEntityChangeDTO.getAccountId();
    Criteria criteria = Criteria.where(OrganizationKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(OrganizationKeys.deleted)
                            .is(Boolean.TRUE);
    List<Organization> organizations = organizationService.list(criteria);
    AtomicBoolean success = new AtomicBoolean(true);
    organizations.forEach(organization -> {
      if (!organizationService.restore(organization.getAccountIdentifier(), organization.getIdentifier())) {
        log.error(String.format("Restore operation failed for organization with accountIdentifier %s and identifier %s",
            organization.getAccountIdentifier(), organization.getIdentifier()));
        success.set(false);
      }
    });
    if (success.get()) {
      log.info(String.format(
          "Successfully completed restoration for organizations in account with identifier %s", accountIdentifier));
    }
    return success.get();
  }
}
