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
import io.harness.ccm.graphql.dto.perspectives.PerspectiveData.PerspectiveDataBuilder;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveEntityStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFieldsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFilterData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveOverviewStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveTrendStats;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.graphql.utils.annotations.GraphQLApi;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.views.dto.PerspectiveTimeSeriesData;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.CloudFilter;
import io.harness.ccm.views.entities.ViewPreferences;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.service.CEViewFolderService;
import io.harness.ccm.views.service.CEViewService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.DefaultValue;
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
  @Inject private CEViewFolderService ceViewFolderService;
  @Inject private CEViewService ceViewService;

  @GraphQLQuery(name = "perspectiveTrendStats", description = "Trend stats for perspective")
  public PerspectiveTrendStats perspectiveTrendStats(
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "preferences") ViewPreferences viewPreferences,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveTrendStats(
        filters, groupBy, aggregateFunction, viewPreferences, isClusterQuery, accountId);
  }

  @GraphQLQuery(name = "perspectiveForecastCost", description = "Forecast cost for perspective")
  public PerspectiveTrendStats perspectiveForecastCost(
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "preferences") ViewPreferences viewPreferences,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveForecastCost(
        filters, groupBy, aggregateFunction, viewPreferences, isClusterQuery, accountId);
  }

  @GraphQLQuery(name = "perspectiveGrid", description = "Table for perspective")
  public PerspectiveEntityStatsData perspectiveGrid(
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "sortCriteria") List<QLCEViewSortCriteria> sortCriteria,
      @GraphQLArgument(name = "limit") Integer limit, @GraphQLArgument(name = "offset") Integer offset,
      @GraphQLArgument(name = "preferences") ViewPreferences viewPreferences,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLArgument(name = "skipRoundOff") Boolean skipRoundOff,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveGrid(aggregateFunction, filters, groupBy, sortCriteria, limit, offset,
        viewPreferences, isClusterQuery, skipRoundOff, accountId);
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
      @GraphQLArgument(name = "preferences") ViewPreferences viewPreferences,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveTimeSeriesStats(
        aggregateFunction, filters, groupBy, sortCriteria, limit, offset, viewPreferences, isClusterQuery, accountId);
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
      @GraphQLArgument(name = "pageSize") @DefaultValue("20") Integer pageSize,
      @GraphQLArgument(name = "pageNo") @DefaultValue("0") Integer pageNo,
      @GraphQLArgument(name = "searchKey") String searchKey,
      @GraphQLArgument(name = "cloudFilters") List<CloudFilter> cloudFilters,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    PerspectiveDataBuilder perspectiveDataBuilder = PerspectiveData.builder();
    if (StringUtils.isEmpty(folderId)) {
      List<CEViewFolder> folders = ceViewFolderService.getFolders(accountId, "");
      Set<String> allowedFolderIds = rbacHelper.checkFolderIdsGivenPermission(accountId, null, null,
          folders.stream().map(CEViewFolder::getUuid).collect(Collectors.toSet()), PERSPECTIVE_VIEW);
      perspectiveDataBuilder.totalCount(
          ceViewService.countByAccountIdAndFolderIds(accountId, allowedFolderIds, searchKey, cloudFilters));
      perspectiveDataBuilder.views(perspectiveService.perspectives(
          sortCriteria, accountId, pageSize, pageNo, searchKey, folders, allowedFolderIds, cloudFilters));
    } else {
      rbacHelper.checkPerspectiveViewPermission(accountId, null, null, folderId);
      perspectiveDataBuilder.totalCount(ceViewService.countByAccountIdAndFolderIds(
          accountId, Collections.singleton(folderId), searchKey, cloudFilters));
      perspectiveDataBuilder.views(perspectiveService.perspectives(
          folderId, sortCriteria, accountId, pageNo, pageSize, cloudFilters, searchKey));
    }
    return perspectiveDataBuilder.build();
  }

  @GraphQLQuery(name = "perspectiveTotalCount", description = "Get total count of rows for query")
  public Integer perspectiveTotalCount(@GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "preferences") ViewPreferences viewPreferences,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.perspectiveTotalCount(filters, groupBy, viewPreferences, isClusterQuery, accountId);
  }

  @GraphQLQuery(name = "workloadLabels", description = "Labels for workloads")
  public Map<String, Map<String, String>> workloadLabels(@GraphQLArgument(name = "workloads") Set<String> workloads,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveService.workloadLabels(workloads, filters, accountId);
  }
}
