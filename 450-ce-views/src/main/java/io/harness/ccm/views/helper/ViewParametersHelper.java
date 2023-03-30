/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD_ID;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.BUSINESS_MAPPING;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.CLUSTER;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.COMMON;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.LABEL;
import static io.harness.ccm.views.graphql.QLCEViewAggregateOperation.MAX;
import static io.harness.ccm.views.graphql.QLCEViewAggregateOperation.MIN;
import static io.harness.ccm.views.graphql.QLCEViewFilterOperator.IN;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.AFTER;
import static io.harness.ccm.views.graphql.QLCEViewTimeGroupType.DAY;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.ECS_TASK_EC2;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.ECS_TASK_FARGATE;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.K8S_NODE;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.K8S_POD;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.K8S_POD_FARGATE;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.K8S_PV;
import static io.harness.ccm.views.utils.ClusterTableKeys.AVG_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.AVG_MEMORY_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.BILLING_AMOUNT;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLOUD_PROVIDER;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLOUD_SERVICE_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.CPU_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.CPU_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_CPU_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_CPU_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_MEMORY_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_MEMORY_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_MEMORY_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_CLOUD_PROVIDER;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_CLUSTER_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_CLUSTER_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_LAUNCH_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_LAUNCH_TYPE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_SERVICE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_SERVICE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_TASK;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_TASK_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_INSTANCE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_INSTANCE_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_INSTANCE_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_NAMESPACE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_NAMESPACE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_NODE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_NONE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_POD;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_PRODUCT;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_STORAGE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_WORKLOAD_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_WORKLOAD_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_WORKLOAD_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.INSTANCE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.INSTANCE_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.INSTANCE_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.LAUNCH_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.MEMORY_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.MEMORY_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.NAMESPACE;
import static io.harness.ccm.views.utils.ClusterTableKeys.PRICING_SOURCE;
import static io.harness.ccm.views.utils.ClusterTableKeys.TASK_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.WORKLOAD_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.WORKLOAD_TYPE;

import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewTimeGranularity;
import io.harness.ccm.views.entities.ViewVisualization;
import io.harness.ccm.views.graphql.QLCEInExpressionFilter;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewRule;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewSortType;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeGroupType;
import io.harness.ccm.views.graphql.QLCEViewTimeTruncGroupBy;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
@Singleton
public class ViewParametersHelper {
  @Inject private BusinessMappingDataSourceHelper businessMappingDataSourceHelper;
  @Inject private CEViewService viewService;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private BusinessMappingService businessMappingService;

  private static final String OTHERS = "Others";
  private static final String STANDARD_TIME_ZONE = "GMT";
  private static final long ONE_DAY_MILLIS = 86400000L;

  // ----------------------------------------------------------------------------------------------------------------
  //  Data sources related methods
  // ----------------------------------------------------------------------------------------------------------------
  public Set<ViewFieldIdentifier> getDataSources(
      final List<QLCEViewFilterWrapper> filters, final List<QLCEViewGroupBy> groupBy) {
    final Set<ViewFieldIdentifier> dataSources = new HashSet<>();
    dataSources.addAll(getDataSourcesFromFilters(filters));
    dataSources.addAll(getDataSourcesFromGroupBys(groupBy));
    dataSources.addAll(
        businessMappingDataSourceHelper.getBusinessMappingViewFieldIdentifiersFromIdFilters(getIdFilters(filters)));
    dataSources.addAll(
        businessMappingDataSourceHelper.getBusinessMappingViewFieldIdentifiersFromRuleFilters(getRuleFilters(filters)));
    dataSources.addAll(businessMappingDataSourceHelper.getBusinessMappingViewFieldIdentifiersFromGroupBys(groupBy));
    return dataSources;
  }

  public Set<ViewFieldIdentifier> getDataSourcesFromFilters(final List<QLCEViewFilterWrapper> filters) {
    final Set<ViewFieldIdentifier> dataSources = new HashSet<>();
    dataSources.addAll(getDataSourcesFromViewFilters(getIdFilters(filters)));
    dataSources.addAll(getDataSourcesFromViewRules(getRuleFilters(filters)));
    return dataSources;
  }

  public Set<ViewFieldIdentifier> getDataSourcesFromViewFilters(final List<QLCEViewFilter> qlCEViewFilters) {
    final Set<ViewFieldIdentifier> dataSources = new HashSet<>();
    for (final QLCEViewFilter qlCEViewFilter : qlCEViewFilters) {
      if (qlCEViewFilter.getField().getIdentifier() != ViewFieldIdentifier.BUSINESS_MAPPING) {
        dataSources.add(qlCEViewFilter.getField().getIdentifier());
      }
    }
    return dataSources;
  }

