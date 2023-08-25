/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.beans.PageRequest.DEFAULT_UNLIMITED;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EXISTS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.delegate.authenticator.DelegateSecretManager;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateToken.DelegateTokenKeys;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.migrations.Migration;
import io.harness.persistence.HPersistence;
import io.harness.secrets.SecretsDao;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class EncryptDelegateTokenViaPagination implements Migration {
  @Inject private HPersistence persistence;
  @Inject private DelegateSecretManager delegateSecretManager;
  @Inject private SecretsDao secretsDao;
  private static final String MIGRATE_DELEGATE_TOKENS = "MigrateDelegateTokens: ";

  @Override
  public void migrate() {
    log.info(MIGRATE_DELEGATE_TOKENS + "Start migration to upsert encrypted delegateToken");
    int cnt = 0;
    try {
      do {
        PageRequest<DelegateToken> pageRequest =
            aPageRequest()
                .addFilter(DelegateTokenKeys.value, EXISTS)
                .addFieldsIncluded("uuid", "accountId", "value", "name", "owner", "encryptedTokenId")
                .withLimit(String.valueOf(DEFAULT_UNLIMITED))
                .withOffset(String.valueOf(cnt))
                .build();
        List<DelegateToken> delegateTokensList = persistence.query(DelegateToken.class, pageRequest);
        if (isEmpty(delegateTokensList)) {
          break;
        }
        updateDelegateTokenRecords(delegateTokensList);
        cnt += delegateTokensList.size();
      } while (true);
    } catch (Exception e) {
      log.error(MIGRATE_DELEGATE_TOKENS + "Exception while migration delegate token with encrypted value", e);
    }
    log.info(MIGRATE_DELEGATE_TOKENS + "The migration completed to insert encrypted delegate token values");
  }

  private void updateDelegateTokenRecords(List<DelegateToken> updateList) {
    int count = 0;
    for (DelegateToken delegateToken : updateList) {
      try {
        UpdateOperations<DelegateToken> updateOperation = persistence.createUpdateOperations(DelegateToken.class);
        String tokenIdentifier = delegateToken.getName().replace("/", "_");
        if (delegateToken.getOwner() != null) {
          String orgId =
              DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(delegateToken.getOwner().getIdentifier());
          String projectId =
              DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(delegateToken.getOwner().getIdentifier());
          tokenIdentifier = String.format("%s_%s_%s", delegateToken.getName().replace("/", "_"), orgId, projectId);
        }
        if (null != delegateToken.getValue()) {
          String encryptedTokenId =
              delegateSecretManager.encrypt(delegateToken.getAccountId(), delegateToken.getValue(), tokenIdentifier);
          String existingEncryptedRecordId = delegateToken.getEncryptedTokenId();
          setUnset(updateOperation, DelegateTokenKeys.encryptedTokenId, encryptedTokenId);
          persistence.update(delegateToken, updateOperation);
          if (null != existingEncryptedRecordId) {
            log.info(MIGRATE_DELEGATE_TOKENS + "deleting encryptedRecord with Id {}", existingEncryptedRecordId);
            secretsDao.deleteSecret(delegateToken.getAccountId(), existingEncryptedRecordId);
          }
          count++;
        }
      } catch (Exception ex) {
        log.error(MIGRATE_DELEGATE_TOKENS
                + "Error occurred when trying to migrate DelegateToken with Id {} with exception {}",
            delegateToken.getUuid(), ex);
      }
    }
    log.info(MIGRATE_DELEGATE_TOKENS + "{} delegate token records updated.", count);
  }
}
