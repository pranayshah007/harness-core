/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.PolicyEnforcement;
import io.harness.ccm.views.entities.PolicyEnforcement.PolicyEnforcementId;
import io.harness.ccm.views.service.GovernancePolicyService;
import io.harness.ccm.views.service.PolicyPackService;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
public class PolicyEnforcementDAO {
  @Inject private HPersistence hPersistence;
  private GovernancePolicyService policyService;
  private PolicyPackService policyPackService;

  public boolean save(PolicyEnforcement PolicyEnforcement) {
    log.info("created: {}", hPersistence.save(PolicyEnforcement));
    return hPersistence.save(PolicyEnforcement) != null;
  }

  public boolean delete(String accountId, String uuid) {
    Query<PolicyEnforcement> query = hPersistence.createQuery(PolicyEnforcement.class)
                                         .field(PolicyEnforcementId.accountId)
                                         .equal(accountId)
                                         .field(PolicyEnforcementId.uuid)
                                         .equal(uuid);
    log.info("deleted policy: {}", uuid);
    return hPersistence.delete(query);
  }

  public PolicyEnforcement update(PolicyEnforcement policy) {
    Query<PolicyEnforcement> query = hPersistence.createQuery(PolicyEnforcement.class)
                                         .field(PolicyEnforcementId.accountId)
                                         .equal(policy.getAccountId())
                                         .field(PolicyEnforcementId.uuid)
                                         .equal(policy.getUuid());
    UpdateOperations<PolicyEnforcement> updateOperations = hPersistence.createUpdateOperations(PolicyEnforcement.class);
    if (policy.getName() != null) {
      updateOperations.set(PolicyEnforcementId.name, policy.getName());
    }
    if (policy.getPolicyIds() != null) {
      updateOperations.set(PolicyEnforcementId.policyIds, policy.getPolicyIds());
    }
    if (policy.getPolicyPackIDs() != null) {
      updateOperations.set(PolicyEnforcementId.policyPackIDs, policy.getPolicyPackIDs());
    }
    if (policy.getExecutionSchedule() != null) {
      updateOperations.set(PolicyEnforcementId.executionSchedule, policy.getExecutionSchedule());
    }
    if (policy.getExecutionTimezone() != null) {
      updateOperations.set(PolicyEnforcementId.executionTimezone, policy.getExecutionTimezone());
    }
    if (policy.getTargetAccounts() != null) {
      updateOperations.set(PolicyEnforcementId.targetAccounts, policy.getTargetAccounts());
    }
    if (policy.getTargetRegions() != null) {
      updateOperations.set(PolicyEnforcementId.targetRegions, policy.getTargetRegions());
    }
    if (policy.getIsDryRun() != null) {
      updateOperations.set(PolicyEnforcementId.executionTimezone, policy.getIsDryRun());
    }
    if (policy.getIsEnabled() != null) {
      updateOperations.set(PolicyEnforcementId.isEnabled, policy.getIsEnabled());
    }

    hPersistence.update(query, updateOperations);
    log.info("Updated policy: {}", policy.getUuid());

    return query.asList().get(0);
  }

  public PolicyEnforcement listName(String accountId, String name, boolean create) {
    try {
      return hPersistence.createQuery(PolicyEnforcement.class)
          .field(PolicyEnforcementId.accountId)
          .equal(accountId)
          .field(PolicyEnforcementId.name)
          .equal(name)
          .asList()
          .get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such policy pack exists,{} accountId{} uuid{}", e, accountId, name);
      if (create) {
        return null;
      }
      throw new InvalidRequestException("No such policy pack exists");
    }
  }

  public PolicyEnforcement listid(String accountId, String uuid, boolean create) {
    try {
      return hPersistence.createQuery(PolicyEnforcement.class)
          .field(PolicyEnforcementId.accountId)
          .equal(accountId)
          .field(PolicyEnforcementId.uuid)
          .equal(uuid)
          .asList()
          .get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such policy pack exists,{} accountId{} uuid{}", e, accountId, uuid);
      if (create) {
        return null;
      }
      throw new InvalidRequestException("No such policy pack exists");
    }
  }

  public List<PolicyEnforcement> list(String accountId) {
    return hPersistence.createQuery(PolicyEnforcement.class)
        .field(PolicyEnforcementId.accountId)
        .equal(accountId)
        .order(Sort.descending(PolicyEnforcementId.lastUpdatedAt))
        .asList();
  }

  public List<PolicyEnforcement> policyEnforcementCount(String accountId, List<String> policyIds) {
    List<PolicyEnforcement> policyEnforcements = hPersistence.createQuery(PolicyEnforcement.class)
                                                     .field(PolicyEnforcementId.accountId)
                                                     .equal(accountId)
                                                     .field(PolicyEnforcementId.policyIds)
                                                     .hasAnyOf(policyIds)
                                                     .asList();
    log.info("{}",policyEnforcements);
    return policyEnforcements;
  }

  public List<PolicyEnforcement> policyPackEnforcementCount(String accountId, List<String> policyPackIds) {
    List<PolicyEnforcement> policyPackEnforcements = hPersistence.createQuery(PolicyEnforcement.class)
                                                     .field(PolicyEnforcementId.accountId)
                                                     .equal(accountId)
                                                     .field(PolicyEnforcementId.policyPackIDs)
                                                     .hasAnyOf(policyPackIds)
                                                     .asList();
    log.info("{}",policyPackEnforcements);
    return policyPackEnforcements;
  }

  public PolicyEnforcement get(String uuid) {
    return hPersistence.createQuery(PolicyEnforcement.class).field(PolicyEnforcementId.uuid).equal(uuid).get();
  }
}
