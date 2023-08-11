/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.commons.entities.CCMSort;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.entities.RuleSet.RuleSetId;
import io.harness.ccm.views.helper.RuleSetFilter;
import io.harness.ccm.views.helper.RuleSetList;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@Singleton
public class RuleSetDAO {
  @Inject private HPersistence hPersistence;
  @Inject private MongoTemplate mongoTemplate;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  public boolean save(RuleSet rulesSet) {
    RuleSet savedRuleSet = mongoTemplate.save(rulesSet);
    log.info("created: {}", savedRuleSet);
    return savedRuleSet != null;
  }

  public boolean deleteOOTB(String accountId, String uuid) {
    Query<RuleSet> query = hPersistence.createQuery(RuleSet.class)
                               .field(RuleSetId.accountId)
                               .equal(accountId)
                               .field(RuleSetId.uuid)
                               .equal(uuid);
    log.info("deleted rules: {}", uuid);
    return hPersistence.delete(query);
  }

  public boolean delete(String accountId, String uuid) {
    Query<RuleSet> query = hPersistence.createQuery(RuleSet.class)
                               .field(RuleSetId.accountId)
                               .equal(accountId)
                               .field(RuleSetId.uuid)
                               .equal(uuid);
    log.info("deleted rules: {}", uuid);
    return hPersistence.delete(query);
  }

  public RuleSet update(String accountId, RuleSet ruleSet) {
    RuleSet existingRuleSet = fetchById(accountId, ruleSet.getUuid(), false);
    if (ruleSet.getName() != null) {
      if (fetchByName(ruleSet.getAccountId(), ruleSet.getName(), true) != null) {
        throw new InvalidRequestException("Rule Set with this name already exits");
      }
      existingRuleSet.setName(ruleSet.getName());
    }
    if (ruleSet.getTags() != null) {
      existingRuleSet.setTags(ruleSet.getTags());
    }
    if (ruleSet.getRulesIdentifier() != null) {
      existingRuleSet.setRulesIdentifier(ruleSet.getRulesIdentifier());
    }
    if (ruleSet.getDescription() != null) {
      existingRuleSet.setDescription(ruleSet.getDescription());
    }

    RuleSet savedRuleSet = mongoTemplate.save(existingRuleSet);
    log.info("Updated rules: {}", savedRuleSet);
    return savedRuleSet;
  }

  public RuleSet fetchByName(String accountId, String name, boolean create) {
    try {
      List<RuleSet> ruleSets = hPersistence.createQuery(RuleSet.class)
                                   .field(RuleSetId.accountId)
                                   .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
                                   .field(RuleSetId.name)
                                   .equal(name)
                                   .asList();
      return ruleSets.get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such rules pack exists,{} accountId{} name {} {}", e, accountId, name, create);
      if (create) {
        log.info("returning null");
        return null;
      }
      throw new InvalidRequestException("No such rules pack exists");
    }
  }

  public RuleSet fetchById(String accountId, String uuid, boolean create) {
    try {
      List<RuleSet> ruleSets = hPersistence.createQuery(RuleSet.class)
                                   .field(RuleSetId.accountId)
                                   .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
                                   .field(RuleSetId.uuid)
                                   .equal(uuid)
                                   .asList();
      return ruleSets.get(0);
    } catch (IndexOutOfBoundsException e) {
      log.error("No such rules pack exists,{} accountId{} name {} {}", e, accountId, uuid, create);
      if (create) {
        log.info("returning null");
        return null;
      }
      throw new InvalidRequestException("No such rules pack exists");
    }
  }

  public List<RuleSet> check(String accountId, List<String> rulesPackIdentifier) {
    List<RuleSet> ruleSets = hPersistence.createQuery(RuleSet.class)
                                 .field(RuleSetId.accountId)
                                 .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
                                 .field(RuleSetId.uuid)
                                 .in(rulesPackIdentifier)
                                 .asList();
    log.info("{} ", ruleSets);
    return ruleSets;
  }

  public List<RuleSet> listPacks(String accountId, List<String> packIds) {
    List<RuleSet> ruleSets = hPersistence.createQuery(RuleSet.class)
                                 .field(RuleSetId.accountId)
                                 .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
                                 .field(RuleSetId.uuid)
                                 .in(packIds)
                                 .order(Sort.ascending(RuleSetId.name))
                                 .asList();
    log.info("list size {}", ruleSets.size());
    return ruleSets;
  }

  public RuleSetList list(String accountId, RuleSetFilter ruleSetFilter) {
    RuleSetList ruleSetList = RuleSetList.builder().build();
    Query<RuleSet> ruleSets = hPersistence.createQuery(RuleSet.class)
                                  .field(RuleSetId.accountId)
                                  .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID));

    if (ruleSetFilter.getRuleSetIds() != null) {
      ruleSets.field(RuleSetId.uuid).in(ruleSetFilter.getRuleSetIds());
    }
    if (ruleSetFilter.getCloudProvider() != null) {
      ruleSets.field(RuleSetId.cloudProvider).equal(ruleSetFilter.getCloudProvider());
    }
    if (ruleSetFilter.getRuleSetIds() != null) {
      ruleSets.field(RuleSetId.rulesIdentifier).in(ruleSetFilter.getRuleSetIds());
    }
    if (ruleSetFilter.getIsOOTB() != null) {
      if (ruleSetFilter.getIsOOTB()) {
        log.info("Adding all OOTB rules");
        ruleSets.field(RuleSetId.accountId).equal(GLOBAL_ACCOUNT_ID);
      } else {
        ruleSets.field(RuleSetId.accountId).equal(accountId);
      }
    }
    if (ruleSetFilter.getOrderBy() != null) {
      for (CCMSort sort : ruleSetFilter.getOrderBy()) {
        switch (sort.getField()) {
          case RULE_SET_NAME:
            sortByRuleSetName(ruleSets, sort.getOrder());
            break;
          default:
            throw new InvalidRequestException("Sort field " + sort.getField() + " is not supported");
        }
      }
    }
    if (ruleSetFilter.getSearch() != null) {
      ruleSets.field(RuleSetId.name).containsIgnoreCase(ruleSetFilter.getSearch());
    }
    ruleSetList.setTotalItems(ruleSets.asList().size());
    ruleSetList.setRuleSet(ruleSets.limit(ruleSetFilter.getLimit()).offset(ruleSetFilter.getOffset()).asList());
    return ruleSetList;
  }
  public Query<RuleSet> sortByRuleSetName(Query<RuleSet> rules, CCMSortOrder order) {
    switch (order) {
      case ASCENDING:
        return rules.order(Sort.ascending(RuleSetId.name));
      case DESCENDING:
        return rules.order(Sort.descending(RuleSetId.name));
      default:
        throw new InvalidRequestException("Operator not supported not supported for time fields");
    }
  }
}
