/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.ccm.views.entities.RuleExecution;
import io.harness.ccm.views.entities.RuleExecution.RuleExecutionKeys;
import io.harness.ccm.views.helper.RuleExecutionFilter;
import io.harness.ccm.views.helper.RuleExecutionList;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
@Singleton
public class RuleExecutionDAO {
  @Inject private HPersistence hPersistence;

  public String save(RuleExecution ruleExecution) {
    return hPersistence.save(ruleExecution);
  }

  public List<RuleExecution> list(String accountId) {
    return hPersistence.createQuery(RuleExecution.class).field(RuleExecutionKeys.accountId).equal(accountId).asList();
  }
  public RuleExecution get(String accountId, String uuid) {
    log.info("accountId: {}, uuid: {}", accountId, uuid);
    Query<RuleExecution> query = hPersistence.createQuery(RuleExecution.class, excludeValidate);
    query.field(RuleExecutionKeys.accountId).equal(accountId).field(RuleExecutionKeys.uuid).equal(uuid);
    log.info(query.toString());
    return query.get();
  }

  public RuleExecutionList filterExecution(RuleExecutionFilter ruleExecutionFilter) {
    RuleExecutionList ruleExecutionList = RuleExecutionList.builder().build();
    Query<RuleExecution> query = hPersistence.createQuery(RuleExecution.class)
                                     .field(RuleExecutionKeys.accountId)
                                     .equal(ruleExecutionFilter.getAccountId());
    log.info("Added accountId filter");
    if (ruleExecutionFilter.getTargetAccount() != null) {
      query.field(RuleExecutionKeys.targetAccount).equal(ruleExecutionFilter.getTargetAccount());
      log.info("Added target account filter");
    }
    if (ruleExecutionFilter.getRuleIds() != null) {
      query.field(RuleExecutionKeys.ruleIdentifier).in(ruleExecutionFilter.getRuleIds());
    }
    if (ruleExecutionFilter.getRuleSetIds() != null) {
      query.field(RuleExecutionKeys.rulePackIdentifier).in(ruleExecutionFilter.getRuleSetIds());
    }
    if (ruleExecutionFilter.getRuleEnforcementId() != null) {
      query.field(RuleExecutionKeys.uuid).in(ruleExecutionFilter.getRuleEnforcementId());
    }
    if (ruleExecutionFilter.getRegion() != null) {
      query.field(RuleExecutionKeys.targetRegions).in(ruleExecutionFilter.getRegion());
    }
    if (ruleExecutionFilter.getExecutionStatus() != null) {
      query.field(RuleExecutionKeys.executionStatus).equal(ruleExecutionFilter.getExecutionStatus());
    }
    if (ruleExecutionFilter.getCloudProvider() != null) {
      query.field(RuleExecutionKeys.cloudProvider).equal(ruleExecutionFilter.getCloudProvider());
    }
    if (ruleExecutionFilter.getTime() != null) {
      for (CCMTimeFilter time : ruleExecutionFilter.getTime()) {
        switch (time.getOperator()) {
          case AFTER:
            query.field(RuleExecutionKeys.lastUpdatedAt).greaterThanOrEq(time.getTimestamp());
            break;
          case BEFORE:
            query.field(RuleExecutionKeys.lastUpdatedAt).lessThanOrEq(time.getTimestamp());
            break;
          default:
            throw new InvalidRequestException("Operator not supported not supported for time fields");
        }
      }
    }
    ruleExecutionList.setTotalItems(query.asList().size());
    ruleExecutionList.setRuleExecution(query.limit(ruleExecutionFilter.getLimit())
                                           .offset(ruleExecutionFilter.getOffset())
                                           .order(Sort.descending(RuleExecutionKeys.lastUpdatedAt))
                                           .asList());

    return ruleExecutionList;
  }
}
