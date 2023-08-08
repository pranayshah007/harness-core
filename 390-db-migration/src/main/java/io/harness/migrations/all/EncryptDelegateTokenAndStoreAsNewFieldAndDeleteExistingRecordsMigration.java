/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.authenticator.DelegateSecretManager;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateToken.DelegateTokenKeys;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.secrets.SecretsDao;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class EncryptDelegateTokenAndStoreAsNewFieldAndDeleteExistingRecordsMigration implements Migration {
  @Inject private HPersistence persistence;
  @Inject private DelegateSecretManager delegateSecretManager;
  @Inject private SecretsDao secretsDao;
  private static final String MIGRATE_DELEGATE_TOKENS = "MigrateDelegateTokens: ";

  @Override
  public void migrate() {
    log.info(MIGRATE_DELEGATE_TOKENS + "Start migration to upsert encrypted delegateToken");
    List<String> updateList = new ArrayList<>();
    Query<DelegateToken> query =
        persistence.createQuery(DelegateToken.class, excludeAuthority).field(DelegateTokenKeys.value).exists();
    try (HIterator<DelegateToken> iterator = new HIterator<>(query.fetch())) {
      while (iterator.hasNext()) {
        updateList.add(iterator.next().getUuid());
        if (updateList.size() % 500 == 0) {
          updateDelegateTokenRecord(updateList);
          updateList.clear();
        }
      }
      if (!updateList.isEmpty()) {
        updateDelegateTokenRecord(updateList);
      }
    } catch (Exception e) {
      log.error(MIGRATE_DELEGATE_TOKENS + "Exception while migration delegate token with encrypted value", e);
    }
    log.info(MIGRATE_DELEGATE_TOKENS + "The migration completed to insert encrypted delegate token values");
  }

  private void updateDelegateTokenRecord(List<String> updateList) {
    Query<DelegateToken> query = persistence.createQuery(DelegateToken.class)
                                     .field(DelegateTokenKeys.uuid)
                                     .in(updateList)
                                     .project(DelegateTokenKeys.accountId, true)
                                     .project(DelegateTokenKeys.value, true)
                                     .project(DelegateTokenKeys.name, true)
                                     .project(DelegateTokenKeys.owner, true)
                                     .project(DelegateTokenKeys.encryptedTokenId, true);
    int count = 0;
    try (HIterator<DelegateToken> iterator = new HIterator<>(query.fetch())) {
      while (iterator.hasNext()) {
        DelegateToken delegateToken = iterator.next();
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
        } catch (Exception ex) {
          log.error(MIGRATE_DELEGATE_TOKENS
                  + "Error occurred when trying to migrate DelegateToken with Id {} with exception {}",
              delegateToken.getUuid(), ex);
        }
      }
    }
    log.info(MIGRATE_DELEGATE_TOKENS + "{} delegate token records updated.", count);
  }
}
