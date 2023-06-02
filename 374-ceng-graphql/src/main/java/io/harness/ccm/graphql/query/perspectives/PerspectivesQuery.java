/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.perspectives;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.rbac.CCMRbacPermissions.PERSPECTIVE_VIEW;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.graphql.core.perspectives.PerspectiveService;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveEntityStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFieldsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFilterData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveOverviewStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveTrendStats;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.graphql.utils.annotations.GraphQLApi;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.views.dto.PerspectiveTimeSeriesData;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewPreferences;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jooq.tools.StringUtils;

@Slf4j
@Singleton
@GraphQLApi
@OwnedBy(CE)
public class PerspectivesQuery {
  @Inject private GraphQLUtils graphQLUtils;
  @Inject private PerspectiveService perspectiveService;
  @Inject private CCMRbacHelper rbacHelper;

  @GraphQLQuery(name = "perspectiveTrendStats", description = "Trend stats for perspective")
  public PerspectiveTrendStats perspectiveTrendStats(
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveTrendStats(filters, groupBy, aggregateFunction, isClusterQuery, accountId);
  }

  @GraphQLQuery(name = "perspectiveForecastCost", description = "Forecast cost for perspective")
  public PerspectiveTrendStats perspectiveForecastCost(
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveForecastCost(filters, groupBy, aggregateFunction, isClusterQuery, accountId);
  }

  @GraphQLQuery(name = "perspectiveGrid", description = "Table for perspective")
  public PerspectiveEntityStatsData perspectiveGrid(
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "sortCriteria") List<QLCEViewSortCriteria> sortCriteria,
      @GraphQLArgument(name = "limit") Integer limit, @GraphQLArgument(name = "offset") Integer offset,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLArgument(name = "skipRoundOff") Boolean skipRoundOff,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveGrid(
        aggregateFunction, filters, groupBy, sortCriteria, limit, offset, isClusterQuery, skipRoundOff, accountId);
  }

  @GraphQLQuery(name = "perspectiveFilters", description = "Filter values for perspective")
  public PerspectiveFilterData perspectiveFilters(
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "sortCriteria") List<QLCEViewSortCriteria> sortCriteria,
      @GraphQLArgument(name = "limit") Integer limit, @GraphQLArgument(name = "offset") Integer offset,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveFilters(
        aggregateFunction, filters, groupBy, sortCriteria, limit, offset, isClusterQuery, accountId);
  }

  @GraphQLQuery(name = "perspectiveOverviewStats", description = "Overview stats for perspective")
  public PerspectiveOverviewStatsData perspectiveOverviewStats(@GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveOverviewStats(accountId);
  }

  @GraphQLQuery(name = "perspectiveTimeSeriesStats", description = "Table for perspective")
  public PerspectiveTimeSeriesData perspectiveTimeSeriesStats(
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "sortCriteria") List<QLCEViewSortCriteria> sortCriteria,
      @GraphQLArgument(name = "limit") Integer limit, @GraphQLArgument(name = "offset") Integer offset,
      @GraphQLArgument(name = "preferences") QLCEViewPreferences preferences,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveTimeSeriesStats(
        aggregateFunction, filters, groupBy, sortCriteria, limit, offset, preferences, isClusterQuery, accountId);
  }

  @GraphQLQuery(name = "perspectiveFields", description = "Fields for perspective explorer")
  public PerspectiveFieldsData perspectiveFields(@GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveFields(filters, accountId);
  }

  @GraphQLQuery(name = "perspectives", description = "Fetch perspectives for account")
  public PerspectiveData perspectives(@GraphQLArgument(name = "folderId") String folderId,
      @GraphQLArgument(name = "sortCriteria") QLCEViewSortCriteria sortCriteria,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    if (StringUtils.isEmpty(folderId)) {
      List<QLCEView> allPerspectives = perspectiveService.perspectives(sortCriteria, accountId);
      List<QLCEView> allowedPerspectives = null;
      if (allPerspectives != null) {
        Set<String> allowedFolderIds = rbacHelper.checkFolderIdsGivenPermission(accountId, null, null,
            allPerspectives.stream().map(perspective -> perspective.getFolderId()).collect(Collectors.toSet()),
            PERSPECTIVE_VIEW);
        allowedPerspectives = allPerspectives.stream()
                                  .filter(perspective -> allowedFolderIds.contains(perspective.getFolderId()))
                                  .collect(Collectors.toList());
      }
      return PerspectiveData.builder().customerViews(allowedPerspectives).build();
    }
    rbacHelper.checkPerspectiveViewPermission(accountId, null, null, folderId);
    return PerspectiveData.builder()
        .customerViews(perspectiveService.perspectives(folderId, sortCriteria, accountId))
        .build();
  }

  @GraphQLQuery(name = "perspectiveTotalCount", description = "Get total count of rows for query")
  public Integer perspectiveTotalCount(@GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveTotalCount(filters, groupBy, isClusterQuery, accountId);
  }

  @GraphQLQuery(name = "workloadLabels", description = "Labels for workloads")
  public Map<String, Map<String, String>> workloadLabels(@GraphQLArgument(name = "workloads") Set<String> workloads,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.workloadLabels(workloads, filters, accountId);
  }
}
