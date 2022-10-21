/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.Policy.PolicyId;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
public class PolicyDAO {
  @Inject private HPersistence hPersistence;

  public boolean save(Policy policy) {
    log.info("created: {}", hPersistence.save(policy));
    return hPersistence.save(policy) != null;
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

  public List<Policy> list(String accountId) {
    return hPersistence.createQuery(Policy.class).field(PolicyId.accountId).equal(accountId).asList();
  }

  public List<Policy> findByResource(String resource, String accountId) {
    Query<Policy> query = hPersistence.createQuery(Policy.class)
                              .filter(PolicyId.resource, resource)
                              .filter(PolicyId.accountId, accountId);
    return query.asList();
  }

  public List<Policy> findByTag(String tag, String accountId) {
    Query<Policy> query =
        hPersistence.createQuery(Policy.class).filter(PolicyId.tags, tag).filter(PolicyId.accountId, accountId);
    return query.asList();
  }

  public Policy listid(String accountId, String name, boolean create) {
    try {
      return hPersistence.createQuery(Policy.class)
          .field(PolicyId.accountId)
          .equal(accountId)
          .field(PolicyId.name)
          .equal(name)
          .asList()
          .get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such policy exists,{} accountId{} uuid{}", e, accountId, name);
      if (create == true) {
        return null;
      }
      throw new InvalidRequestException("No such policy exists");
    }
  }

  public List<Policy> findByTagAndResource(String resource, String tag, String accountId) {
    Query<Policy> query = hPersistence.createQuery(Policy.class)
                              .filter(PolicyId.resource, resource)
                              .filter(PolicyId.tags, tag)
                              .filter(PolicyId.accountId, accountId);
    log.info("Query: {}", query);
    return query.asList();
  }

  public Policy update(Policy policy) {
    Query query = hPersistence.createQuery(Policy.class)
                      .field(PolicyId.accountId)
                      .equal(policy.getAccountId())
                      .field(PolicyId.uuid)
                      .equal(policy.getUuid());
    UpdateOperations<Policy> updateOperations = hPersistence.createUpdateOperations(Policy.class)
                                                    .set(PolicyId.resource, policy.getResource())
                                                    .set(PolicyId.name, policy.getName())
                                                    .set(PolicyId.description, policy.getDescription())
                                                    .set(PolicyId.policyYaml, policy.getPolicyYaml())
                                                    .set(PolicyId.isStablePolicy, policy.getIsStablePolicy())
                                                    .set(PolicyId.isOOTBPolicy, policy.getIsOOTBPolicy())
                                                    .set(PolicyId.tags, policy.getTags())
                                                    .set(PolicyId.lastUpdatedAt, policy.getLastUpdatedAt());

    hPersistence.update(query, updateOperations);
    log.info("Updated policy: {}", policy.getUuid());
    return policy;
  }

  public List<Policy> findByStability(String isStablePolicy, String accountId) {
    Query<Policy> query = hPersistence.createQuery(Policy.class)
                              .filter(PolicyId.isStablePolicy, isStablePolicy)
                              .filter(PolicyId.accountId, accountId);
    return query.asList();
  }
  public void check(String accountId, List<String> policiesIdentifier) {
    for (String identifiers : policiesIdentifier) {
      listid(accountId, identifiers, false);
    }
  }
}
