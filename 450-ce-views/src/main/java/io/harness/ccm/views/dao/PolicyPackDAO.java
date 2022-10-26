/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.PolicyEnforcement;
import io.harness.ccm.views.entities.PolicyPack;
import io.harness.ccm.views.entities.PolicyPack.PolicySetId;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
public class PolicyPackDAO {
  @Inject private HPersistence hPersistence;

  public boolean save(PolicyPack policySet) {
    log.info("created: {}", hPersistence.save(policySet));
    return hPersistence.save(policySet) != null;
  }

  public boolean delete(String accountId, String uuid) {
    Query<PolicyPack> query = hPersistence.createQuery(PolicyPack.class)
                                  .field(PolicySetId.accountId)
                                  .equal(accountId)
                                  .field(PolicySetId.uuid)
                                  .equal(uuid);
    log.info("deleted policy: {}", uuid);
    return hPersistence.delete(query);
  }

  public PolicyPack update(PolicyPack policy) {
    Query query = hPersistence.createQuery(Policy.class)
                      .field(PolicySetId.accountId)
                      .equal(policy.getAccountId())
                      .field(PolicySetId.uuid)
                      .equal(policy.getUuid());
    UpdateOperations<PolicyPack> updateOperations =
        hPersistence.createUpdateOperations(PolicyPack.class)
            .set(PolicySetId.name, policy.getName())
            //          .set(PolicyPack.PolicySetId.tags, policy.getTags())
            //            .set(PolicyPack.PolicySetId.policySetPolicies, policy.getPolicySetPolicies())
            //            .set(PolicyPack.PolicySetId.policySetExecutionCron, policy.getPolicySetExecutionCron())
            //            .set(PolicyPack.PolicySetId.policySetTargetAccounts, policy.getPolicySetTargetAccounts())
            //            .set(PolicyPack.PolicySetId.policySetTargetRegions, policy.getPolicySetTargetRegions())
            //            .set(PolicyPack.PolicySetId.isEnabled, policy.getIsEnabled())
            .set(PolicySetId.lastUpdatedAt, policy.getLastUpdatedAt());

    hPersistence.update(query, updateOperations);
    log.info("Updated policy: {}", policy.getUuid());
    return policy;
  }

  public PolicyPack listid(String accountId, String name, boolean create) {
    try {
      List<PolicyPack> policyPacks = hPersistence.createQuery(PolicyPack.class)
                                         .field(PolicySetId.accountId)
                                         .equal(accountId)
                                         .field(PolicySetId.name)
                                         .equal(name)
                                         .asList();
      policyPacks.addAll(hPersistence.createQuery(PolicyPack.class)
                             .field(PolicySetId.accountId)
                             .equal("")
                             .field(PolicySetId.name)
                             .equal(name)
                             .asList());
      return policyPacks.get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such policy pack exists,{} accountId{} name {} {}", e, accountId, name, create);
      if (create) {
        log.info("returning null");
        return null;
      }
      throw new InvalidRequestException("No such policy pack exists");
    }
  }
  public void check(String accountId, List<String> policiesPackIdentifier) {
    for (String identifiers : policiesPackIdentifier) {
      listid(accountId, identifiers, false);
    }
  }
  public List<PolicyPack> list(String accountId) {
    List<PolicyPack> policyPacks = hPersistence.createQuery(PolicyPack.class)
                                       .field(PolicySetId.accountId)
                                       .equal(accountId)
                                       .order(Sort.descending(PolicyEnforcement.PolicyEnforcementId.lastUpdatedAt))
                                       .asList();
    policyPacks.addAll(hPersistence.createQuery(PolicyPack.class)
                           .field(PolicySetId.accountId)
                           .equal("")
                           .order(Sort.descending(PolicyEnforcement.PolicyEnforcementId.lastUpdatedAt))
                           .asList());
    log.info("list size {}", policyPacks.size());
    return policyPacks;
  }
}