  public Set<ViewFieldIdentifier> getDataSourcesFromViewRules(final List<QLCEViewRule> qlCEViewRules) {
    final Set<ViewFieldIdentifier> dataSources = new HashSet<>();
    for (final QLCEViewRule qlCEViewRule : qlCEViewRules) {
      dataSources.addAll(getDataSourcesFromViewFilters(qlCEViewRule.getConditions()));
    }
    return dataSources;
  }

  public Set<ViewFieldIdentifier> getDataSourcesFromGroupBys(final List<QLCEViewGroupBy> groupBy) {
    final Set<ViewFieldIdentifier> dataSources = new HashSet<>();
    if (Objects.nonNull(groupBy)) {
      for (final QLCEViewGroupBy qlceViewGroupBy : groupBy) {
        if (Objects.nonNull(qlceViewGroupBy) && Objects.nonNull(qlceViewGroupBy.getEntityGroupBy())
            && qlceViewGroupBy.getEntityGroupBy().getIdentifier() != ViewFieldIdentifier.BUSINESS_MAPPING) {
          dataSources.add(qlceViewGroupBy.getEntityGroupBy().getIdentifier());
        }
      }
    }
    return dataSources;
  }

  public Set<ViewFieldIdentifier> getDataSourcesFromCEView(final CEView ceView) {
    final Set<ViewFieldIdentifier> dataSources = new HashSet<>();
    if (Objects.nonNull(ceView) && Objects.nonNull(ceView.getDataSources())) {
      ceView.getDataSources().forEach(viewFieldIdentifier -> {
        if (viewFieldIdentifier == ViewFieldIdentifier.BUSINESS_MAPPING) {
          dataSources.addAll(businessMappingDataSourceHelper.getBusinessMappingViewFieldIdentifiersFromViewRules(
              ceView.getViewRules()));
        } else {
          dataSources.add(viewFieldIdentifier);
        }
      });
    }
    return dataSources;
  }

