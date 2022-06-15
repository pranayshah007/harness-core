/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.utils.TimeUtils.offsetDateTimeNow;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.CLUSTER;
import static io.harness.ccm.views.graphql.ViewsQueryHelper.getPerspectiveIdFromMetadataFilter;
import static io.harness.ccm.views.service.impl.ViewsBillingServiceImpl.convertIdFilterToViewCondition;
import static io.harness.ccm.views.service.impl.ViewsBillingServiceImpl.convertQLCEViewRuleToViewRule;
import static io.harness.ccm.views.service.impl.ViewsBillingServiceImpl.getIdFilters;
import static io.harness.ccm.views.service.impl.ViewsBillingServiceImpl.getRuleFilters;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.timescaledb.Tables.CE_RECOMMENDATIONS;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.graphql.core.recommendation.RecommendationService;
import io.harness.ccm.graphql.dto.recommendation.FilterStatsDTO;
import io.harness.ccm.graphql.dto.recommendation.K8sRecommendationFilterDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationDetailsDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationItemDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationsDTO;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.graphql.utils.annotations.GraphQLApi;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewRule;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.InvalidRequestException;
import io.harness.queryconverter.SQLConverter;
import io.harness.timescaledb.tables.records.CeRecommendationsRecord;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.impl.DSL;

@Slf4j
@Singleton
@GraphQLApi
@OwnedBy(CE)
public class RecommendationsOverviewQueryV2 {
  @Inject private GraphQLUtils graphQLUtils;
  @Inject private CEViewService viewService;
  @Inject private RecommendationService recommendationService;
  @Inject private RecommendationsDetailsQuery detailsQuery;

  private static final Gson GSON = new Gson();
  private static final String RESOURCE_TYPE_WORKLOAD = "WORKLOAD";

  @GraphQLQuery(name = "recommendationsV2", description = "The list of all types of recommendations for overview page")
  public RecommendationsDTO recommendations(
      @GraphQLArgument(name = "filter", defaultValue = "{\"offset\":0,\"limit\":10, \"minSaving\":0}")
      K8sRecommendationFilterDTO filter, @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);

    Condition condition = applyAllFilters(filter);

