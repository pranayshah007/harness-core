/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.CEGcpServiceAccount;
import io.harness.ccm.commons.entities.billing.CEGcpServiceAccount.CEGcpServiceAccountKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;

@OwnedBy(CE)
public class CEGcpServiceAccountDao {
  @Inject private HPersistence persistence;

  public String save(CEGcpServiceAccount gcpServiceAccount) {
    return persistence.save(gcpServiceAccount);
  }

  public CEGcpServiceAccount getUnassignedServiceAccountByAccountId(String accountId) {
    Query<CEGcpServiceAccount> query =
        persistence.createQuery(CEGcpServiceAccount.class).field(CEGcpServiceAccountKeys.accountId).equal(accountId);
    return query.get();
  }

  public CEGcpServiceAccount getByServiceAccountId(String serviceAccountEmail) {
    Query<CEGcpServiceAccount> query = persistence.createQuery(CEGcpServiceAccount.class)
                                           .field(CEGcpServiceAccountKeys.email)
                                           .equal(serviceAccountEmail);
    return query.get();
  }
}
