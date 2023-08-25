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
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
public class RuleEnforcementDAO {
  @Inject private HPersistence hPersistence;
  @Inject private MongoTemplate mongoTemplate;

  public boolean save(RuleEnforcement ruleEnforcement) {
    RuleEnforcement savedRuleEnforcement = mongoTemplate.save(ruleEnforcement);
    log.info("created: {}", savedRuleEnforcement);
    return savedRuleEnforcement != null;
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
    Criteria criteria = Criteria.where(RuleEnforcementId.accountId)
                            .is(rule.getAccountId())
                            .and(RuleEnforcementId.uuid)
                            .is(rule.getUuid());
    org.springframework.data.mongodb.core.query.Query query =
        new org.springframework.data.mongodb.core.query.Query(criteria);
    Update update = new Update();
    update.set(RuleEnforcementId.runCount, rule.getRunCount());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    mongoTemplate.findAndModify(query, update, options, RuleEnforcement.class);
  }

  public RuleEnforcement update(RuleEnforcement ruleEnforcement) {
    RuleEnforcement existingRuleEnforcement =
        fetchById(ruleEnforcement.getAccountId(), ruleEnforcement.getUuid(), false);
    if (ruleEnforcement.getName() != null) {
      if (fetchByName(ruleEnforcement.getAccountId(), ruleEnforcement.getName(), true) != null) {
        throw new InvalidRequestException("Rule Enforcement with the given name already exits");
      }
      existingRuleEnforcement.setName(ruleEnforcement.getName());
    }
    if (ruleEnforcement.getRuleIds() != null) {
      existingRuleEnforcement.setRuleIds(ruleEnforcement.getRuleIds());
    }
    if (ruleEnforcement.getRuleSetIDs() != null) {
      existingRuleEnforcement.setRuleSetIDs(ruleEnforcement.getRuleSetIDs());
    }
    if (ruleEnforcement.getExecutionSchedule() != null) {
      existingRuleEnforcement.setExecutionSchedule(ruleEnforcement.getExecutionSchedule());
    }
    if (ruleEnforcement.getExecutionTimezone() != null) {
      existingRuleEnforcement.setExecutionTimezone(ruleEnforcement.getExecutionTimezone());
    }
    if (ruleEnforcement.getTargetAccounts() != null) {
      existingRuleEnforcement.setTargetAccounts(ruleEnforcement.getTargetAccounts());
    }
    if (ruleEnforcement.getTargetRegions() != null) {
      existingRuleEnforcement.setTargetRegions(ruleEnforcement.getTargetRegions());
    }
    if (ruleEnforcement.getIsDryRun() != null) {
      existingRuleEnforcement.setIsDryRun(ruleEnforcement.getIsDryRun());
    }
    if (ruleEnforcement.getIsEnabled() != null) {
      existingRuleEnforcement.setIsEnabled(ruleEnforcement.getIsEnabled());
    }

    RuleEnforcement updatedRuleEnforcement = mongoTemplate.save(existingRuleEnforcement);
    log.info("Updated rule: {}", updatedRuleEnforcement.getUuid());

    return updatedRuleEnforcement;
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
