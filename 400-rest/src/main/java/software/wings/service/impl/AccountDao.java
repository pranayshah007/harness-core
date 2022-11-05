/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.persistence.HPersistence;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;

@OwnedBy(PL)
public class AccountDao {
  @Inject private HPersistence persistence;

  public String save(Account account) {
    return persistence.save(account);
  }

  public void updateCeLicense(String accountId, CeLicenseInfo ceLicenseInfo) {
    Query query = persistence.createQuery(Account.class).field(AccountKeys.uuid).equal(accountId);
    UpdateOperations<Account> updateOperations =
        persistence.createUpdateOperations(Account.class).set(AccountKeys.ceLicenseInfo, ceLicenseInfo);
    persistence.update(query, updateOperations);
  }

  public Account get(String accountId) {
    return persistence.get(Account.class, accountId);
  }
}