    List<RecommendationItemDTO> items =
        recommendationService.listAll(accountId, condition, filter.getOffset(), filter.getLimit());
    items = items.stream()
                .map(item
                    -> item.getRecommendationDetails() != null
                        ? item
                        : RecommendationItemDTO.builder()
                              .id(item.getId())
                              .resourceName(item.getResourceName())
                              .clusterName(item.getClusterName())
                              .namespace(item.getNamespace())
                              .resourceType(item.getResourceType())
                              .recommendationDetails(getRecommendationDetails(item, env))
                              .monthlyCost(item.getMonthlyCost())
                              .monthlySaving(item.getMonthlySaving())
                              .build())
                .collect(Collectors.toList());
    return RecommendationsDTO.builder().items(items).offset(filter.getOffset()).limit(filter.getLimit()).build();
  }

  @GraphQLQuery(name = "recommendationStatsV2", description = "Top panel stats API, aggregated")
  public RecommendationOverviewStats recommendationStats(
      @GraphQLArgument(name = "filter", defaultValue = "{}") K8sRecommendationFilterDTO filter,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);

    Condition condition = applyAllFilters(filter);

    return recommendationService.getStats(accountId, condition);
  }

  // TODO(UTSAV): Add unit test
  @GraphQLQuery(name = "count", description = "generic count query RecommendationOverviewStats context")
  public int count(
      @GraphQLContext RecommendationOverviewStats xyz, @GraphQLEnvironment final ResolutionEnvironment env) {
    return genericCountQuery(env);
  }

  // TODO(UTSAV): Add unit test
  @GraphQLQuery(name = "count", description = "generic count query RecommendationsDTO context")
  public int count(@GraphQLContext RecommendationsDTO xyz, @GraphQLEnvironment final ResolutionEnvironment env) {
    return genericCountQuery(env);
  }

  private int genericCountQuery(@NotNull final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);

    K8sRecommendationFilterDTO filter = extractRecommendationFilter(env);

    Condition condition = applyAllFilters(filter);

    return recommendationService.getRecommendationsCount(accountId, condition);
  }

  private K8sRecommendationFilterDTO extractRecommendationFilter(final ResolutionEnvironment env) {
    Object filter = env.dataFetchingEnvironment.getVariables().getOrDefault("filter", new HashMap<String, Object>());
    JsonElement jsonElement = GSON.toJsonTree(filter);
    return GSON.fromJson(jsonElement, K8sRecommendationFilterDTO.class);
  }

  @GraphQLQuery(name = "recommendationFilterStatsV2", description = "Possible filter values for each key")
  public List<FilterStatsDTO> recommendationFilterStats(
      @GraphQLArgument(name = "keys", defaultValue = "[]") List<String> columns,
      @GraphQLArgument(name = "filter", defaultValue = "{}") K8sRecommendationFilterDTO filter,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);

    Condition condition = applyAllFilters(filter);

    return recommendationService.getFilterStats(accountId, condition, columns, CE_RECOMMENDATIONS);
  }

  @NotNull
  private Condition applyAllFilters(@NotNull K8sRecommendationFilterDTO filter) {
    Condition condition = getValidRecommendationFilter();

    if (!isEmpty(filter.getIds())) {
      condition = condition.and(CE_RECOMMENDATIONS.ID.in(filter.getIds()));
    } else {
      if (!isEmpty(filter.getResourceTypes())) {
        condition = condition.and(CE_RECOMMENDATIONS.RESOURCETYPE.in(enumToString(filter.getResourceTypes())));
      }

      condition = condition.and(constructInCondition(CE_RECOMMENDATIONS.CLUSTERNAME, filter.getClusterNames()));
      condition = condition.and(constructInCondition(CE_RECOMMENDATIONS.NAMESPACE, filter.getNamespaces()));
      condition = condition.and(constructInCondition(CE_RECOMMENDATIONS.NAME, filter.getNames()));
      condition = condition.and(constructGreaterOrEqualFilter(CE_RECOMMENDATIONS.MONTHLYSAVING, filter.getMinSaving()));
      condition = condition.and(constructGreaterOrEqualFilter(CE_RECOMMENDATIONS.MONTHLYCOST, filter.getMinCost()));
    }

    final Condition perspectiveCondition =
        getPerspectiveCondition(firstNonNull(filter.getPerspectiveFilters(), emptyList()));

    return condition.and(perspectiveCondition);
  }

  @NotNull
  private Condition getPerspectiveCondition(@NotNull List<QLCEViewFilterWrapper> perspectiveFilters) {
    final List<QLCEViewRule> qlCeViewRules = getRuleFilters(perspectiveFilters);

    final List<ViewRule> combinedViewRuleList = convertQLCEViewRuleToViewRule(qlCeViewRules);
    combinedViewRuleList.addAll(getPerspectiveRuleList(perspectiveFilters));

    Condition ORConditions = constructViewRuleFilterCondition(combinedViewRuleList);

    final List<QLCEViewFilter> qlceViewFilters = getIdFilters(perspectiveFilters);

    final List<ViewCondition> viewIdConditions = convertIdFilterToViewCondition(qlceViewFilters);

    Condition ANDConditions = constructViewFilterCondition(viewIdConditions);

    return ORConditions.and(ANDConditions);
  }

  @NotNull
  private List<ViewRule> getPerspectiveRuleList(@NotNull List<QLCEViewFilterWrapper> perspectiveFilters) {
    final Optional<String> perspectiveId = getPerspectiveIdFromMetadataFilter(perspectiveFilters);

    if (perspectiveId.isPresent()) {
      final CEView perspective = viewService.get(perspectiveId.get());
      if (perspective != null) {
        return perspective.getViewRules();
      }

      throw new InvalidRequestException(String.format("perspectiveId=[%s] not present", perspectiveId.get()));
    }

    return emptyList();
  }

  @NotNull
  private static Condition constructViewRuleFilterCondition(@NotNull List<ViewRule> viewRuleList) {
    Condition condition = DSL.noCondition();

    for (ViewRule viewRule : viewRuleList) {
      condition = condition.or(constructViewFilterCondition(viewRule.getViewConditions()));
    }

    return condition;
  }

  @NotNull
  private static Condition constructViewFilterCondition(@NotNull List<ViewCondition> viewConditionList) {
    Condition condition = DSL.noCondition();

    for (ViewCondition viewCondition : viewConditionList) {
      ViewIdCondition idCondition = (ViewIdCondition) viewCondition;

      if (idCondition.getViewField().getIdentifier() == CLUSTER) {
        condition = condition.and(constructViewFilterCondition(idCondition));
      }
    }

    return condition;
  }

  private static Condition constructViewFilterCondition(ViewIdCondition viewIdCondition) {
    final Table<?> table = CE_RECOMMENDATIONS;
    final String fieldId = normalizeField(viewIdCondition.getViewField().getFieldId());

    switch (viewIdCondition.getViewOperator()) {
      case IN:
        return SQLConverter.getField(fieldId, table).in(viewIdCondition.getValues());
      case NOT_IN:
        return SQLConverter.getField(fieldId, table).notIn(viewIdCondition.getValues());
      case NOT_NULL:
        return SQLConverter.getField(fieldId, table).isNotNull();
      case NULL:
        return SQLConverter.getField(fieldId, table).isNull();
      default:
        throw new InvalidRequestException(String.format("%s not implemented", viewIdCondition.getViewOperator()));
    }
  }

  @NotNull
  private static String normalizeField(final String fieldId) {
    if ("workloadName".equalsIgnoreCase(fieldId)) {
      return CE_RECOMMENDATIONS.NAME.getName();
    }

    return fieldId;
  }

  @NotNull
  private static List<String> enumToString(List<? extends Enum> list) {
    return list.stream().map(Enum::name).collect(Collectors.toList());
  }

  @NotNull
  private static Condition constructInCondition(TableField<CeRecommendationsRecord, String> field, List<String> value) {
    if (!isEmpty(value)) {
      return field.in(value);
    }

    return DSL.noCondition();
  }

  @NotNull
  private static Condition constructGreaterOrEqualFilter(
      TableField<CeRecommendationsRecord, Double> field, Double value) {
    if (value != null) {
      return field.greaterOrEqual(value);
    }

    return DSL.noCondition();
  }

  private static Condition getValidRecommendationFilter() {
    return CE_RECOMMENDATIONS.ISVALID
        .eq(true)
        // based on current-gen workload recommendation dataFetcher
        .and(CE_RECOMMENDATIONS.LASTPROCESSEDAT.greaterOrEqual(
            offsetDateTimeNow().truncatedTo(ChronoUnit.DAYS).minusDays(2)))
        .and(nonDelegate());
  }

  private static Condition nonDelegate() {
    return CE_RECOMMENDATIONS.RESOURCETYPE.notEqual(RESOURCE_TYPE_WORKLOAD)
        .or(CE_RECOMMENDATIONS.RESOURCETYPE.eq(RESOURCE_TYPE_WORKLOAD)
                .and(CE_RECOMMENDATIONS.NAMESPACE.notIn("harness-delegate", "harness-delegate-ng")));
  }

  private RecommendationDetailsDTO getRecommendationDetails(
      RecommendationItemDTO item, final ResolutionEnvironment env) {
    try {
      return detailsQuery.recommendationDetails(item, OffsetDateTime.now().minusDays(7), OffsetDateTime.now(), env);
    } catch (Exception e) {
      log.error("Exception while fetching data: {}", e);
    }
    return null;
  }
}
