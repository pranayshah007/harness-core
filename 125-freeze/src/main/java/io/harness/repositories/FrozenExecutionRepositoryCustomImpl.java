/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.freeze.entity.FrozenExecution;
import io.harness.freeze.entity.FrozenExecution.FrozenExecutionKeys;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class FrozenExecutionRepositoryCustomImpl implements FrozenExecutionRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Optional<FrozenExecution> findByAccountIdAndOrgIdAndProjectIdAndPlanExecutionId(
      String accountId, String orgId, String projectId, String planExecutionId) {
    final Criteria criteria = Criteria.where(FrozenExecutionKeys.accountId)
                                  .is(accountId)
                                  .and(FrozenExecutionKeys.orgId)
                                  .is(orgId)
                                  .and(FrozenExecutionKeys.projectId)
                                  .is(projectId)
                                  .and(FrozenExecutionKeys.planExecutionId)
                                  .is(planExecutionId);
    FrozenExecution eg = mongoTemplate.findOne(new Query(criteria), FrozenExecution.class);
    return Optional.ofNullable(eg);
  }
}
