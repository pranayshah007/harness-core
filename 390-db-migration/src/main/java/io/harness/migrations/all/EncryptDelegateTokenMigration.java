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
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateToken.DelegateTokenKeys;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class EncryptDelegateTokenMigration implements Migration {
  @Inject private HPersistence persistence;
  @Inject private DelegateNgTokenService delegateTokenService;

  @Override
  public void migrate() {
    log.info("Start migration to upsert encrypted delegateToken");
    Query<DelegateToken> query =
        persistence.createQuery(DelegateToken.class, excludeAuthority).field(DelegateTokenKeys.value).exists();
    try (HIterator<DelegateToken> records = new HIterator<>(query.fetch())) {
      for (DelegateToken delegateToken : records) {
        delegateToken.setTokenValue(delegateToken.getValue().toCharArray());
        persistence.save(delegateToken);
      }
    } catch (Exception e) {
      log.error("Exception while migration delegate token with encrypted value", e);
    }
    log.info("The migration completed to insert encrypted delegate token values");
  }
}
