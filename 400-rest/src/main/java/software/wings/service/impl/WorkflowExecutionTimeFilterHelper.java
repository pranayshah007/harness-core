/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.FeatureName.ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS;
import static io.harness.beans.SearchFilter.Operator;
import static io.harness.beans.SearchFilter.Operator.LT;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.DatastoreImpl;
import org.mongodb.morphia.mapping.Mapper;

@Singleton
@Slf4j
public class WorkflowExecutionTimeFilterHelper {
  @Inject FeatureFlagService featureFlagService;
  @Inject HPersistence hPersistence;
  private final Long THREE_MONTHS_MILLIS = 7889400000L;
  private final Long FOUR_MONTHS_MILLIS = 10519200000L;

  public void updatePageRequestForTimeFilter(PageRequest<WorkflowExecution> pageRequest, String accountId) {
    if (!featureFlagService.isEnabled(ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS, accountId)) {
      return;
    }
    PageRequest<WorkflowExecution> copiedPageRequest = populatePageRequestFilters(pageRequest);
    final List<SearchFilter> searchFiltersForTime =
        emptyIfNull(copiedPageRequest.getFilters())
            .stream()
            .filter(searchFilter -> searchFilter.getFieldName().equals(WorkflowExecutionKeys.createdAt))
            .collect(Collectors.toList());

    if (isEmpty(searchFiltersForTime)) {
      log.info("Automatically adding search filter of 3 months");
      Object[] threeMonthsOldTime = new Object[] {System.currentTimeMillis() - THREE_MONTHS_MILLIS};
      pageRequest.addFilter(WorkflowExecutionKeys.createdAt, Operator.GT, threeMonthsOldTime);
      return;
    }

    if (searchFiltersForTime.size() > 2) {
      throw new InvalidRequestException("Cannot have more than two time filters.");
    }

    if (searchFiltersForTime.size() == 1) {
      if (searchFiltersForTime.get(0).getOp().equals(Operator.GT)) {
        if (!checkTimeNotGreaterThanFourMonths(searchFiltersForTime)) {
          final Long timeValueFromFilter = getTimeValueFromFilter(searchFiltersForTime.get(0).getFieldValues()[0]);
          if (timeValueFromFilter != 0) {
            pageRequest.addFilter(
                WorkflowExecutionKeys.createdAt, Operator.LT, timeValueFromFilter + THREE_MONTHS_MILLIS);
          }
        }
      } else if (searchFiltersForTime.get(0).getOp().equals(LT)) {
        final Long timeValueFromFilter = getTimeValueFromFilter(searchFiltersForTime.get(0).getFieldValues()[0]);
        if (timeValueFromFilter != 0) {
          pageRequest.addFilter(
              WorkflowExecutionKeys.createdAt, Operator.GT, timeValueFromFilter - THREE_MONTHS_MILLIS);
        }
      }
      return;
    }

    if (searchFiltersForTime.size() == 2) {
      SearchFilter lowerBoundOfTime, upperBoundOfTime;
      if (searchFiltersForTime.get(0).getOp().equals(LT)) {
        lowerBoundOfTime = searchFiltersForTime.get(1);
        upperBoundOfTime = searchFiltersForTime.get(0);
      } else {
        lowerBoundOfTime = searchFiltersForTime.get(0);
        upperBoundOfTime = searchFiltersForTime.get(1);
      }
      final Long lowerTime = getTimeValueFromFilter(lowerBoundOfTime.getFieldValues()[0]);
      final Long upperTime = getTimeValueFromFilter(upperBoundOfTime.getFieldValues()[0]);
      if (upperTime - lowerTime > FOUR_MONTHS_MILLIS) {
        throw new InvalidRequestException("Time range can be maximum  of three months");
      }
    }
  }

  @VisibleForTesting
  PageRequest<WorkflowExecution> populatePageRequestFilters(PageRequest<WorkflowExecution> pageRequest) {
    Mapper mapper = ((DatastoreImpl) hPersistence.getDatastore(WorkflowExecution.class)).getMapper();
    PageRequest<WorkflowExecution> copiedPageRequest = pageRequest.copy();
    copiedPageRequest.populateFilters(
        copiedPageRequest.getUriInfo().getQueryParameters(), mapper.getMappedClass(WorkflowExecution.class), mapper);
    return copiedPageRequest;
  }

  private boolean checkTimeNotGreaterThanFourMonths(List<SearchFilter> searchFiltersForTime) {
    Object timeFilter = searchFiltersForTime.get(0).getFieldValues()[0];
    final Long timeInFilter = getTimeValueFromFilter(timeFilter);
    return timeInFilter >= System.currentTimeMillis() - FOUR_MONTHS_MILLIS;
    // returning default as true
  }

  private Long getTimeValueFromFilter(Object timeFilter) {
    if (timeFilter instanceof Integer || timeFilter instanceof Long || timeFilter instanceof String) {
      return Long.valueOf(String.valueOf(timeFilter));
    } else {
      return 0L;
    }
  }
}
