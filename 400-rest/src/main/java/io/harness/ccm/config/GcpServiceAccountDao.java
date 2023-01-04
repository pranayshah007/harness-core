/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.config;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.config.GcpServiceAccount.GcpServiceAccountKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;

@OwnedBy(CE)
public class GcpServiceAccountDao {
  @Inject private HPersistence persistence;

  public String save(GcpServiceAccount gcpServiceAccount) {
    return persistence.save(gcpServiceAccount);
  }

  public GcpServiceAccount getByServiceAccountId(String serviceAccountId) {
    Query<GcpServiceAccount> query = persistence.createQuery(GcpServiceAccount.class)
                                         .field(GcpServiceAccountKeys.serviceAccountId)
                                         .equal(serviceAccountId);
    return query.get();
  }

  public GcpServiceAccount getByAccountId(String accountId) {
    Query<GcpServiceAccount> query =
        persistence.createQuery(GcpServiceAccount.class).field(GcpServiceAccountKeys.accountId).equal(accountId);
    return query.get();
  }
}
