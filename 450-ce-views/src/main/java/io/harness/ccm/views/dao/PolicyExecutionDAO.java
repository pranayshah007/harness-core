/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.ccm.commons.entities.CCMTimeFilter;
import static io.harness.ccm.commons.utils.TimeUtils.toOffsetDateTime;
import io.harness.ccm.views.entities.PolicyExecution;
import io.harness.ccm.views.service.PolicyExecutionService;
import io.harness.ccm.views.entities.PolicyExecution.PolicyExecutionKeys;
import io.harness.ccm.views.entities.PolicyExecutionFilter;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

import static java.util.stream.Collectors.toList;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
@Singleton
public class PolicyExecutionDAO {
  @Inject private HPersistence hPersistence;
  private PolicyExecutionService policyExecutionService;

  public boolean save(PolicyExecution policyExecution) {
    log.info("created: {}", hPersistence.save(policyExecution));
    return hPersistence.save(policyExecution) != null;
  }

  public List<PolicyExecution> list(String accountId) {
    return hPersistence.createQuery(PolicyExecution.class).
            field(PolicyExecutionKeys.accountId).
            equal(accountId)
            .asList();
  }
  public PolicyExecution get(String accountId, String uuid) {
    log.info("accountId: {}, uuid: {}", accountId, uuid);
    return hPersistence.createQuery(PolicyExecution.class, excludeValidate)
            .field(PolicyExecution.PolicyExecutionKeys.uuid)
            .equal(uuid)
            .get();
  }

  public List<PolicyExecution> filterExecution(PolicyExecutionFilter policyExecutionFilter) {
    Query<PolicyExecution> query= hPersistence.createQuery(PolicyExecution.class).field(PolicyExecutionKeys.accountId).equal(policyExecutionFilter.getAccountId());
    log.info("Added accountId filter");
    if(policyExecutionFilter.getAccountName()!=null)
    {
      query.field(PolicyExecutionKeys.targetAccounts).in(policyExecutionFilter.getAccountName());
      log.info("Added target account filter");
    }
    if(policyExecutionFilter.getPolicyName()!=null)
    {
      query.field(PolicyExecutionKeys.policyIdentifier).in(policyExecutionFilter.getAccountName());
      log.info("Added policy Identifier filter");
    }
    if(policyExecutionFilter.getRegion()!=null)
    {
      query.field(PolicyExecutionKeys.targetRegions).in(policyExecutionFilter.getRegion());
      log.info("Added  target Regions filter");
    }
    if(policyExecutionFilter.getCloudProvider()!=null)
    {
      query.field(PolicyExecutionKeys.cloudProvider).equal(policyExecutionFilter.getCloudProvider());
      log.info("Added  cloud Provider filter");
    }
    if(policyExecutionFilter.getTime()!=null) {
      for (CCMTimeFilter time : policyExecutionFilter.getTime()) {
        log.info("Added time filter {}",time.getOperator() );
        switch (time.getOperator()) {
          case AFTER :
            query.field(PolicyExecutionKeys.lastUpdatedAt).greaterThanOrEq(time.getTimestamp());
          case BEFORE :
            query.field(PolicyExecutionKeys.lastUpdatedAt).lessThanOrEq(time.getTimestamp());
          default:
            throw new InvalidRequestException("Operator not supported not supported for time fields");
        }
      }
    }
    log.info("list size {}", query.asList().size());
    log.info("stream size {}", query.asList().stream().skip(policyExecutionFilter.getOffset())
            .limit(policyExecutionFilter.getLIMIT()).collect(toList()).size());

//    return query.asList().stream().skip(policyExecutionFilter.getOffset())
//            .limit(policyExecutionFilter.getLIMIT()).collect(toList());
    return query.asList();
  }

  }


