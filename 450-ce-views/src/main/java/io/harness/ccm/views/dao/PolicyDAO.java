/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.Policy;
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
                              .field(Policy.PolicyId.accountId)
                              .equal(accountId)
                              .field(Policy.PolicyId.uuid)
                              .equal(uuid);
    log.info("deleted policy: {}", uuid);
    return hPersistence.delete(query);
  }

  public List<Policy> list(String accountId) {
    return hPersistence.createQuery(Policy.class).field(Policy.PolicyId.accountId).equal(accountId).asList();
  }

  public List<Policy> findByResource(String resource, String accountId) {
    Query<Policy> query = hPersistence.createQuery(Policy.class)
                              .filter(Policy.PolicyId.resource, resource)
                              .filter(Policy.PolicyId.accountId, accountId);
    return query.asList();
  }

  public List<Policy> findByTag(String tag, String accountId) {
    Query<Policy> query = hPersistence.createQuery(Policy.class)
                              .filter(Policy.PolicyId.tags, tag)
                              .filter(Policy.PolicyId.accountId, accountId);
    return query.asList();
  }

  public Policy listid(String accountId, String uuid) {
  try {
    return hPersistence.createQuery(Policy.class)
            .field(Policy.PolicyId.accountId)
            .equal(accountId)
            .field(Policy.PolicyId.uuid)
            .equal(uuid)
            .asList()
            .get(0);
  } catch (IndexOutOfBoundsException e) {
    log.error("No such policy exists,{} accountId{} uuid{}", e,accountId,uuid);
    throw new InvalidRequestException("No such policy exists");
  }
  }

  public List<Policy> findByTagAndResource(String resource, String tag, String accountId) {
    Query<Policy> query = hPersistence.createQuery(Policy.class)
                              .filter(Policy.PolicyId.resource, resource)
                              .filter(Policy.PolicyId.tags, tag)
                              .filter(Policy.PolicyId.accountId, accountId);
    log.info("Query: {}", query);
    return query.asList();
  }

  public Policy update(Policy policy) {
    Query query = hPersistence.createQuery(Policy.class)
                      .field(Policy.PolicyId.accountId)
                      .equal(policy.getAccountId())
                      .field(Policy.PolicyId.uuid)
                      .equal(policy.getUuid());
    UpdateOperations<Policy> updateOperations = hPersistence.createUpdateOperations(Policy.class)
                                                    .set(Policy.PolicyId.resource, policy.getResource())
                                                    .set(Policy.PolicyId.name, policy.getName())
                                                    .set(Policy.PolicyId.description, policy.getDescription())
                                                    .set(Policy.PolicyId.policyYaml, policy.getPolicyYaml())
                                                    .set(Policy.PolicyId.isStablePolicy, policy.getIsStablePolicy())
                                                    .set(Policy.PolicyId.isOOTBPolicy, policy.getIsOOTBPolicy())
                                                    .set(Policy.PolicyId.tags, policy.getTags())
                                                    .set(Policy.PolicyId.lastUpdatedAt, policy.getLastUpdatedAt());

    hPersistence.update(query, updateOperations);
    log.info("Updated policy: {}", policy.getUuid());
    return policy;
  }

  public List<Policy> findByStability(String isStablePolicy, String accountId) {
    Query<Policy> query = hPersistence.createQuery(Policy.class)
                              .filter(Policy.PolicyId.isStablePolicy, isStablePolicy)
                              .filter(Policy.PolicyId.accountId, accountId);
    return query.asList();
  }
}
