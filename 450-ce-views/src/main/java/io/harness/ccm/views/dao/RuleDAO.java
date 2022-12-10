/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.Rule.RuleId;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.RuleList;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
public class RuleDAO {
  @Inject private HPersistence hPersistence;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  public boolean save(Rule rule) {
    log.info("created: {}", hPersistence.save(rule));
    return hPersistence.save(rule) != null;
  }

  public boolean delete(String accountId, String uuid) {
    Query<Rule> query =
        hPersistence.createQuery(Rule.class).field(RuleId.accountId).equal(accountId).field(RuleId.uuid).equal(uuid);
    log.info("deleted rule: {}", uuid);
    return hPersistence.delete(query);
  }

  public RuleList list(GovernanceRuleFilter governancePolicyFilter) {
    RuleList ruleList = RuleList.builder().build();
    Query<Rule> rules = hPersistence.createQuery(Rule.class)
                            .field(RuleId.accountId)
                            .in(Arrays.asList(governancePolicyFilter.getAccountId(), GLOBAL_ACCOUNT_ID))
                            .order(Sort.ascending(RuleId.name));

    if (governancePolicyFilter.getCloudProvider() != null) {
      rules.field(RuleId.cloudProvider).equal(governancePolicyFilter.getCloudProvider());
    }
    if (governancePolicyFilter.getPolicyIds() != null) {
      rules.field(RuleId.uuid).in(governancePolicyFilter.getPolicyIds());
    }
    if (governancePolicyFilter.getIsOOTB() != null) {
      if (governancePolicyFilter.getIsOOTB()) {
        rules.field(RuleId.accountId).equal(GLOBAL_ACCOUNT_ID);
      } else {
        rules.field(RuleId.accountId).equal(governancePolicyFilter.getAccountId());
      }
    }
    if (governancePolicyFilter.getSearch() != null) {
      rules.field(RuleId.name).containsIgnoreCase(governancePolicyFilter.getSearch());
    }
    ruleList.setTotalItems(rules.asList().size());

    ruleList.setRule(rules.limit(governancePolicyFilter.getLimit())
                         .offset(governancePolicyFilter.getOffset())
                         .order(Sort.descending(RuleId.name))
                         .asList());
    return ruleList;
  }

  public Rule fetchByName(String accountId, String name, boolean create) {
    try {
      List<Rule> rules = hPersistence.createQuery(Rule.class)
                             .field(RuleId.accountId)
                             .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
                             .field(RuleId.name)
                             .equal(name)
                             .asList();
      return rules.get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such rule exists,{} accountId {} name {}", e, accountId, name);
      if (create) {
        return null;
      }
      throw new InvalidRequestException("No such rule exists");
    }
  }

  public Rule fetchById(String accountId, String uuid, boolean create) {
    try {
      List<Rule> rules = hPersistence.createQuery(Rule.class)
                             .field(RuleId.accountId)
                             .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
                             .field(RuleId.uuid)
                             .equal(uuid)
                             .asList();
      return rules.get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such rule exists,{} accountId {} name {}", e, accountId, uuid);
      if (create) {
        return null;
      }
      throw new InvalidRequestException("No such rule exists");
    }
  }

  public Rule update(Rule rule, String accountId) {
    Query<Rule> query = hPersistence.createQuery(Rule.class)
                            .field(RuleId.accountId)
                            .equal(accountId)
                            .field(RuleId.uuid)
                            .equal(rule.getUuid());
    UpdateOperations<Rule> updateOperations = hPersistence.createUpdateOperations(Rule.class);

    if (rule.getName() != null) {
      if (fetchByName(accountId, rule.getName(), false) != null) {
        throw new InvalidRequestException("Rule with given name already exits");
      }
      updateOperations.set(RuleId.name, rule.getName());
    }
    if (rule.getDescription() != null) {
      updateOperations.set(RuleId.description, rule.getDescription());
    }
    if (rule.getRulesYaml() != null) {
      updateOperations.set(RuleId.rulesYaml, rule.getRulesYaml());
    }
    if (rule.getTags() != null) {
      updateOperations.set(RuleId.tags, rule.getTags());
    }
    log.info("Updated rule: {} {} {}", rule.getUuid(), hPersistence.update(query, updateOperations), query);
    hPersistence.update(query, updateOperations);
    return query.asList().get(0);
  }

  public List<Rule> check(String accountId, List<String> rulesIdentifier) {
    List<Rule> rules = hPersistence.createQuery(Rule.class)
                           .field(RuleId.accountId)
                           .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
                           .field(RuleId.uuid)
                           .in(rulesIdentifier)
                           .asList();
    log.info("{} ", rules);
    return rules;
  }
}