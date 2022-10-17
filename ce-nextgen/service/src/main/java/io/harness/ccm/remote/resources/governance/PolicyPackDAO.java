/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
public class PolicyPackDAO {
  @Inject private HPersistence hPersistence;

  public boolean save(PolicyPack policyPack) {
    log.info("created: {}", hPersistence.save(policyPack));
    return hPersistence.save(policyPack) != null;
  }

  public boolean delete(String accountId, String uuid) {
    Query<PolicyPack> query = hPersistence.createQuery(PolicyPack.class)
                                 .field(PolicyPack.PolicySetId.accountId)
                                 .equal(accountId)
                                 .field(PolicyPack.PolicySetId.uuid)
                                 .equal(uuid);
    log.info("deleted policy: {}", uuid);
    return hPersistence.delete(query);
  }

  public PolicyPack update(PolicyPack policy) {
    Query query = hPersistence.createQuery(Policy.class)
                      .field(PolicyPack.PolicySetId.accountId)
                      .equal(policy.getAccountId())
                      .field(PolicyPack.PolicySetId.uuid)
                      .equal(policy.getUuid());
    UpdateOperations<PolicyPack> updateOperations =
        hPersistence.createUpdateOperations(PolicyPack.class)
            .set(PolicyPack.PolicySetId.name, policy.getName())
            .set(PolicyPack.PolicySetId.tags, policy.getTags())
            .set(PolicyPack.PolicySetId.policiesIdentifier, policy.getPoliciesIdentifier())
            .set(PolicyPack.PolicySetId.lastUpdatedAt, policy.getLastUpdatedAt());

    hPersistence.update(query, updateOperations);
    log.info("Updated policy: {}", policy.getUuid());
    return policy;
  }
}
