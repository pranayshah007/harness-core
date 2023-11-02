/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.impl;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.batch.processing.dao.intfc.AccountShardMappingDao;
import io.harness.batch.processing.entities.AccountShardMapping;
import io.harness.batch.processing.entities.AccountShardMapping.AccountShardMappingKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class AccountShardMappingDaoImpl implements AccountShardMappingDao {
  @Autowired @Inject private HPersistence hPersistence;

  @Override
  public List<AccountShardMapping> getAccountShardMapping() {
    return hPersistence.createQuery(AccountShardMapping.class, excludeAuthority).asList();
  }

  @Override
  public long count(String accountId) {
    return hPersistence.createQuery(AccountShardMapping.class)
        .field(AccountShardMappingKeys.accountId)
        .equal(accountId)
        .count();
  }

  @Override
  public boolean delete(String accountId) {
    Query<AccountShardMapping> query =
        hPersistence.createQuery(AccountShardMapping.class).field(AccountShardMappingKeys.accountId).equal(accountId);
    return hPersistence.delete(query);
  }
}
