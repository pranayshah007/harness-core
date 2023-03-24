/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.beans.InstanceType.ECS_TASK_EC2;
import static io.harness.ccm.commons.beans.InstanceType.ECS_TASK_FARGATE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_POD;
import static io.harness.ccm.commons.beans.InstanceType.K8S_POD_FARGATE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.CLOUD_SERVICE_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.CLUSTER_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.INSTANCE_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.NAMESPACE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.THRESHOLD_DAYS_TO_SHOW_RECOMMENDATION;
import static io.harness.ccm.commons.constants.ViewFieldConstants.WORKLOAD_NAME_FIELD_ID;
import static io.harness.ccm.commons.utils.TimeUtils.offsetDateTimeNow;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.PERMISSION_MISSING_MESSAGE;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.RESOURCE_FOLDER;
import static io.harness.ccm.rbac.CCMRbacPermissions.PERSPECTIVE_VIEW;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.BUSINESS_MAPPING;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.CLUSTER;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.LABEL;
import static io.harness.ccm.views.graphql.ViewsQueryHelper.getPerspectiveIdFromMetadataFilter;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE_HOURLY_AGGREGRATED;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE_HOURLY_AGGREGRATED_CH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.timescaledb.Tables.CE_RECOMMENDATIONS;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.commons.beans.recommendation.RecommendationState;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.graphql.core.recommendation.RecommendationService;
import io.harness.ccm.graphql.dto.recommendation.FilterStatsDTO;
import io.harness.ccm.graphql.dto.recommendation.K8sRecommendationFilterDTO;
import io.harness.ccm.graphql.dto.recommendation.K8sRecommendationFilterDTO.K8sRecommendationFilterDTOBuilder;
import io.harness.ccm.graphql.dto.recommendation.RecommendationDetailsDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationItemDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationsDTO;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.graphql.utils.annotations.GraphQLApi;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dto.CEViewShortHand;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewRule;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.helper.ViewParametersHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.queryconverter.SQLConverter;
import io.harness.timescaledb.tables.records.CeRecommendationsRecord;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import graphql.com.google.common.collect.ImmutableSet;
import io.fabric8.utils.Lists;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
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
  @Inject private BusinessMappingService businessMappingService;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private ViewsQueryBuilder viewsQueryBuilder;
  @Inject private BigQueryService bigQueryService;
  @Inject private BigQueryHelper bigQueryHelper;
  @Inject private ViewParametersHelper viewParametersHelper;
  @Inject private CCMRbacHelper rbacHelper;
  @Inject private CEViewService ceViewService;
  @Inject @Nullable @Named("clickHouseConfig") ClickHouseConfig clickHouseConfig;
  @Inject ClickHouseService clickHouseService;
  @Inject @Named("isClickHouseEnabled") boolean isClickHouseEnabled;
  private static final Set<String> RECOMMENDATION_RESOURCE_TYPE_COLUMNS =
      ImmutableSet.of(WORKLOAD_NAME_FIELD_ID, INSTANCE_NAME_FIELD_ID, CLOUD_SERVICE_NAME_FIELD_ID);
  private static final Set<String> RECOMMENDATION_FILTER_COLUMNS = ImmutableSet.of(CLUSTER_NAME_FIELD_ID,
      NAMESPACE_FIELD_ID, WORKLOAD_NAME_FIELD_ID, INSTANCE_NAME_FIELD_ID, CLOUD_SERVICE_NAME_FIELD_ID);
  private static final Set<String> WORKLOAD_INSTANCE_TYPES = ImmutableSet.of(K8S_POD.name(), K8S_POD_FARGATE.name());
  private static final Set<String> CLOUD_SERVICE_INSTANCE_TYPES =
      ImmutableSet.of(ECS_TASK_EC2.name(), ECS_TASK_FARGATE.name());
  private static final String DEFAULT_CLUSTER_VIEW_NAME = "Cluster";

  private static final Gson GSON = new Gson();

  @GraphQLQuery(name = "recommendationsV2", description = "The list of all types of recommendations for overview page")
  public RecommendationsDTO recommendations(
      @GraphQLArgument(name = "filter", defaultValue = "{\"offset\":0,\"limit\":10, \"minSaving\":0}")
      K8sRecommendationFilterDTO filter, @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);

    // Check access across all the perspectives
    boolean accessToAllPerspectives = rbacHelper.hasPerspectiveViewOnAllResources(accountId, null, null);
    final String perspectiveId;
    final String perspectiveName;
    if (accessToAllPerspectives) {
      List<QLCEView> defaultPerspectives =
          ceViewService.getAllViews(accountId, ceViewService.getDefaultFolderId(accountId), true, null)
              .stream()
              .filter(qlceView -> qlceView.getName().equalsIgnoreCase(DEFAULT_CLUSTER_VIEW_NAME))
              .collect(Collectors.toList());
      perspectiveId = Lists.isNullOrEmpty(defaultPerspectives) ? null : defaultPerspectives.get(0).getId();
      perspectiveName = Lists.isNullOrEmpty(defaultPerspectives) ? null : defaultPerspectives.get(0).getName();
    } else {
      perspectiveId = null;
      perspectiveName = null;
    }

    HashMap<String, CEViewShortHand> allowedRecommendationsIdAndPerspectives =
        accessToAllPerspectives ? null : listAllowedRecommendationsIdAndPerspectives(accountId);

    Condition condition = applyAllFilters(filter, accountId);

    List<RecommendationItemDTO> items =
        recommendationService.listAll(accountId, condition, filter.getOffset(), filter.getLimit());
    items =
        items.stream()
            .filter(
                item -> accessToAllPerspectives || allowedRecommendationsIdAndPerspectives.containsKey(item.getId()))
            .map(item
                -> item.getRecommendationDetails() != null
                    ? RecommendationItemDTO.builder()
                          .id(item.getId())
                          .clusterName(item.getClusterName())
                          .namespace(item.getNamespace())
                          .resourceName(item.getResourceName())
                          .monthlyCost(item.getMonthlyCost())
                          .monthlySaving(item.getMonthlySaving())
                          .resourceType(item.getResourceType())
                          .recommendationState(item.getRecommendationState())
                          .jiraConnectorRef(item.getJiraConnectorRef())
                          .jiraIssueKey(item.getJiraIssueKey())
                          .jiraStatus(item.getJiraStatus())
                          .recommendationDetails(item.getRecommendationDetails())
                          .perspectiveId(accessToAllPerspectives
                                  ? perspectiveId
                                  : allowedRecommendationsIdAndPerspectives.get(item.getId()).getUuid())
                          .perspectiveName(accessToAllPerspectives
                                  ? perspectiveName
                                  : allowedRecommendationsIdAndPerspectives.get(item.getId()).getName())
                          .build()
                    : RecommendationItemDTO.builder()
                          .id(item.getId())
                          .resourceName(item.getResourceName())
                          .clusterName(item.getClusterName())
                          .namespace(item.getNamespace())
                          .resourceType(item.getResourceType())
                          .recommendationDetails(getRecommendationDetails(item, env))
                          .monthlyCost(item.getMonthlyCost())
                          .monthlySaving(item.getMonthlySaving())
                          .recommendationState(item.getRecommendationState())
                          .jiraConnectorRef(item.getJiraConnectorRef())
                          .jiraIssueKey(item.getJiraIssueKey())
                          .jiraStatus(item.getJiraStatus())
                          .perspectiveId(accessToAllPerspectives
                                  ? perspectiveId
                                  : allowedRecommendationsIdAndPerspectives.get(item.getId()).getUuid())
                          .perspectiveName(accessToAllPerspectives
                                  ? perspectiveName
                                  : allowedRecommendationsIdAndPerspectives.get(item.getId()).getName())
                          .build())
            .collect(Collectors.toList());
    return RecommendationsDTO.builder().items(items).offset(filter.getOffset()).limit(filter.getLimit()).build();
  }

  @GraphQLQuery(name = "recommendationStatsV2", description = "Top panel stats API, aggregated")
  public RecommendationOverviewStats recommendationStats(
      @GraphQLArgument(name = "filter", defaultValue = "{}") K8sRecommendationFilterDTO filter,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    K8sRecommendationFilterDTO appliedAllowedPerspectiveFilter = filter;
    if (!rbacHelper.hasPerspectiveViewOnAllResources(accountId, null, null)) {
      appliedAllowedPerspectiveFilter = applyAllowedPerspectiveFilter(accountId, filter);
      if (Lists.isNullOrEmpty(appliedAllowedPerspectiveFilter.getIds())) {
        return null;
      }
    }
    Condition condition = applyAllFilters(appliedAllowedPerspectiveFilter, accountId);
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

  @GraphQLQuery(name = "markRecommendationAsApplied", description = "Mark a recommendation as applied")
  public void markRecommendationAsApplied(@GraphQLArgument(name = "recommendationId") String recommendationId,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    HashMap<String, CEViewShortHand> allowedRecommendationsIdAndPerspectives =
        listAllowedRecommendationsIdAndPerspectives(accountId);
    if (!allowedRecommendationsIdAndPerspectives.containsKey(recommendationId)) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, PERSPECTIVE_VIEW, RESOURCE_FOLDER), WingsException.USER, null);
    }
    recommendationService.updateRecommendationState(recommendationId, RecommendationState.APPLIED);
  }

  private int genericCountQuery(@NotNull final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    K8sRecommendationFilterDTO filter = extractRecommendationFilter(env);
    K8sRecommendationFilterDTO appliedAllowedPerspectiveFilter = filter;
    if (!rbacHelper.hasPerspectiveViewOnAllResources(accountId, null, null)) {
      appliedAllowedPerspectiveFilter = applyAllowedPerspectiveFilter(accountId, filter);
      if (Lists.isNullOrEmpty(appliedAllowedPerspectiveFilter.getIds())) {
        return 0;
      }
    }
    Condition condition = applyAllFilters(appliedAllowedPerspectiveFilter, accountId);
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
    K8sRecommendationFilterDTO appliedAllowedPerspectiveFilter = filter;
    if (!rbacHelper.hasPerspectiveViewOnAllResources(accountId, null, null)) {
      appliedAllowedPerspectiveFilter = applyAllowedPerspectiveFilter(accountId, filter);
      if (Lists.isNullOrEmpty(appliedAllowedPerspectiveFilter.getIds())) {
        return null;
      }
    }
    Condition condition = applyAllFilters(appliedAllowedPerspectiveFilter, accountId);
    return recommendationService.getFilterStats(accountId, condition, columns, CE_RECOMMENDATIONS);
  }

  @NotNull
  private Condition applyAllFilters(@NotNull K8sRecommendationFilterDTO filter, @NotNull String accountId) {
    Condition condition = getValidRecommendationFilter();

    if (!isEmpty(filter.getIds())) {
      condition = condition.and(CE_RECOMMENDATIONS.ID.in(filter.getIds()));
    } else {
      if (!isEmpty(filter.getResourceTypes())) {
        condition = condition.and(CE_RECOMMENDATIONS.RESOURCETYPE.in(enumToString(filter.getResourceTypes())));
      }
      if (!isEmpty(filter.getRecommendationStates())) {
        condition =
            condition.and(CE_RECOMMENDATIONS.RECOMMENDATIONSTATE.in(enumToString(filter.getRecommendationStates())));
      }

      condition = condition.and(constructInCondition(CE_RECOMMENDATIONS.CLUSTERNAME, filter.getClusterNames()));
      condition = condition.and(constructInCondition(CE_RECOMMENDATIONS.NAMESPACE, filter.getNamespaces()));
      condition = condition.and(constructInCondition(CE_RECOMMENDATIONS.NAME, filter.getNames()));
      condition = condition.and(constructGreaterOrEqualFilter(CE_RECOMMENDATIONS.MONTHLYSAVING, filter.getMinSaving()));
      condition = condition.and(constructGreaterOrEqualFilter(CE_RECOMMENDATIONS.MONTHLYCOST, filter.getMinCost()));
    }

    final Condition perspectiveCondition =
        getPerspectiveCondition(firstNonNull(filter.getPerspectiveFilters(), emptyList()), accountId);

    return condition.and(perspectiveCondition);
  }

  @NotNull
  private Condition getPerspectiveCondition(
      @NotNull List<QLCEViewFilterWrapper> perspectiveFilters, @NotNull String accountId) {
    final List<QLCEViewTimeFilter> qlCEViewTimeFilters = viewsQueryHelper.getTimeFilters(perspectiveFilters);

    final List<QLCEViewRule> qlCeViewRules = viewParametersHelper.getRuleFilters(perspectiveFilters);
    final List<ViewRule> combinedViewRuleList =
        viewParametersHelper.convertQLCEViewRuleListToViewRuleList(qlCeViewRules);
    combinedViewRuleList.addAll(getPerspectiveRuleList(perspectiveFilters));

    Condition ORConditions = constructViewRuleFilterCondition(combinedViewRuleList, accountId, qlCEViewTimeFilters);

    final List<ViewCondition> viewIdConditions =
        viewParametersHelper.convertIdFilterToViewCondition(viewParametersHelper.getIdFilters(perspectiveFilters));

    Condition ANDConditions = constructViewFilterCondition(viewIdConditions, accountId, qlCEViewTimeFilters);

    return ORConditions.and(ANDConditions);
  }

  private List<ViewRule> getBusinessMappingViewRules(final String businessMappingId, final String accountId,
      final ViewIdOperator viewIdOperator, final List<String> values) {
    final List<ViewRule> viewRuleList = new ArrayList<>();
    if (Objects.nonNull(businessMappingId)) {
      final BusinessMapping businessMapping = businessMappingService.get(businessMappingId, accountId);
      if (businessMapping != null) {
        if (Objects.nonNull(businessMapping.getCostTargets())) {
          businessMapping.getCostTargets().forEach(costTarget -> {
            if (Objects.nonNull(costTarget) && !Lists.isNullOrEmpty(costTarget.getRules())) {
              viewRuleList.addAll(getUpdatedBusinessMappingViewRules(
                  costTarget.getName(), costTarget.getRules(), viewIdOperator, values));
            }
          });
        }
        if (Objects.nonNull(businessMapping.getSharedCosts())) {
          businessMapping.getSharedCosts().forEach(sharedCost -> {
            if (Objects.nonNull(sharedCost) && !Lists.isNullOrEmpty(sharedCost.getRules())) {
              viewRuleList.addAll(sharedCost.getRules());
            }
          });
        }
      }
    }
    return viewRuleList;
  }

  private List<ViewRule> getUpdatedBusinessMappingViewRules(
      final String name, final List<ViewRule> rules, final ViewIdOperator viewIdOperator, final List<String> values) {
    List<ViewRule> updatedViewRules = new ArrayList<>();
    switch (viewIdOperator) {
      case IN:
        if (Objects.nonNull(values) && values.contains(name)) {
          updatedViewRules = rules;
        }
        break;
      case NOT_IN:
        if (Objects.nonNull(values) && !values.contains(name)) {
          updatedViewRules = rules;
        }
        break;
      case LIKE:
        if (Objects.nonNull(values)) {
          for (final String value : values) {
            if (name.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))) {
              updatedViewRules = rules;
              break;
            }
          }
        }
        break;
      case NOT_NULL:
        updatedViewRules = rules;
        break;
      case NULL:
        // Not updating any rules
        break;
      case EQUALS:
        // Not supporting EQUALS
        break;
      default:
        break;
    }
    return updatedViewRules;
  }

  @NotNull
  public List<ViewRule> getPerspectiveRuleList(@NotNull List<QLCEViewFilterWrapper> perspectiveFilters) {
    final Optional<String> perspectiveId = getPerspectiveIdFromMetadataFilter(perspectiveFilters);

    if (Objects.nonNull(perspectiveId) && perspectiveId.isPresent()) {
      final CEView perspective = viewService.get(perspectiveId.get());
      if (perspective != null) {
        return perspective.getViewRules();
      }

      throw new InvalidRequestException(String.format("perspectiveId=[%s] not present", perspectiveId.get()));
    }

    return emptyList();
  }

  @NotNull
  private Condition constructViewRuleFilterCondition(@NotNull List<ViewRule> viewRuleList, @NotNull String accountId,
      @NotNull List<QLCEViewTimeFilter> qlCEViewTimeFilters) {
    Condition condition = DSL.noCondition();

    for (ViewRule viewRule : viewRuleList) {
      condition =
          condition.or(constructViewFilterCondition(viewRule.getViewConditions(), accountId, qlCEViewTimeFilters));
    }

    return condition;
  }

  @NotNull
  private Condition constructViewFilterCondition(@NotNull List<ViewCondition> viewConditionList,
      @NotNull String accountId, @NotNull List<QLCEViewTimeFilter> qlCEViewTimeFilters) {
    Condition condition = DSL.noCondition();

    final Set<String> resourceTypes = new HashSet<>();
    for (final ViewCondition viewCondition : viewConditionList) {
      final ViewIdCondition idCondition = (ViewIdCondition) viewCondition;
      final String fieldId = idCondition.getViewField().getFieldId();
      final ViewFieldIdentifier viewFieldIdentifier = idCondition.getViewField().getIdentifier();
      if (viewFieldIdentifier == CLUSTER && RECOMMENDATION_FILTER_COLUMNS.contains(fieldId)) {
        condition = condition.and(constructViewFilterCondition(idCondition));
        final String resourceType = getRecommendationResourceType(fieldId);
        if (Objects.nonNull(resourceType)) {
          resourceTypes.add(resourceType);
        }
      } else if (viewFieldIdentifier == BUSINESS_MAPPING) {
        final String businessMappingId = idCondition.getViewField().getFieldId();
        if (Objects.nonNull(businessMappingId)) {
          condition = condition.and(
              constructViewRuleFilterCondition(getBusinessMappingViewRules(businessMappingId, accountId,
                                                   idCondition.getViewOperator(), idCondition.getValues()),
                  accountId, qlCEViewTimeFilters));
        }
      } else if (idCondition.getViewField().getIdentifier() == LABEL) {
        if (isClickHouseEnabled) {
          final ResultSet result = getWorkloadAndCloudServiceNamesResultSet(qlCEViewTimeFilters, idCondition);
          condition = condition.and(getWorkloadAndCloudServiceNamesConditions(result));
        } else {
          final TableResult result =
              getWorkloadAndCloudServiceNamesTableResult(accountId, qlCEViewTimeFilters, idCondition);
          condition = condition.and(getWorkloadAndCloudServiceNamesConditions(result));
        }
      }
    }

    if (!resourceTypes.isEmpty()) {
      condition = condition.and(CE_RECOMMENDATIONS.RESOURCETYPE.in(resourceTypes));
    }

    return condition;
  }

  private TableResult getWorkloadAndCloudServiceNamesTableResult(
      final String accountId, final List<QLCEViewTimeFilter> qlCEViewTimeFilters, final ViewIdCondition idCondition) {
    final BigQuery bigQuery = bigQueryService.get();
    final List<QLCEViewFilter> qlCEViewFilters =
        Collections.singletonList(viewParametersHelper.constructQLCEViewFilterFromViewIdCondition(idCondition));
    final String cloudProviderTableName =
        bigQueryHelper.getCloudProviderTableName(accountId, CLUSTER_TABLE_HOURLY_AGGREGRATED);
    final SelectQuery query = viewsQueryBuilder.getWorkloadAndCloudServiceNamesForLabels(
        qlCEViewFilters, qlCEViewTimeFilters, cloudProviderTableName);
    final QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to get labels recommendation for query {}", query, e);
      Thread.currentThread().interrupt();
      return null;
    }
    return result;
  }

  private ResultSet getWorkloadAndCloudServiceNamesResultSet(
      final List<QLCEViewTimeFilter> qlCEViewTimeFilters, final ViewIdCondition idCondition) {
    final List<QLCEViewFilter> qlCEViewFilters =
        Collections.singletonList(viewParametersHelper.constructQLCEViewFilterFromViewIdCondition(idCondition));
    final String cloudProviderTableName = CLUSTER_TABLE_HOURLY_AGGREGRATED_CH;
    final SelectQuery query = viewsQueryBuilder.getWorkloadAndCloudServiceNamesForLabels(
        qlCEViewFilters, qlCEViewTimeFilters, cloudProviderTableName);
    ResultSet resultSet;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
    } catch (SQLException e) {
      log.error("Failed to get labels recommendation for query {}", query, e);
      Thread.currentThread().interrupt();
      return null;
    }
    return resultSet;
  }

  private Condition getWorkloadAndCloudServiceNamesConditions(final TableResult result) {
    if (Objects.isNull(result)) {
      return DSL.noCondition();
    }
    final Set<String> workloadNames = new HashSet<>();
    final Set<String> cloudServiceNames = new HashSet<>();
    for (final FieldValueList row : result.iterateAll()) {
      String resourceName = null;
      String instanceType = null;
      for (final Field field : result.getSchema().getFields()) {
        if ("resourceName".equalsIgnoreCase(field.getName())) {
          resourceName = fetchStringValue(row, field);
        } else if ("instanceType".equalsIgnoreCase(field.getName())) {
          instanceType = fetchStringValue(row, field);
        }
      }
      if (Objects.nonNull(resourceName) && Objects.nonNull(instanceType)) {
        if (WORKLOAD_INSTANCE_TYPES.contains(instanceType)) {
          workloadNames.add(resourceName);
        } else if (CLOUD_SERVICE_INSTANCE_TYPES.contains(instanceType)) {
          cloudServiceNames.add(resourceName);
        }
      }
    }
    final Condition workloadCondition = getCondition(workloadNames, ResourceType.WORKLOAD);
    final Condition cloudServiceCondition = getCondition(cloudServiceNames, ResourceType.ECS_SERVICE);
    return DSL.or(workloadCondition, cloudServiceCondition);
  }

  private Condition getWorkloadAndCloudServiceNamesConditions(final ResultSet resultSet) {
    if (Objects.isNull(resultSet)) {
      return DSL.noCondition();
    }
    final Set<String> workloadNames = new HashSet<>();
    final Set<String> cloudServiceNames = new HashSet<>();
    try {
      while (resultSet.next()) {
        String resourceName = resultSet.getString("resourceName");
        String instanceType = resultSet.getString("instanceType");
        if (Objects.nonNull(resourceName) && Objects.nonNull(instanceType)) {
          if (WORKLOAD_INSTANCE_TYPES.contains(instanceType)) {
            workloadNames.add(resourceName);
          } else if (CLOUD_SERVICE_INSTANCE_TYPES.contains(instanceType)) {
            cloudServiceNames.add(resourceName);
          }
        }
      }
    } catch (Exception ignored) {
    }
    final Condition workloadCondition = getCondition(workloadNames, ResourceType.WORKLOAD);
    final Condition cloudServiceCondition = getCondition(cloudServiceNames, ResourceType.ECS_SERVICE);
    return DSL.or(workloadCondition, cloudServiceCondition);
  }

  private Condition getCondition(final Set<String> resourceNames, final ResourceType resourceType) {
    Condition resourceCondition = DSL.noCondition();
    if (!resourceNames.isEmpty()) {
      resourceCondition = resourceCondition.and(CE_RECOMMENDATIONS.NAME.in(resourceNames));
      resourceCondition = resourceCondition.and(CE_RECOMMENDATIONS.RESOURCETYPE.eq(resourceType.name()));
    }
    return resourceCondition;
  }

  private String fetchStringValue(final FieldValueList row, final Field field) {
    final Object value = row.get(field.getName()).getValue();
    if (Objects.nonNull(value)) {
      return value.toString();
    }
    return null;
  }

  private static String getRecommendationResourceType(final String fieldId) {
    String resourceType = null;
    switch (fieldId) {
      case WORKLOAD_NAME_FIELD_ID:
        resourceType = ResourceType.WORKLOAD.name();
        break;
      case INSTANCE_NAME_FIELD_ID:
        resourceType = ResourceType.NODE_POOL.name();
        break;
      case CLOUD_SERVICE_NAME_FIELD_ID:
        resourceType = ResourceType.ECS_SERVICE.name();
        break;
      default:
        break;
    }
    return resourceType;
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
    String normalizedFieldId = fieldId;
    if (isRecommendationResourceTypeField(fieldId)) {
      normalizedFieldId = CE_RECOMMENDATIONS.NAME.getName();
    }
    return normalizedFieldId;
  }

  private static boolean isRecommendationResourceTypeField(final String fieldId) {
    return RECOMMENDATION_RESOURCE_TYPE_COLUMNS.contains(fieldId);
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

  public static Condition getValidRecommendationFilter() {
    return CE_RECOMMENDATIONS.ISVALID
        .eq(true)
        // based on current-gen workload recommendation dataFetcher
        .and(CE_RECOMMENDATIONS.LASTPROCESSEDAT.greaterOrEqual(
            offsetDateTimeNow().truncatedTo(ChronoUnit.DAYS).minusDays(THRESHOLD_DAYS_TO_SHOW_RECOMMENDATION)))
        .and(nonDelegate());
  }

  private static Condition nonDelegate() {
    return CE_RECOMMENDATIONS.RESOURCETYPE.notEqual(ResourceType.WORKLOAD.name())
        .or(CE_RECOMMENDATIONS.RESOURCETYPE.eq(ResourceType.WORKLOAD.name())
                .and(CE_RECOMMENDATIONS.NAMESPACE.notIn("harness-delegate", "harness-delegate-ng")));
  }

  private RecommendationDetailsDTO getRecommendationDetails(
      RecommendationItemDTO item, final ResolutionEnvironment env) {
    try {
      return detailsQuery.recommendationDetails(item, OffsetDateTime.now().minusDays(7), OffsetDateTime.now(), 0L, env);
    } catch (Exception e) {
      log.error("Exception while fetching data for item: {}", item, e);
    }
    return null;
  }

  public K8sRecommendationFilterDTO applyAllowedPerspectiveFilter(
      String accountId, K8sRecommendationFilterDTO recommendationFilterDTO) {
    Set<String> allowedRecommendationIds = listAllowedRecommendationsIdAndPerspectives(accountId).keySet();
    K8sRecommendationFilterDTOBuilder recommendationFilterDTOBuilder = K8sRecommendationFilterDTO.builder();

    if (allowedRecommendationIds != null && allowedRecommendationIds.size() > 0) {
      Set<String> recommendationIds = new HashSet<>();
      if (!Lists.isNullOrEmpty(recommendationFilterDTO.getIds())) {
        recommendationIds = recommendationFilterDTO.getIds()
                                .stream()
                                .filter(recommendationId -> allowedRecommendationIds.contains(recommendationId))
                                .collect(Collectors.toSet());
      } else {
        recommendationIds.addAll(allowedRecommendationIds);
      }

      return recommendationFilterDTOBuilder.ids(recommendationIds.stream().collect(Collectors.toList()))
          .names(recommendationFilterDTO.getNames())
          .namespaces(recommendationFilterDTO.getNamespaces())
          .clusterNames(recommendationFilterDTO.getClusterNames())
          .resourceTypes(recommendationFilterDTO.getResourceTypes())
          .recommendationStates(recommendationFilterDTO.getRecommendationStates())
          .perspectiveFilters(recommendationFilterDTO.getPerspectiveFilters())
          .minSaving(recommendationFilterDTO.getMinSaving())
          .minCost(recommendationFilterDTO.getMinCost())
          .offset(recommendationFilterDTO.getOffset())
          .limit(recommendationFilterDTO.getLimit())
          .build();
    }

    // Setting recommendations id filter to be null since we dont have access to any recommendations
    return recommendationFilterDTOBuilder.ids(null)
        .names(recommendationFilterDTO.getNames())
        .namespaces(recommendationFilterDTO.getNamespaces())
        .clusterNames(recommendationFilterDTO.getClusterNames())
        .resourceTypes(recommendationFilterDTO.getResourceTypes())
        .recommendationStates(recommendationFilterDTO.getRecommendationStates())
        .perspectiveFilters(recommendationFilterDTO.getPerspectiveFilters())
        .minSaving(recommendationFilterDTO.getMinSaving())
        .minCost(recommendationFilterDTO.getMinCost())
        .offset(recommendationFilterDTO.getOffset())
        .limit(recommendationFilterDTO.getLimit())
        .build();
  }

  public HashMap<String, CEViewShortHand> listAllowedRecommendationsIdAndPerspectives(String accountIdentifier) {
    HashMap<String, CEViewShortHand> recommendationAndPerspective = new HashMap<>();
    List<CEViewShortHand> allowedPerspectives = getAllowedPerspectives(accountIdentifier);
    if (allowedPerspectives.size() > 0) {
      for (CEViewShortHand perspective : allowedPerspectives) {
        Condition condition = getRbacPerspectiveIndividualCondition(
            Collections.singletonList(
                QLCEViewFilterWrapper.builder()
                    .viewMetadataFilter(
                        QLCEViewMetadataFilter.builder().viewId(perspective.getUuid()).isPreview(false).build())
                    .build()),
            accountIdentifier);
        List<RecommendationItemDTO> recommendationItemDTOs =
            recommendationService.listAll(accountIdentifier, condition, 0L, Long.MAX_VALUE);
        recommendationItemDTOs.forEach(
            recommendationItemDTO -> recommendationAndPerspective.put(recommendationItemDTO.getId(), perspective));
      }
    }
    return recommendationAndPerspective;
  }

  public List<CEViewShortHand> getAllowedPerspectives(String accountIdentifier) {
    List<CEViewShortHand> perspectives = ceViewService.getAllViewsShortHand(accountIdentifier);
    Set<String> allowedFolderIds = rbacHelper.checkFolderIdsGivenPermission(accountIdentifier, null, null,
        perspectives.stream().map(ceView -> ceView.getFolderId()).collect(Collectors.toSet()), PERSPECTIVE_VIEW);

    if ((allowedFolderIds == null || allowedFolderIds.size() < 1)
        && (perspectives != null && perspectives.size() > 0)) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, PERSPECTIVE_VIEW, RESOURCE_FOLDER), WingsException.USER, null);
    }

    List<CEViewShortHand> allowedPerspectives = new ArrayList<>();
    if (allowedFolderIds != null && perspectives != null) {
      allowedPerspectives = perspectives.stream()
                                .filter(ceView -> allowedFolderIds.contains(ceView.getFolderId()))
                                .collect(Collectors.toList());
    }
    return allowedPerspectives;
  }

  private Condition getRbacPerspectiveIndividualCondition(
      List<QLCEViewFilterWrapper> perspectiveFilters, @NotNull String accountId) {
    Condition condition = getValidRecommendationFilter();
    final Condition perspectiveCondition = getPerspectiveCondition(perspectiveFilters, accountId);
    return condition.and(perspectiveCondition);
  }
}
