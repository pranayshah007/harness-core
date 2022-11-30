/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.entities.RuleSet.RuleSetId;
import io.harness.ccm.views.helper.RuleSetFilter;
import io.harness.ccm.views.helper.RuleSetList;
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
public class RuleSetDAO {
  @Inject private HPersistence hPersistence;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  public boolean save(RuleSet rulesSet) {
    log.info("created: {}", hPersistence.save(rulesSet));
    return hPersistence.save(rulesSet) != null;
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
    Query<RuleSet> query = hPersistence.createQuery(RuleSet.class)
                               .field(RuleSetId.accountId)
                               .equal(accountId)
                               .field(RuleSetId.uuid)
                               .equal(ruleSet.getUuid());
    UpdateOperations<RuleSet> updateOperations = hPersistence.createUpdateOperations(RuleSet.class);
    if (ruleSet.getName() != null) {
      if (fetchByName(ruleSet.getAccountId(), ruleSet.getName(), true) != null) {
        throw new InvalidRequestException("Rule Set with this name already exits");
      }
      updateOperations.set(RuleSetId.name, ruleSet.getName());
    }
    if (ruleSet.getTags() != null) {
      updateOperations.set(RuleSetId.tags, ruleSet.getTags());
    }
    if (ruleSet.getRulesIdentifier() != null) {
      updateOperations.set(RuleSetId.rulesIdentifier, ruleSet.getRulesIdentifier());
    }
    if (ruleSet.getDescription() != null) {
      updateOperations.set(RuleSetId.description, ruleSet.getDescription());
    }

    hPersistence.update(query, updateOperations);
    log.info("Updated rules: {} {}", query, hPersistence.update(query, updateOperations));
    return query.asList().get(0);
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

  public RuleSetList list(String accountId, RuleSetFilter ruleSet) {
    RuleSetList ruleSetList = RuleSetList.builder().build();
    Query<RuleSet> ruleSets = hPersistence.createQuery(RuleSet.class)
                                  .field(RuleSetId.accountId)
                                  .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
                                  .order(Sort.ascending(RuleSetId.name));

    if (ruleSet.getRuleSetIds() != null) {
      ruleSets.field(RuleSetId.uuid).in(ruleSet.getRuleSetIds());
    }
    if (ruleSet.getCloudProvider() != null) {
      ruleSets.field(RuleSetId.cloudProvider).equal(ruleSet.getCloudProvider());
    }
    if (ruleSet.getRuleSetIds() != null) {
      ruleSets.field(RuleSetId.rulesIdentifier).in(ruleSet.getRuleSetIds());
    }
    if (ruleSet.getIsOOTB() != null) {
      if (ruleSet.getIsOOTB()) {
        log.info("Adding all OOTB rules");
        ruleSets.field(RuleSetId.accountId).equal(GLOBAL_ACCOUNT_ID);
      } else {
        ruleSets.field(RuleSetId.accountId).equal(accountId);
      }
    }
    if (ruleSet.getSearch() != null) {
      ruleSets.field(RuleSetId.name).containsIgnoreCase(ruleSet.getSearch());
    }
    ruleSetList.setTotalItems(ruleSets.asList().size());
    ruleSetList.setRuleSet(
        ruleSets.limit(ruleSet.getLimit()).offset(ruleSet.getOffset()).order(Sort.descending(RuleSetId.name)).asList());
    return ruleSetList;
  }
}
