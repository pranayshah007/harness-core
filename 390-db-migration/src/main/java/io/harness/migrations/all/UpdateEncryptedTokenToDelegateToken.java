/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

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

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class UpdateEncryptedTokenToDelegateToken implements Migration {
  @Inject private HPersistence persistence;
  @Inject private DelegateSecretManager delegateSecretManager;

  private final String TOKEN_NAME_ILLEGAL_CHARACTERS = "[~!@#$%^&*'\"/?<>,;.]";

  @Override
  public void migrate() {
    log.info("Start migration to upsert encrypted delegateToken");
    List<String> updateList = new ArrayList<>();
    Query<DelegateToken> query = persistence.createQuery(DelegateToken.class, excludeAuthority)
                                     .field(DelegateTokenKeys.value)
                                     .exists()
                                     .field(DelegateTokenKeys.encryptedTokenId)
                                     .exists();

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
      log.error("Exception while migration delegate token with encrypted value", e);
    }
    log.info("The migration completed to insert encrypted delegate token values");
  }

  private void updateDelegateTokenRecord(List<String> updateList) {
    Query<DelegateToken> query = persistence.createQuery(DelegateToken.class)
                                     .field(DelegateTokenKeys.uuid)
                                     .in(updateList)
                                     .project(DelegateTokenKeys.accountId, true)
                                     .project(DelegateTokenKeys.encryptedTokenId, true)
                                     .project(DelegateTokenKeys.name, true)
                                     .project(DelegateTokenKeys.value, true);
    int count = 0;
    final DBCollection collection = persistence.getCollection(DelegateToken.class);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    try (HIterator<DelegateToken> iterator = new HIterator<>(query.fetch())) {
      while (iterator.hasNext()) {
        DelegateToken delegateToken = iterator.next();
        String tokenIdentifier = delegateToken.getName();
        if (delegateToken.getOwner() != null) {
          String orgId =
              DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(delegateToken.getOwner().getIdentifier());
          String projectId =
              DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(delegateToken.getOwner().getIdentifier());
          tokenIdentifier = String.format("%s_%s_%s", delegateToken.getName(), orgId, projectId);
        }
        String tokenNameSanitized = StringUtils.replaceAll(tokenIdentifier, TOKEN_NAME_ILLEGAL_CHARACTERS, "_");
        String decryptedTokenValue = delegateSecretManager.decrypt(delegateToken);
        if ((delegateToken.getName()).equals(decryptedTokenValue)) {
          log.info("Found delegate token record with value same as name {}.", delegateToken.getName());
          String encryptedTokenId =
              delegateSecretManager.encrypt(delegateToken.getAccountId(), delegateToken.getValue(), tokenNameSanitized);
          bulkWriteOperation.find(new BasicDBObject("_id", delegateToken.getUuid()))
              .updateOne(
                  new BasicDBObject("$set", new BasicDBObject(DelegateTokenKeys.encryptedTokenId, encryptedTokenId)));
        }
        count++;
      }
    }
    bulkWriteOperation.execute();
    log.info("{} delegate token records updated.", count);
  }
}
