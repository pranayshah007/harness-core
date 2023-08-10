/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.commons.entities.CCMSort;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.Rule.RuleId;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.helper.RuleList;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.model.Collation;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@Singleton
public class RuleDAO {
  @Inject private HPersistence hPersistence;
  @Inject private MongoTemplate mongoTemplate;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private static final String LOCALE_EN = "en";

  public boolean save(Rule rule) {
    Rule savedRule = mongoTemplate.save(rule);
    log.info("created: {}", savedRule);

    // We are creating a OOTB rule, we explicitly update createdBy, updatedBy
    if (rule.getAccountId().equals(GLOBAL_ACCOUNT_ID)) {
      Criteria criteria =
          Criteria.where(RuleId.accountId).is(savedRule.getAccountId()).and(RuleId.uuid).is(savedRule.getUuid());
      org.springframework.data.mongodb.core.query.Query query =
          new org.springframework.data.mongodb.core.query.Query(criteria);
      Update update = new Update();
      update.set(RuleId.createdBy, null);
      update.set(RuleId.lastUpdatedBy, null);
      FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
      Rule updatedRule = mongoTemplate.findAndModify(query, update, options, Rule.class);
      log.info("Updated rule {}", updatedRule);
      return updatedRule != null;
    }
    return savedRule != null;
  }

  public boolean delete(String accountId, String uuid) {
    Query<Rule> query =
        hPersistence.createQuery(Rule.class).field(RuleId.accountId).equal(accountId).field(RuleId.uuid).equal(uuid);
    log.info("deleted rule: {}", uuid);
    return hPersistence.delete(query);
  }
  public List<Rule> forRecommendation(RuleCloudProviderType ruleCloudProviderType, String accountId) {
    log.info("creating a query");
    Query<Rule> rules = hPersistence.createQuery(Rule.class)
                            .field(RuleId.accountId)
                            .in(List.of(GLOBAL_ACCOUNT_ID, accountId))
                            .field(RuleId.forRecommendation)
                            .equal(true)
                            .field(RuleId.cloudProvider)
                            .equal(ruleCloudProviderType);
    log.info("Rule List for cloud provider {} forRecommendation: {}", ruleCloudProviderType.name(), rules.asList());
    return rules.asList();
  }
  public RuleList list(GovernanceRuleFilter governancePolicyFilter) {
    RuleList ruleList = RuleList.builder().build();
    Query<Rule> rules = hPersistence.createQuery(Rule.class)
                            .field(RuleId.accountId)
                            .in(Arrays.asList(governancePolicyFilter.getAccountId(), GLOBAL_ACCOUNT_ID));

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
    if (governancePolicyFilter.getOrderBy() != null) {
      for (CCMSort sort : governancePolicyFilter.getOrderBy()) {
        switch (sort.getField()) {
          case RULE_NAME:
            sortByRuleName(rules, sort.getOrder());
            break;
          default:
            throw new InvalidRequestException("Sort field " + sort.getField() + " is not supported");
        }
      }
    }
    final FindOptions options = new FindOptions();
    options.collation(Collation.builder().locale(LOCALE_EN).build());
    options.limit(governancePolicyFilter.getLimit());
    options.skip(governancePolicyFilter.getOffset());
    ruleList.setTotalItems(rules.asList().size());

    ruleList.setRules(rules.asList(options));
    return ruleList;
  }

  public Query<Rule> sortByRuleName(Query<Rule> rules, CCMSortOrder order) {
    switch (order) {
      case ASCENDING:
        return rules.order(Sort.ascending(RuleId.name));
      case DESCENDING:
        return rules.order(Sort.descending(RuleId.name));
      default:
        throw new InvalidRequestException("Operator not supported not supported for time fields");
    }
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
    // We are updating a OOTB rule, we don't want to populate createdBy, updatedBy
    if (accountId.equals(GLOBAL_ACCOUNT_ID)) {
      Criteria criteria = Criteria.where(RuleId.accountId).is(accountId).and(RuleId.uuid).is(rule.getUuid());
      org.springframework.data.mongodb.core.query.Query query =
          new org.springframework.data.mongodb.core.query.Query(criteria);
      Update update = new Update();
      update.set(RuleId.createdBy, null);
      update.set(RuleId.lastUpdatedBy, null);
      if (rule.getName() != null) {
        if (fetchByName(accountId, rule.getName(), true) != null) {
          throw new InvalidRequestException("Rule with given name already exits");
        }
        update.set(RuleId.name, rule.getName());
      }
      if (rule.getDescription() != null) {
        update.set(RuleId.description, rule.getDescription());
      }
      if (rule.getRulesYaml() != null) {
        update.set(RuleId.rulesYaml, rule.getRulesYaml());
      }
      if (rule.getTags() != null) {
        update.set(RuleId.tags, rule.getTags());
      }
      if (rule.getForRecommendation() != null) {
        update.set(RuleId.forRecommendation, rule.getForRecommendation());
      }
      if (rule.getResourceType() != null) {
        update.set(RuleId.resourceType, rule.getResourceType());
      }

      FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
      Rule updatedRule = mongoTemplate.findAndModify(query, update, options, Rule.class);
      log.info("Updated rule {}", updatedRule);
      return updatedRule;
    }

    Rule existingRule = fetchById(accountId, rule.getUuid(), false);
    if (rule.getName() != null) {
      if (fetchByName(accountId, rule.getName(), true) != null) {
        throw new InvalidRequestException("Rule with given name already exits");
      }
      existingRule.setName(rule.getName());
    }
    if (rule.getDescription() != null) {
      existingRule.setDescription(rule.getDescription());
    }
    if (rule.getRulesYaml() != null) {
      existingRule.setRulesYaml(rule.getRulesYaml());
    }
    if (rule.getTags() != null) {
      existingRule.setTags(rule.getTags());
    }
    if (rule.getForRecommendation() != null) {
      existingRule.setForRecommendation(rule.getForRecommendation());
    }
    if (rule.getResourceType() != null) {
      existingRule.setResourceType(rule.getResourceType());
    }
    Rule savedRule = mongoTemplate.save(existingRule);
    log.info("Updated rule: {} {}", rule.getUuid(), savedRule);
    return savedRule;
  }

  public List<Rule> check(String accountId, List<String> rulesIdentifier) {
    return hPersistence.createQuery(Rule.class)
        .field(RuleId.accountId)
        .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
        .field(RuleId.uuid)
        .in(rulesIdentifier)
        .asList();
  }

  public List<Rule> validateCloudProvider(
      String accountId, Set<String> rulesIdentifier, RuleCloudProviderType ruleCloudProviderType) {
    return hPersistence.createQuery(Rule.class)
        .project(RuleId.uuid, true)
        .field(RuleId.accountId)
        .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
        .field(RuleId.uuid)
        .in(rulesIdentifier)
        .field(RuleId.cloudProvider)
        .equal(ruleCloudProviderType)
        .asList();
  }
}