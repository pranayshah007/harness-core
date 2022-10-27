/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.GovernancePolicyFilter;
import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.Policy.PolicyId;
import io.harness.ccm.views.entities.PolicyExecution;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
public class PolicyDAO {
  @Inject private HPersistence hPersistence;
  private List<Policy> policies;

  public boolean save(Policy policy) {
    log.info("created: {}", hPersistence.save(policy));
    return hPersistence.save(policy) != null;
  }

  public boolean deleteOOTB(String uuid) {
    Query<Policy> query = hPersistence.createQuery(Policy.class).field(PolicyId.uuid).equal(uuid);
    log.info("deleted policy: {}", uuid);
    return hPersistence.delete(query);
  }
  public boolean delete(String accountId, String uuid) {
    Query<Policy> query = hPersistence.createQuery(Policy.class)
                              .field(PolicyId.accountId)
                              .equal(accountId)
                              .field(PolicyId.uuid)
                              .equal(uuid);
    log.info("deleted policy: {}", uuid);
    return hPersistence.delete(query);
  }

  public List<Policy> list(GovernancePolicyFilter governancePolicyFilter) {
    Query<Policy> policiesCustom = hPersistence.createQuery(Policy.class)
                                       .field(PolicyId.accountId)
                                       .equal(governancePolicyFilter.getAccountId())
                                       .order(Sort.descending(PolicyExecution.PolicyExecutionKeys.lastUpdatedAt));
    Query<Policy> policiesOOTB = hPersistence.createQuery(Policy.class)
                                     .field(PolicyId.accountId)
                                     .equal("")
                                     .order(Sort.descending(PolicyExecution.PolicyExecutionKeys.lastUpdatedAt));

    if (governancePolicyFilter.getCloudProvider() != null) {
      policiesOOTB.field(PolicyId.cloudProvider).equal(governancePolicyFilter.getCloudProvider());
      policiesCustom.field(PolicyId.cloudProvider).equal(governancePolicyFilter.getCloudProvider());

    }
    if(governancePolicyFilter.getIsOOTB()!=null) {
      if(governancePolicyFilter.getIsOOTB()) {
        return policiesOOTB.asList();
      }
      return policiesCustom.asList();
    }

      log.info("Adding all available policies");
      List<Policy> policies = policiesOOTB.asList();
      policies.addAll(policiesCustom.asList());
      return policies;
    }


  public Policy listid(String accountId, String name, boolean create) {
    try {
      List<Policy> policies = hPersistence.createQuery(Policy.class)
                                  .field(PolicyId.accountId)
                                  .equal(accountId)
                                  .field(PolicyId.name)
                                  .equal(name)
                                  .asList();
      policies.addAll(hPersistence.createQuery(Policy.class)
                          .field(PolicyId.accountId)
                          .equal("")
                          .field(PolicyId.name)
                          .equal(name)
                          .asList());
      return policies.get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such policy exists,{} accountId {} name {}", e, accountId, name);
      if (create) {
        return null;
      }
      throw new InvalidRequestException("No such policy exists");
    }
  }

  public Policy update(Policy policy) {
    Query query = hPersistence.createQuery(Policy.class)
                      .field(PolicyId.accountId)
                      .equal(policy.getAccountId())
                      .field(PolicyId.uuid)
                      .equal(policy.getUuid());
    UpdateOperations<Policy> updateOperations = hPersistence.createUpdateOperations(Policy.class)
                                                    .set(PolicyId.name, policy.getName())
                                                    .set(PolicyId.description, policy.getDescription())
                                                    .set(PolicyId.policyYaml, policy.getPolicyYaml())
                                                    .set(PolicyId.isStablePolicy, policy.getIsStablePolicy())
                                                    .set(PolicyId.isOOTB, policy.getIsOOTB())
                                                    .set(PolicyId.tags, policy.getTags())
                                                    .set(PolicyId.lastUpdatedAt, policy.getLastUpdatedAt());

    hPersistence.update(query, updateOperations);
    log.info("Updated policy: {}", policy.getUuid());
    return policy;
  }

  public void check(String accountId, List<String> policiesIdentifier) {
    for (String identifiers : policiesIdentifier) {
      listid(accountId, identifiers, false);
    }
  }
}
