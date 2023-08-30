/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;
import static io.harness.beans.FeatureName.ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS;
import static io.harness.beans.FeatureName.SPG_ENFORCE_TIME_RANGE_DEPLOYMENTS_WITHOUT_APP_ID;
import static io.harness.beans.SearchFilter.Operator;
import static io.harness.beans.SearchFilter.Operator.GT;
import static io.harness.beans.SearchFilter.Operator.LT;
import static io.harness.beans.SearchFilter.SearchFilterBuilder;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
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
import dev.morphia.DatastoreImpl;
import dev.morphia.mapping.Mapper;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@Slf4j
public class WorkflowExecutionTimeFilterHelper {
  private static final Duration MAXIMUM_DURATION_WITHOUT_APPID = Duration.ofDays(30);
  private static final Duration MAXIMUM_DURATION_WITH_APPID = Duration.ofDays(90);
  private static final Duration FOUR_MONTHS_DURATION = Duration.ofDays(120);
  @Inject FeatureFlagService featureFlagService;
  @Inject HPersistence hPersistence;

  public void updatePageRequestForTimeFilter(PageRequest<WorkflowExecution> pageRequest, String accountId) {
    if (!featureFlagService.isEnabled(ENABLE_DEFAULT_TIMEFRAME_IN_DEPLOYMENTS, accountId)) {
      return;
    }
    PageRequest<WorkflowExecution> copiedPageRequest = populatePageRequestFilters(pageRequest);
    Optional<SearchFilter> appIdFilterOpt =
        copiedPageRequest.getFilters()
            .stream()
            .filter(filter -> WorkflowExecutionKeys.appId.equals(filter.getFieldName()))
            .findFirst();
    final List<SearchFilter> searchFiltersForTime =
        emptyIfNull(copiedPageRequest.getFilters())
            .stream()
            .filter(searchFilter -> searchFilter.getFieldName().equals(WorkflowExecutionKeys.createdAt))
            .collect(Collectors.toList());

    if (isEmpty(searchFiltersForTime)) {
      if (appIdFilterOpt.isPresent()) {
        log.info("Automatically adding search filter of 3 months");
        Object[] threeMonthsOldTime =
            new Object[] {System.currentTimeMillis() - MAXIMUM_DURATION_WITH_APPID.toMillis()};
        pageRequest.addFilter(WorkflowExecutionKeys.createdAt, Operator.GE, threeMonthsOldTime);
        return;
      }
      log.info("Automatically adding search filter of 1 month");
      Object[] oneMonthOldTime = new Object[] {System.currentTimeMillis() - MAXIMUM_DURATION_WITHOUT_APPID.toMillis()};
      pageRequest.addFilter(WorkflowExecutionKeys.createdAt, Operator.GE, oneMonthOldTime);
      return;
    }

    if (searchFiltersForTime.size() > 2) {
      throw new InvalidRequestException("Cannot have more than two time filters.");
    }

    Map<String, Long> createdAtMap = new HashMap<>();

    searchFiltersForTime.forEach(filter -> {
      switch (filter.getOp()) {
        case GE:
        case GT:
          createdAtMap.put("startedAt", getTimeValueFromFilter(String.valueOf(filter.getFieldValues()[0])));
          break;
        case LT:
        case LT_EQ:
          createdAtMap.put("endAt", getTimeValueFromFilter(String.valueOf(filter.getFieldValues()[0])));
          break;
        default:
          break;
      }
    });

    Instant startedAt = null;
    Instant endAt = null;
    if (createdAtMap.containsKey("startedAt")) {
      Long epochMillis = createdAtMap.get("startedAt");
      startedAt = Instant.ofEpochMilli(epochMillis);
    }
    if (createdAtMap.containsKey("endAt")) {
      Long epochMillis = createdAtMap.get("endAt");
      endAt = Instant.ofEpochMilli(epochMillis);
    }

    if (createdAtMap.keySet().size() == 2) {
      Duration duration = Duration.between(startedAt, endAt);
      if (!appIdFilterOpt.isPresent() && duration.compareTo(MAXIMUM_DURATION_WITHOUT_APPID) > 0
          && featureFlagService.isEnabled(SPG_ENFORCE_TIME_RANGE_DEPLOYMENTS_WITHOUT_APP_ID, accountId)) {
        throw new InvalidRequestException("Maximum time range without appId is 1 month.");
      } else if (duration.compareTo(FOUR_MONTHS_DURATION) > 0) {
        throw new InvalidRequestException("Time range can be maximum of three months.");
      }
    } else if (createdAtMap.keySet().size() == 1) {
      if (endAt != null) {
        startedAt = appIdFilterOpt.isPresent() ? endAt.minus(MAXIMUM_DURATION_WITH_APPID)
                                               : endAt.minus(MAXIMUM_DURATION_WITHOUT_APPID);
      } else {
        endAt = appIdFilterOpt.isPresent() ? startedAt.plus(MAXIMUM_DURATION_WITH_APPID)
                                           : startedAt.plus(MAXIMUM_DURATION_WITHOUT_APPID);
      }
    }

    if (endAt != null && !createdAtMap.containsKey("endAt")) {
      final SearchFilterBuilder startFilterBuilder = SearchFilter.builder();
      startFilterBuilder.fieldName(WorkflowExecutionKeys.createdAt)
          .fieldValues(new Object[] {endAt.toEpochMilli()})
          .op(LT);
      pageRequest.addFilter(startFilterBuilder.build());
    }

    if (startedAt != null && !createdAtMap.containsKey("startedAt")) {
      final SearchFilterBuilder endFilterBuilder = SearchFilter.builder();
      endFilterBuilder.fieldName(WorkflowExecutionKeys.createdAt)
          .fieldValues(new Object[] {startedAt.toEpochMilli()})
          .op(GT);
      pageRequest.addFilter(endFilterBuilder.build());
    }
  }

  @VisibleForTesting
  PageRequest<WorkflowExecution> populatePageRequestFilters(PageRequest<WorkflowExecution> pageRequest) {
    Mapper mapper = ((DatastoreImpl) hPersistence.getDatastore(WorkflowExecution.class)).getMapper();
    PageRequest<WorkflowExecution> copiedPageRequest = pageRequest.deepCopy();
    copiedPageRequest.populateFilters(
        copiedPageRequest.getUriInfo().getQueryParameters(), mapper.getMappedClass(WorkflowExecution.class), mapper);
    return copiedPageRequest;
  }

  private Long getTimeValueFromFilter(Object timeFilter) {
    if (timeFilter instanceof Integer || timeFilter instanceof Long || timeFilter instanceof String) {
      return Long.valueOf(String.valueOf(timeFilter));
    } else {
      return 0L;
    }
  }
}