  public boolean isClusterDataSources(final Set<ViewFieldIdentifier> dataSources) {
    return (dataSources.size() == 1 && dataSources.contains(CLUSTER))
        || (dataSources.size() == 2 && dataSources.contains(CLUSTER)
            && (dataSources.contains(COMMON) || dataSources.contains(LABEL)))
        || (dataSources.size() == 3 && dataSources.contains(CLUSTER) && dataSources.contains(COMMON)
            && dataSources.contains(LABEL));
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Filter related methods
  // ----------------------------------------------------------------------------------------------------------------
  public List<QLCEViewFilter> getIdFilters(List<QLCEViewFilterWrapper> filters) {
    return filters.stream()
        .map(QLCEViewFilterWrapper::getIdFilter)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public List<QLCEViewRule> getRuleFilters(@NotNull List<QLCEViewFilterWrapper> filters) {
    return filters.stream()
        .map(QLCEViewFilterWrapper::getRuleFilter)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public List<QLCEInExpressionFilter> getInExpressionFilters(@NotNull List<QLCEViewFilterWrapper> filters) {
    return filters.stream()
        .map(QLCEViewFilterWrapper::getInExpressionFilter)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public Optional<QLCEViewFilterWrapper> getViewMetadataFilter(List<QLCEViewFilterWrapper> filters) {
    return filters.stream().filter(f -> f.getViewMetadataFilter() != null).findFirst();
  }

  public QLCEViewFilter getFilterForInstanceDetails(List<QLCEViewGroupBy> groupByList) {
    List<String> entityGroupBy = groupByList.stream()
                                     .filter(groupBy -> groupBy.getEntityGroupBy() != null)
                                     .map(entry -> entry.getEntityGroupBy().getFieldName())
                                     .collect(Collectors.toList());
    String[] values;
    if (entityGroupBy.contains(GROUP_BY_NODE)) {
      values = new String[] {K8S_NODE};
    } else if (entityGroupBy.contains(GROUP_BY_STORAGE)) {
      values = new String[] {K8S_PV};
    } else if (entityGroupBy.contains(GROUP_BY_ECS_TASK_ID)) {
      values = new String[] {ECS_TASK_EC2, ECS_TASK_FARGATE};
    } else {
      values = new String[] {K8S_POD, K8S_POD_FARGATE};
    }

    return QLCEViewFilter.builder()
        .field(QLCEViewFieldInput.builder().fieldId(INSTANCE_TYPE).fieldName(INSTANCE_TYPE).identifier(CLUSTER).build())
        .operator(IN)
        .values(values)
        .build();
  }

  public List<QLCEViewFilter> getModifiedIdFilters(
      final List<QLCEViewFilter> idFilters, final boolean isClusterTableQuery) {
    final List<QLCEViewFilter> modifiedIdFilters = new ArrayList<>();
    idFilters.forEach(idFilter
        -> modifiedIdFilters.add(QLCEViewFilter.builder()
                                     .field(getModifiedQLCEViewFieldInput(idFilter.getField(), isClusterTableQuery))
                                     .operator(idFilter.getOperator())
                                     .values(idFilter.getValues())
                                     .build()));
    return modifiedIdFilters;
  }

  public QLCEViewFieldInput getModifiedQLCEViewFieldInput(
      final QLCEViewFieldInput viewFieldInput, final boolean isClusterTable) {
    QLCEViewFieldInput modifiedQLCEViewFieldInput = viewFieldInput;
    if (isClusterTable && COMMON.equals(viewFieldInput.getIdentifier())
        && "product".equals(viewFieldInput.getFieldId())) {
      modifiedQLCEViewFieldInput = QLCEViewFieldInput.builder()
                                       .fieldId("clustername")
                                       .fieldName("Cluster Name")
                                       .identifier(COMMON)
                                       .identifierName("Common")
                                       .build();
    }
    return modifiedQLCEViewFieldInput;
  }

  public List<QLCEViewFilter> addNotNullFilters(List<QLCEViewFilter> filters, List<QLCEViewGroupBy> groupByList) {
    List<QLCEViewFilter> updatedFilters = new ArrayList<>(filters);
    groupByList.forEach(groupBy -> {
      if (groupBy.getEntityGroupBy() != null && groupBy.getEntityGroupBy().getIdentifier() != BUSINESS_MAPPING) {
        switch (groupBy.getEntityGroupBy().getFieldName()) {
          case GROUP_BY_ECS_TASK_ID:
          case GROUP_BY_INSTANCE_ID:
          case GROUP_BY_INSTANCE_NAME:
          case GROUP_BY_INSTANCE_TYPE:
            break;
          default:
            updatedFilters.add(QLCEViewFilter.builder()
                                   .field(groupBy.getEntityGroupBy())
                                   .operator(QLCEViewFilterOperator.NOT_NULL)
                                   .values(new String[] {""})
                                   .build());
        }
      }
    });
    return updatedFilters;
  }

  public List<QLCEViewFilterWrapper> getModifiedFilters(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final boolean isClusterTableQuery) {
    final List<QLCEViewFilterWrapper> modifiedFilters = new ArrayList<>(filters);
    if (isClusterTableQuery) {
      final List<QLCEViewGroupBy> modifiedGroupBys = addAdditionalRequiredGroupBy(groupBy);
      final List<QLCEViewFilter> modifiedIdFilters =
          getModifiedIdFilters(addNotNullFilters(Collections.emptyList(), modifiedGroupBys), true);
      modifiedIdFilters.forEach(
          modifiedIdFilter -> modifiedFilters.add(QLCEViewFilterWrapper.builder().idFilter(modifiedIdFilter).build()));
    }
    return modifiedFilters;
  }

  public List<QLCEViewFilterWrapper> getFiltersForEntityStatsCostTrend(List<QLCEViewFilterWrapper> filters) {
    List<QLCEViewFilterWrapper> trendFilters =
        filters.stream().filter(f -> f.getTimeFilter() == null).collect(Collectors.toList());
    List<QLCEViewTimeFilter> timeFilters = viewsQueryHelper.getTimeFilters(filters);
    List<QLCEViewTimeFilter> trendTimeFilters = viewsQueryHelper.getTrendFilters(timeFilters);
    trendTimeFilters.forEach(
        timeFilter -> trendFilters.add(QLCEViewFilterWrapper.builder().timeFilter(timeFilter).build()));

    return trendFilters;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Perspective rules, fields and conditions related methods
  // ----------------------------------------------------------------------------------------------------------------
  public List<ViewRule> getViewRules(List<QLCEViewFilterWrapper> filters) {
    List<ViewRule> viewRuleList = new ArrayList<>();
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = getViewMetadataFilter(filters);

    List<QLCEViewRule> rules = AwsAccountFieldHelper.removeAccountNameFromAWSAccountRuleFilter(getRuleFilters(filters));
    if (!rules.isEmpty()) {
      for (QLCEViewRule rule : rules) {
        viewRuleList.add(convertQLCEViewRuleToViewRule(rule));
      }
    }

    if (viewMetadataFilter.isPresent()) {
      QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
      final String viewId = metadataFilter.getViewId();
      if (!metadataFilter.isPreview()) {
        CEView ceView = viewService.get(viewId);
        viewRuleList = ceView.getViewRules();
      }
    }

    return viewRuleList;
  }

  public List<ViewRule> getModifiedRuleFilters(final List<ViewRule> viewRules) {
    final List<ViewRule> modifiedRuleFilters = new ArrayList<>();
    viewRules.forEach(viewRule -> {
      if (!Lists.isNullOrEmpty(viewRule.getViewConditions())) {
        final List<ViewCondition> modifiedConditions = new ArrayList<>();
        viewRule.getViewConditions().forEach(viewCondition -> {
          final ViewIdCondition viewIdCondition = (ViewIdCondition) viewCondition;
          modifyViewIdCondition(viewIdCondition);
          modifiedConditions.add(viewIdCondition);
        });
        modifiedRuleFilters.add(ViewRule.builder().viewConditions(modifiedConditions).build());
      } else {
        modifiedRuleFilters.add(viewRule);
      }
    });
    return modifiedRuleFilters;
  }

  private void modifyViewIdCondition(final ViewIdCondition viewIdCondition) {
    final ViewField viewField = viewIdCondition.getViewField();
    if (COMMON.equals(viewField.getIdentifier()) && "product".equals(viewField.getFieldId())) {
      viewIdCondition.setViewField(ViewField.builder()
                                       .fieldId("clustername")
                                       .fieldName("Cluster Name")
                                       .identifier(COMMON)
                                       .identifierName("Common")
                                       .build());
    }
  }

  public List<ViewRule> convertQLCEViewRuleListToViewRuleList(@NotNull List<QLCEViewRule> ruleList) {
    return ruleList.stream().map(this::convertQLCEViewRuleToViewRule).collect(Collectors.toList());
  }

  public ViewRule convertQLCEViewRuleToViewRule(QLCEViewRule rule) {
    List<ViewCondition> conditionsList = convertIdFilterToViewCondition(rule.getConditions());
    return ViewRule.builder().viewConditions(conditionsList).build();
  }

  public List<ViewCondition> convertIdFilterToViewCondition(@NotNull List<QLCEViewFilter> viewFilters) {
    return viewFilters.stream().map(this::constructViewIdConditionFromQLCEViewFilter).collect(Collectors.toList());
  }

  private ViewIdCondition constructViewIdConditionFromQLCEViewFilter(QLCEViewFilter filter) {
    return ViewIdCondition.builder()
        .values(Arrays.asList(filter.getValues()))
        .viewField(getViewField(filter.getField()))
        .viewOperator(mapQLCEViewFilterOperatorToViewIdOperator(filter.getOperator()))
        .build();
  }

  private ViewIdOperator mapQLCEViewFilterOperatorToViewIdOperator(QLCEViewFilterOperator operator) {
    try {
      return ViewIdOperator.valueOf(operator.name());
    } catch (IllegalArgumentException ex) {
      log.warn("ViewIdOperator equivalent of QLCEViewFilterOperator=[{}] is not present.", operator.name(), ex);
      return null;
    }
  }

  public ViewField getViewField(QLCEViewFieldInput field) {
    return ViewField.builder()
        .fieldId(field.getFieldId())
        .fieldName(field.getFieldName())
        .identifier(field.getIdentifier())
        .identifierName(field.getIdentifier().getDisplayName())
        .build();
  }

  public QLCEViewFieldInput getViewFieldInput(ViewField field) {
    return QLCEViewFieldInput.builder()
        .fieldId(field.getFieldId())
        .fieldName(field.getFieldName())
        .identifier(field.getIdentifier())
        .identifierName(field.getIdentifier().getDisplayName())
        .build();
  }

  public QLCEViewFilter constructQLCEViewFilterFromViewIdCondition(ViewIdCondition viewIdCondition) {
    return QLCEViewFilter.builder()
        .values(viewIdCondition.getValues().toArray(new String[0]))
        .field(getQLCEViewFieldInput(viewIdCondition.getViewField()))
        .operator(mapViewIdOperatorToQLCEViewFilterOperator(viewIdCondition.getViewOperator()))
        .build();
  }

  private QLCEViewFieldInput getQLCEViewFieldInput(ViewField field) {
    return QLCEViewFieldInput.builder()
        .fieldId(field.getFieldId())
        .fieldName(field.getFieldName())
        .identifier(field.getIdentifier())
        .identifierName(field.getIdentifier().getDisplayName())
        .build();
  }

  private static QLCEViewFilterOperator mapViewIdOperatorToQLCEViewFilterOperator(ViewIdOperator operator) {
    try {
      return QLCEViewFilterOperator.valueOf(operator.name());
    } catch (IllegalArgumentException ex) {
      log.warn("QLCEViewFilterOperator equivalent of ViewIdOperator=[{}] is not present.", operator.name(), ex);
      return null;
    }
  }

  @NotNull
  public List<QLCEViewFieldInput> getInFieldsList(final List<String> fields) {
    final List<QLCEViewFieldInput> qlCEViewFieldInputs = new ArrayList<>();
    for (final String field : fields) {
      qlCEViewFieldInputs.add(QLCEViewFieldInput.builder().fieldId(field).fieldName(field.toLowerCase()).build());
    }
    return qlCEViewFieldInputs;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Pre aggregation related methods
  // ----------------------------------------------------------------------------------------------------------------
  public boolean areAggregationsValidForPreAggregation(List<QLCEViewAggregation> aggregateFunctions) {
    if (aggregateFunctions.isEmpty()) {
      return true;
    }
    return !aggregateFunctions.stream().anyMatch(aggregationFunction
        -> aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_CPU_LIMIT)
            || aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_CPU_REQUEST)
            || aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_MEMORY_LIMIT)
            || aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_MEMORY_REQUEST)
            || aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_CPU_UTILIZATION_VALUE)
            || aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_MEMORY_UTILIZATION_VALUE)
            || aggregationFunction.getColumnName().equalsIgnoreCase(AVG_CPU_UTILIZATION_VALUE)
            || aggregationFunction.getColumnName().equalsIgnoreCase(AVG_MEMORY_UTILIZATION_VALUE)
            || aggregationFunction.getColumnName().equalsIgnoreCase(CPU_REQUEST)
            || aggregationFunction.getColumnName().equalsIgnoreCase(CPU_LIMIT)
            || aggregationFunction.getColumnName().equalsIgnoreCase(MEMORY_REQUEST)
            || aggregationFunction.getColumnName().equalsIgnoreCase(MEMORY_LIMIT));
  }

