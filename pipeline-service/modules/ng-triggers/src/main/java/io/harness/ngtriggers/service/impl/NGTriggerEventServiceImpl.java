/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.repositories.spring.TriggerEventHistoryRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NGTriggerEventServiceImpl implements NGTriggerEventsService {
  private TriggerEventHistoryRepository triggerEventHistoryRepository;

  @Override
  public Criteria formCriteria(String accountId, String orgId, String projectId, String targetIdentifier,
      String identifier, String searchTerm, List<ExecutionStatus> statusList) {
    Criteria criteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(accountId)) {
      criteria.and(TriggerEventHistoryKeys.accountId).is(accountId);
    }
    if (EmptyPredicate.isNotEmpty(orgId)) {
      criteria.and(TriggerEventHistoryKeys.orgIdentifier).is(orgId);
    }
    if (EmptyPredicate.isNotEmpty(projectId)) {
      criteria.and(TriggerEventHistoryKeys.projectIdentifier).is(projectId);
    }
    if (EmptyPredicate.isNotEmpty(targetIdentifier)) {
      criteria.and(TriggerEventHistoryKeys.targetIdentifier).is(targetIdentifier);
    }
    if (EmptyPredicate.isNotEmpty(identifier)) {
      criteria.and(TriggerEventHistoryKeys.triggerIdentifier).is(identifier);
    }
    if (EmptyPredicate.isNotEmpty(statusList)) {
      criteria.and(TriggerEventHistoryKeys.finalStatus).in(statusList);
    }

    Criteria searchCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      try {
        searchCriteria.orOperator(where(TriggerEventHistoryKeys.triggerIdentifier)
                                      .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      } catch (PatternSyntaxException pex) {
        throw new InvalidRequestException(pex.getMessage() + " Use \\\\ for special character", pex);
      }
    }
    criteria.andOperator(searchCriteria);
    return criteria;
  }

  @Override
  public Page<TriggerEventHistory> getEventHistory(Criteria criteria, Pageable pageable) {
    return triggerEventHistoryRepository.findAll(criteria, pageable);
  }
}
