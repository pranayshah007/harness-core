/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.entities.RuleEnforcement.RuleEnforcementId;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class RuleEnforcementDAO {
  @Inject private HPersistence hPersistence;

  public boolean save(RuleEnforcement ruleEnforcement) {
    log.info("created: {}", hPersistence.save(ruleEnforcement));
    return hPersistence.save(ruleEnforcement) != null;
  }

  public boolean delete(String accountId, String uuid) {
    Query<RuleEnforcement> query = hPersistence.createQuery(RuleEnforcement.class)
                                       .field(RuleEnforcementId.accountId)
                                       .equal(accountId)
                                       .field(RuleEnforcementId.uuid)
                                       .equal(uuid);
    log.info("deleted rule: {}", uuid);
    return hPersistence.delete(query);
  }

  public void updateCount(RuleEnforcement rule) {
    Query<RuleEnforcement> query = hPersistence.createQuery(RuleEnforcement.class)
                                       .field(RuleEnforcementId.accountId)
                                       .equal(rule.getAccountId())
                                       .field(RuleEnforcementId.uuid)
                                       .equal(rule.getUuid());
    UpdateOperations<RuleEnforcement> updateOperations = hPersistence.createUpdateOperations(RuleEnforcement.class);
    updateOperations.set(RuleEnforcementId.runCount, rule.getRunCount());
    hPersistence.update(query, updateOperations);
  }

  public RuleEnforcement update(RuleEnforcement rule) {
    Query<RuleEnforcement> query = hPersistence.createQuery(RuleEnforcement.class)
                                       .field(RuleEnforcementId.accountId)
                                       .equal(rule.getAccountId())
                                       .field(RuleEnforcementId.uuid)
                                       .equal(rule.getUuid());
    UpdateOperations<RuleEnforcement> updateOperations = hPersistence.createUpdateOperations(RuleEnforcement.class);
    if (rule.getName() != null) {
      if (fetchByName(rule.getAccountId(), rule.getName(), true) != null) {
        throw new InvalidRequestException("Rule Enforcement with the given name already exits");
      }
      updateOperations.set(RuleEnforcementId.name, rule.getName());
    }
    if (rule.getRuleIds() != null) {
      updateOperations.set(RuleEnforcementId.ruleIds, rule.getRuleIds());
    }
    if (rule.getRuleSetIDs() != null) {
      updateOperations.set(RuleEnforcementId.ruleSetIDs, rule.getRuleSetIDs());
    }
    if (rule.getExecutionSchedule() != null) {
      updateOperations.set(RuleEnforcementId.executionSchedule, rule.getExecutionSchedule());
    }
    if (rule.getExecutionTimezone() != null) {
      updateOperations.set(RuleEnforcementId.executionTimezone, rule.getExecutionTimezone());
    }
    if (rule.getTargetAccounts() != null) {
      updateOperations.set(RuleEnforcementId.targetAccounts, rule.getTargetAccounts());
    }
    if (rule.getTargetRegions() != null) {
      updateOperations.set(RuleEnforcementId.targetRegions, rule.getTargetRegions());
    }
    if (rule.getIsDryRun() != null) {
      updateOperations.set(RuleEnforcementId.isDryRun, rule.getIsDryRun());
    }
    if (rule.getIsEnabled() != null) {
      updateOperations.set(RuleEnforcementId.isEnabled, rule.getIsEnabled());
    }

    hPersistence.update(query, updateOperations);
    log.info("Updated rule: {}", rule.getUuid());

    return query.asList().get(0);
  }

  public RuleEnforcement fetchByName(String accountId, String name, boolean create) {
    try {
      return hPersistence.createQuery(RuleEnforcement.class)
          .field(RuleEnforcementId.accountId)
          .equal(accountId)
          .field(RuleEnforcementId.name)
          .equal(name)
          .asList()
          .get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such rule pack exists,{} accountId{} uuid{}", e, accountId, name);
      if (create) {
        return null;
      }
      throw new InvalidRequestException("No such rule pack exists");
    }
  }

  public RuleEnforcement fetchById(String accountId, String uuid, boolean create) {
    try {
      return hPersistence.createQuery(RuleEnforcement.class)
          .field(RuleEnforcementId.accountId)
          .equal(accountId)
          .field(RuleEnforcementId.uuid)
          .equal(uuid)
          .asList()
          .get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such rule enforcement exists {} accountId {} uuid {}", e, accountId, uuid);
      if (create) {
        return null;
      }
      throw new InvalidRequestException("No such rule enforcement exists");
    }
  }

  public List<RuleEnforcement> list(String accountId) {
    return hPersistence.createQuery(RuleEnforcement.class)
        .field(RuleEnforcementId.accountId)
        .equal(accountId)
        .order(Sort.ascending(RuleEnforcementId.name))
        .asList();
  }

  public List<RuleEnforcement> ruleEnforcement(String accountId, List<String> ruleIds) {
    return hPersistence.createQuery(RuleEnforcement.class)
        .field(RuleEnforcementId.accountId)
        .equal(accountId)
        .field(RuleEnforcementId.ruleIds)
        .hasAnyOf(ruleIds)
        .asList();
  }

  public List<RuleEnforcement> ruleSetEnforcement(String accountId, List<String> ruleSetIds) {
    return hPersistence.createQuery(RuleEnforcement.class)
        .field(RuleEnforcementId.accountId)
        .equal(accountId)
        .field(RuleEnforcementId.ruleSetIDs)
        .hasAnyOf(ruleSetIds)
        .asList();
  }

  public List<RuleEnforcement> listAll(String accountId, List<String> ruleEnforcementId) {
    return hPersistence.createQuery(RuleEnforcement.class)
        .field(RuleEnforcementId.accountId)
        .equal(accountId)
        .field(RuleEnforcementId.uuid)
        .in(ruleEnforcementId)
        .asList();
  }

  public RuleEnforcement get(String uuid) {
    return hPersistence.createQuery(RuleEnforcement.class).field(RuleEnforcementId.uuid).equal(uuid).get();
  }
}
