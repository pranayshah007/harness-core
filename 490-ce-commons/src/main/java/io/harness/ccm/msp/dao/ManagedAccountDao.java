/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.msp.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.msp.dto.ManagedAccount;
import io.harness.ccm.msp.dto.ManagedAccount.ManagedAccountKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class ManagedAccountDao {
  @Inject private HPersistence hPersistence;

  public String save(ManagedAccount managedAccount) {
    return hPersistence.save(managedAccount);
  }

  public ManagedAccount get(String uuid) {
    return hPersistence.get(ManagedAccount.class, uuid);
  }

  public ManagedAccount get(String mspAccountId, String managedAccountId) {
    return hPersistence.createQuery(ManagedAccount.class)
        .field(ManagedAccountKeys.accountId)
        .equal(managedAccountId)
        .field(ManagedAccountKeys.mspAccountId)
        .equal(mspAccountId)
        .first();
  }

  public ManagedAccount getDetailsForAccount(String mspAccountId, String accountId) {
    return hPersistence.createQuery(ManagedAccount.class)
        .field(ManagedAccountKeys.accountId)
        .equal(accountId)
        .field(ManagedAccountKeys.mspAccountId)
        .equal(mspAccountId)
        .first();
  }

  public List<ManagedAccount> list(String mspAccountId) {
    return hPersistence.createQuery(ManagedAccount.class)
        .field(ManagedAccountKeys.mspAccountId)
        .equal(mspAccountId)
        .asList();
  }

  public ManagedAccount update(ManagedAccount managedAccount) {
    Query<ManagedAccount> query = hPersistence.createQuery(ManagedAccount.class)
                                      .field(ManagedAccountKeys.accountId)
                                      .equal(managedAccount.getAccountId())
                                      .field(ManagedAccountKeys.mspAccountId)
                                      .equal(managedAccount.getMspAccountId());

    hPersistence.update(query, getUpdateOperations(managedAccount));
    return managedAccount;
  }

  private UpdateOperations<ManagedAccount> getUpdateOperations(ManagedAccount managedAccount) {
    UpdateOperations<ManagedAccount> updateOperations = hPersistence.createUpdateOperations(ManagedAccount.class);

    if (managedAccount.getAccountId() != null) {
      setUnsetUpdateOperations(updateOperations, ManagedAccountKeys.accountId, managedAccount.getAccountId());
    }
    if (managedAccount.getAccountName() != null) {
      setUnsetUpdateOperations(updateOperations, ManagedAccountKeys.accountName, managedAccount.getAccountName());
    }
    if (managedAccount.getMspAccountId() != null) {
      setUnsetUpdateOperations(updateOperations, ManagedAccountKeys.mspAccountId, managedAccount.getMspAccountId());
    }
    return updateOperations;
  }

  private void setUnsetUpdateOperations(UpdateOperations<ManagedAccount> updateOperations, String key, Object value) {
    if (Objects.nonNull(value)) {
      updateOperations.set(key, value);
    } else {
      updateOperations.unset(key);
    }
  }

  public boolean delete(String accountId) {
    Query<ManagedAccount> query =
        hPersistence.createQuery(ManagedAccount.class).field(ManagedAccountKeys.accountId).equal(accountId);
    return hPersistence.delete(query);
  }
}
