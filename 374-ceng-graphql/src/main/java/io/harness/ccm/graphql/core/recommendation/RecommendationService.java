/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.commons.beans.recommendation.RecommendationState;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.graphql.dto.recommendation.FilterStatsDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationItemDTO;
import io.harness.queryconverter.SQLConverter;
import io.harness.timescaledb.tables.pojos.CeRecommendations;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;

@Singleton
public class RecommendationService {
  @Inject private K8sRecommendationDAO k8sRecommendationDAO;

  @NonNull
  public RecommendationOverviewStats getStats(@NonNull final String accountId, Condition condition) {
    return k8sRecommendationDAO.fetchRecommendationsOverviewStats(accountId, condition);
  }

  @NonNull
  public List<RecommendationItemDTO> listAll(
      @NonNull final String accountId, Condition condition, @NonNull Long offset, @NonNull Long limit) {
    final List<CeRecommendations> ceRecommendationsList =
        k8sRecommendationDAO.fetchRecommendationsOverview(accountId, condition, offset, limit);

    return ceRecommendationsList.stream()
        .map(ceRecommendations
            -> RecommendationItemDTO.builder()
                   .id(ceRecommendations.getId())
                   .resourceName(ceRecommendations.getName())
                   .clusterName(ceRecommendations.getClustername())
                   .namespace(ceRecommendations.getNamespace())
                   .resourceType(ResourceType.valueOf(ceRecommendations.getResourcetype()))
                   .monthlyCost(ceRecommendations.getMonthlycost())
                   .monthlySaving(ceRecommendations.getMonthlysaving())
                   .isValid(ceRecommendations.getIsvalid())
                   .lastProcessedAt(ceRecommendations.getLastprocessedat())
                   .recommendationState(RecommendationState.valueOf(ceRecommendations.getRecommendationstate()))
                   .jiraConnectorRef(ceRecommendations.getJiraconnectorref())
                   .jiraIssueKey(ceRecommendations.getJiraissuekey())
                   .jiraStatus(ceRecommendations.getJirastatus())
                   .cloudProvider(ceRecommendations.getCloudprovider())
                   .governanceRuleId(ceRecommendations.getGovernanceruleid())
                   .build())
        .collect(Collectors.toList());
  }

  public List<FilterStatsDTO> getFilterStats(
      String accountId, Condition preCondition, @NonNull List<String> columns, @NonNull Table<?> table) {
    List<FilterStatsDTO> result = new ArrayList<>();

    for (String column : columns) {
      Field<?> field = SQLConverter.getField(column, table);
      List<String> columnValues = k8sRecommendationDAO.getDistinctStringValues(accountId, preCondition, field, table);

      result.add(FilterStatsDTO.builder().key(column).values(columnValues).build());
    }

    return result;
  }

  public int getRecommendationsCount(@NonNull String accountId, @NonNull Condition condition) {
    return k8sRecommendationDAO.fetchRecommendationsCount(accountId, condition);
  }

  public void updateRecommendationState(
      @NonNull String recommendationId, @NonNull RecommendationState recommendationState) {
    k8sRecommendationDAO.updateRecommendationState(recommendationId, recommendationState);
  }
}