  // Check for pod/pv/cloudservicename/taskid/launchtype
  public boolean isValidGroupByForPreAggregation(List<QLCEViewFieldInput> groupByList) {
    if (groupByList.isEmpty()) {
      return true;
    }
    return groupByList.stream().noneMatch(groupBy
        -> groupBy.getFieldId().equals(CLOUD_SERVICE_NAME) || groupBy.getFieldId().equals(TASK_ID)
            || groupBy.getFieldId().equals(LAUNCH_TYPE) || groupBy.getFieldId().equals(PRICING_SOURCE));
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Aws account related methods
  // ----------------------------------------------------------------------------------------------------------------
  public boolean isDataFilteredByAwsAccount(final List<QLCEViewFilter> idFilters) {
    return idFilters.stream()
        .filter(idFilter -> Objects.nonNull(idFilter) && Objects.nonNull(idFilter.getField()))
        .anyMatch(idFilter -> AWS_ACCOUNT_FIELD.equals(idFilter.getField().getFieldName()));
  }

  public boolean isDataGroupedByAwsAccount(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy) {
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = getViewMetadataFilter(filters);
    boolean defaultFieldCheck = false;
    boolean isGroupByEntityEmpty = groupBy.stream().noneMatch(g -> g.getEntityGroupBy() != null);
    if (viewMetadataFilter.isPresent()) {
      QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
      final String viewId = metadataFilter.getViewId();
      if (!metadataFilter.isPreview()) {
        CEView ceView = viewService.get(viewId);
        if (ceView.getViewVisualization() != null) {
          ViewVisualization viewVisualization = ceView.getViewVisualization();
          ViewField defaultGroupByField = viewVisualization.getGroupBy();
          defaultFieldCheck = defaultGroupByField.getFieldName().equals(AWS_ACCOUNT_FIELD)
              || defaultGroupByField.getFieldId().equals(AWS_ACCOUNT_FIELD_ID);
        }
      }
    }
    return (isGroupByEntityEmpty && defaultFieldCheck)
        || viewsQueryHelper.isGroupByFieldPresent(groupBy, AWS_ACCOUNT_FIELD)
        || viewsQueryHelper.isGroupByFieldIdPresent(groupBy, AWS_ACCOUNT_FIELD_ID);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Aggregation related methods
  // ----------------------------------------------------------------------------------------------------------------
  public List<QLCEViewAggregation> getModifiedAggregations(List<QLCEViewAggregation> aggregateFunctions) {
    List<QLCEViewAggregation> modifiedAggregations;
    if (aggregateFunctions == null) {
      return new ArrayList<>();
    }
    boolean isCostAggregationPresent =
        aggregateFunctions.stream().anyMatch(function -> function.getColumnName().equals(COST));
    if (isCostAggregationPresent) {
      modifiedAggregations = aggregateFunctions.stream()
                                 .filter(function -> !function.getColumnName().equals(COST))
                                 .collect(Collectors.toList());
      modifiedAggregations.add(QLCEViewAggregation.builder()
                                   .columnName(BILLING_AMOUNT)
                                   .operationType(QLCEViewAggregateOperation.SUM)
                                   .build());
    } else {
      modifiedAggregations = aggregateFunctions;
    }
    return modifiedAggregations;
  }

  public List<QLCEViewAggregation> getCostAggregation(final boolean isClusterPerspective) {
    final List<QLCEViewAggregation> costAggregation = new ArrayList<>();
    final String costColumn = isClusterPerspective ? BILLING_AMOUNT : COST;
    costAggregation.add(
        QLCEViewAggregation.builder().columnName(costColumn).operationType(QLCEViewAggregateOperation.SUM).build());
    return costAggregation;
  }

  public List<QLCEViewAggregation> getAggregationsForEntityStatsCostTrend(List<QLCEViewAggregation> aggregations) {
    List<QLCEViewAggregation> trendAggregations = new ArrayList<>(aggregations);
    trendAggregations.add(QLCEViewAggregation.builder().operationType(MAX).columnName("startTime").build());
    trendAggregations.add(QLCEViewAggregation.builder().operationType(MIN).columnName("startTime").build());
    return trendAggregations;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Sort related methods
  // ----------------------------------------------------------------------------------------------------------------
  public List<QLCEViewSortCriteria> getModifiedSort(List<QLCEViewSortCriteria> sortCriteria) {
    List<QLCEViewSortCriteria> modifiedSortCriteria;
    if (sortCriteria == null) {
      return new ArrayList<>();
    }
    boolean isSortByCostPresent = sortCriteria.stream().anyMatch(sort -> sort.getSortType() == QLCEViewSortType.COST);
    if (isSortByCostPresent) {
      QLCEViewSortCriteria costSort =
          sortCriteria.stream().filter(sort -> sort.getSortType() == QLCEViewSortType.COST).findFirst().get();
      modifiedSortCriteria = sortCriteria.stream()
                                 .filter(sort -> sort.getSortType() != QLCEViewSortType.COST)
                                 .collect(Collectors.toList());
      modifiedSortCriteria.add(QLCEViewSortCriteria.builder()
                                   .sortOrder(costSort.getSortOrder())
                                   .sortType(QLCEViewSortType.CLUSTER_COST)
                                   .build());
    } else {
      modifiedSortCriteria = sortCriteria;
    }
    return modifiedSortCriteria;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Group by related methods
  // ----------------------------------------------------------------------------------------------------------------

  /*
   * This method is overriding the Group By passed by the UI with the defaults selected by user while creating the View
   * */
  public List<QLCEViewGroupBy> getModifiedGroupBy(List<QLCEViewGroupBy> groupByList, ViewField defaultGroupByField,
      ViewTimeGranularity defaultTimeGranularity, boolean isTimeTruncGroupByRequired, boolean skipDefaultGroupBy) {
    if (groupByList == null) {
      return new ArrayList<>();
    }
    List<QLCEViewGroupBy> modifiedGroupBy = new ArrayList<>();
    Optional<QLCEViewGroupBy> timeTruncGroupBy =
        groupByList.stream().filter(g -> g.getTimeTruncGroupBy() != null).findFirst();

    List<QLCEViewGroupBy> entityGroupBy =
        groupByList.stream().filter(g -> g.getEntityGroupBy() != null).collect(Collectors.toList());

    if (timeTruncGroupBy.isPresent()) {
      modifiedGroupBy.add(timeTruncGroupBy.get());
    } else if (isTimeTruncGroupByRequired) {
      modifiedGroupBy.add(
          QLCEViewGroupBy.builder()
              .timeTruncGroupBy(QLCEViewTimeTruncGroupBy.builder()
                                    .resolution(mapViewTimeGranularityToQLCEViewTimeGroupType(defaultTimeGranularity))
                                    .build())
              .build());
    }

    if (!entityGroupBy.isEmpty()) {
      modifiedGroupBy.addAll(entityGroupBy);
    } else {
      if (!skipDefaultGroupBy) {
        modifiedGroupBy.add(QLCEViewGroupBy.builder().entityGroupBy(getViewFieldInput(defaultGroupByField)).build());
      }
    }
    return modifiedGroupBy;
  }

  public List<QLCEViewGroupBy> addAdditionalRequiredGroupBy(List<QLCEViewGroupBy> groupByList) {
    List<QLCEViewGroupBy> modifiedGroupBy = new ArrayList<>();
    groupByList.forEach(groupBy -> {
      if (groupBy.getEntityGroupBy() != null) {
        switch (groupBy.getEntityGroupBy().getFieldName()) {
          case GROUP_BY_WORKLOAD_ID:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_NAMESPACE, NAMESPACE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_WORKLOAD_TYPE, WORKLOAD_TYPE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_WORKLOAD_NAME, WORKLOAD_NAME, CLUSTER));
            break;
          case GROUP_BY_NAMESPACE_ID:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_NAMESPACE, NAMESPACE, CLUSTER));
            break;
          case GROUP_BY_ECS_SERVICE_ID:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_LAUNCH_TYPE, LAUNCH_TYPE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_SERVICE, CLOUD_SERVICE_NAME, CLUSTER));
            break;
          case GROUP_BY_ECS_LAUNCH_TYPE_ID:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_LAUNCH_TYPE, LAUNCH_TYPE, CLUSTER));
            break;
          case GROUP_BY_ECS_TASK_ID:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_LAUNCH_TYPE, LAUNCH_TYPE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_SERVICE, CLOUD_SERVICE_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_TASK, TASK_ID, CLUSTER));
            break;
          case GROUP_BY_PRODUCT:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            break;
          case GROUP_BY_NODE:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_ID, CLUSTER_ID, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_ID, INSTANCE_ID, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_TYPE, INSTANCE_TYPE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_NAME, INSTANCE_NAME, CLUSTER));
            break;
          case GROUP_BY_POD:
          case GROUP_BY_STORAGE:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_ID, CLUSTER_ID, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_NAMESPACE, NAMESPACE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_WORKLOAD_NAME, WORKLOAD_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLOUD_PROVIDER, CLOUD_PROVIDER, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_ID, INSTANCE_ID, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_TYPE, INSTANCE_TYPE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_NAME, INSTANCE_NAME, CLUSTER));
            break;
          case GROUP_BY_NONE:
            break;
          default:
            modifiedGroupBy.add(groupBy);
        }
      } else {
        modifiedGroupBy.add(groupBy);
      }
    });

    return modifiedGroupBy;
  }

  private QLCEViewGroupBy getGroupBy(String fieldName, String fieldId, ViewFieldIdentifier identifier) {
    return QLCEViewGroupBy.builder()
        .entityGroupBy(
            QLCEViewFieldInput.builder().fieldId(fieldId).fieldName(fieldName).identifier(identifier).build())
        .build();
  }

  public QLCEViewTimeGroupType mapViewTimeGranularityToQLCEViewTimeGroupType(ViewTimeGranularity timeGranularity) {
    if (timeGranularity.equals(ViewTimeGranularity.DAY)) {
      return DAY;
    } else if (timeGranularity.equals(ViewTimeGranularity.MONTH)) {
      return QLCEViewTimeGroupType.MONTH;
    }
    return null;
  }

  public boolean isGroupByHour(List<QLCEViewGroupBy> groupBy) {
    QLCEViewTimeTruncGroupBy groupByTime = getGroupByTime(groupBy);
    return groupByTime != null && groupByTime.getResolution() != null
        && groupByTime.getResolution() == QLCEViewTimeGroupType.HOUR;
  }

  public QLCEViewTimeTruncGroupBy getGroupByTime(List<QLCEViewGroupBy> groupBy) {
    if (groupBy != null) {
      Optional<QLCEViewTimeTruncGroupBy> first =
          groupBy.stream().map(QLCEViewGroupBy::getTimeTruncGroupBy).filter(Objects::nonNull).findFirst();
      return first.orElse(null);
    }
    return null;
  }

  public List<QLCEViewGroupBy> getTimeTruncGroupBys(final List<QLCEViewGroupBy> groupByList) {
    return groupByList.stream()
        .filter(groupBy -> Objects.nonNull(groupBy.getTimeTruncGroupBy()))
        .collect(Collectors.toList());
  }

  public String getEntityGroupByFieldName(final List<QLCEViewGroupBy> groupBy) {
    String entityGroupByFieldName = OTHERS;
    final Optional<String> groupByFieldName = groupBy.stream()
                                                  .filter(entry -> Objects.nonNull(entry.getEntityGroupBy()))
                                                  .map(entry -> entry.getEntityGroupBy().getFieldName())
                                                  .findFirst();
    if (groupByFieldName.isPresent()) {
      entityGroupByFieldName = "No " + groupByFieldName.get();
    }
    return entityGroupByFieldName;
  }

  public String getEntityGroupByFieldId(final List<QLCEViewGroupBy> groupBy) {
    String entityGroupByFieldName = OTHERS;
    final Optional<String> groupByFieldId = groupBy.stream()
                                                .filter(entry -> Objects.nonNull(entry.getEntityGroupBy()))
                                                .map(entry -> entry.getEntityGroupBy().getFieldId())
                                                .findFirst();
    if (groupByFieldId.isPresent()) {
      entityGroupByFieldName = groupByFieldId.get();
    }
    return entityGroupByFieldName;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods used to determine table to be used
  // ----------------------------------------------------------------------------------------------------------------
  public boolean shouldUseHourlyData(List<QLCEViewTimeFilter> timeFilters) {
    if (!timeFilters.isEmpty()) {
      QLCEViewTimeFilter startTimeFilter =
          timeFilters.stream().filter(timeFilter -> timeFilter.getOperator().equals(AFTER)).findFirst().orElse(null);

      if (startTimeFilter != null) {
        long startTime = startTimeFilter.getValue().longValue();
        ZoneId zoneId = ZoneId.of(STANDARD_TIME_ZONE);
        LocalDate today = LocalDate.now(zoneId);
        ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
        long cutoffTime = zdtStart.toEpochSecond() * 1000 - 7 * ONE_DAY_MILLIS;
        return startTime >= cutoffTime;
      }
    }
    return false;
  }

  public boolean isClusterTableQuery(
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, ViewQueryParams queryParams) {
    return (queryParams.isClusterQuery() || isClusterPerspective(filters, groupBy))
        && queryParams.getAccountId() != null;
  }

  public boolean isClusterPerspective(final List<QLCEViewFilterWrapper> filters, final List<QLCEViewGroupBy> groupBy) {
    final Set<ViewFieldIdentifier> dataSources = getDataSources(filters, groupBy);
    final Optional<QLCEViewFilterWrapper> viewMetadataFilter = getViewMetadataFilter(filters);
    if (viewMetadataFilter.isPresent()) {
      final QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
      if (!metadataFilter.isPreview()) {
        final CEView ceView = viewService.get(metadataFilter.getViewId());
        dataSources.addAll(getDataSourcesFromCEView(ceView));
      }
    }
    return isClusterDataSources(dataSources);
  }

  public boolean isPodQuery(List<QLCEViewGroupBy> groupByList) {
    List<QLCEViewFieldInput> entityGroupBy = groupByList.stream()
                                                 .map(QLCEViewGroupBy::getEntityGroupBy)
                                                 .filter(Objects::nonNull)
                                                 .collect(Collectors.toList());
    return entityGroupBy.stream().anyMatch(groupBy -> groupBy.getFieldName().equals(GROUP_BY_POD));
  }

  public boolean isInstanceDetailsQuery(List<QLCEViewGroupBy> groupByList) {
    List<QLCEViewFieldInput> entityGroupBy = groupByList.stream()
                                                 .map(QLCEViewGroupBy::getEntityGroupBy)
                                                 .filter(Objects::nonNull)
                                                 .collect(Collectors.toList());
    return entityGroupBy.stream().anyMatch(groupBy
        -> groupBy.getFieldName().equals(GROUP_BY_NODE) || groupBy.getFieldName().equals(GROUP_BY_POD)
            || groupBy.getFieldName().equals(GROUP_BY_STORAGE));
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Cost category related methods
  // ----------------------------------------------------------------------------------------------------------------

  public List<String> getBusinessMappingIds(List<QLCEViewFilterWrapper> filters, String groupByBusinessMappingId) {
    Set<String> businessMappingIds = new HashSet<>();
    List<ViewRule> viewRules = getViewRules(filters);
    businessMappingIds.addAll(viewsQueryHelper.getBusinessMappingIdsFromViewRules(viewRules));
    businessMappingIds.addAll(new HashSet<>(viewsQueryHelper.getBusinessMappingIdsFromFilters(filters)));
    if (Objects.nonNull(groupByBusinessMappingId) && !groupByBusinessMappingId.isEmpty()) {
      businessMappingIds.add(groupByBusinessMappingId);
    }
    return new ArrayList<>(businessMappingIds);
  }

  public List<BusinessMapping> getSharedCostBusinessMappings(final List<String> businessMappingIds) {
    final List<BusinessMapping> sharedCostBusinessMappings = new ArrayList<>();
    if (!businessMappingIds.isEmpty()) {
      businessMappingIds.forEach(businessMappingId -> {
        final BusinessMapping businessMapping = businessMappingService.get(businessMappingId);
        if (businessMapping != null && businessMapping.getSharedCosts() != null) {
          sharedCostBusinessMappings.add(businessMapping);
        }
      });
    }
    return sharedCostBusinessMappings;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Miscellaneous methods
  // ----------------------------------------------------------------------------------------------------------------
  public long getStartTimeForTrendFilters(List<QLCEViewFilterWrapper> filters) {
    List<QLCEViewTimeFilter> timeFilters = viewsQueryHelper.getTimeFilters(filters);
    List<QLCEViewTimeFilter> trendTimeFilters = viewsQueryHelper.getTrendFilters(timeFilters);

    Optional<QLCEViewTimeFilter> startTimeFilter =
        trendTimeFilters.stream().filter(filter -> filter.getOperator() == AFTER).findFirst();
    if (startTimeFilter.isPresent()) {
      return startTimeFilter.get().getValue().longValue();
    } else {
      throw new InvalidRequestException("Start time cannot be null");
    }
  }

  public String getInstanceType(Set<String> instanceTypes) {
    if (instanceTypes.contains(K8S_NODE)) {
      return K8S_NODE;
    } else if (instanceTypes.contains(K8S_PV)) {
      return K8S_PV;
    } else {
      return K8S_POD;
    }
  }

  public Instant getStartInstantForForecastCost() {
    return Instant.ofEpochMilli(viewsQueryHelper.getStartOfCurrentDay());
  }

  @Nullable
  public String getNullValueField(final List<QLCEViewGroupBy> entityGroupBy, final List<List<String>> inValues) {
    String nullValueField = null;
    final String groupByName = getEntityGroupByFieldName(entityGroupBy);
    for (final List<String> values : inValues) {
      if (values.contains(groupByName)) {
        final Optional<String> groupByFieldId = entityGroupBy.stream()
                                                    .filter(entry -> Objects.nonNull(entry.getEntityGroupBy()))
                                                    .map(entry -> entry.getEntityGroupBy().getFieldId())
                                                    .findFirst();
        if (groupByFieldId.isPresent()) {
          nullValueField = groupByFieldId.get();
        }
        break;
      }
    }
    return nullValueField;
  }

  public String getFormattedDate(Instant instant, String datePattern) {
    return instant.atZone(ZoneId.of("GMT")).format(DateTimeFormatter.ofPattern(datePattern));
  }
}
