/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_INSTANCE_TYPE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_SERVICE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_USAGE_TYPE_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AZURE_METER_CATEGORY;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AZURE_RESOURCE_GROUP;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AZURE_SUBSCRIPTION_GUID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.CLUSTER_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.GCP_PRODUCT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.GCP_PROJECT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.GCP_SKU_DESCRIPTION_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.NAMESPACE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.REGION_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.WORKLOAD_NAME_FIELD_ID;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.BUSINESS_MAPPING;

import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.commons.entities.CCMFilter;
import io.harness.ccm.commons.entities.CCMGroupBy;
import io.harness.ccm.commons.entities.CCMNumberFilter;
import io.harness.ccm.commons.entities.CCMOperator;
import io.harness.ccm.commons.entities.CCMStringFilter;
import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dto.PerspectiveQueryDTO;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewVisualization;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.exception.InvalidAccessRequestException;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;

public class PerspectiveToAnomalyQueryHelper {
  @Inject ViewsQueryBuilder viewsQueryBuilder;
  @Inject BusinessMappingService businessMappingService;
  @Inject BusinessMappingDataSourceHelper businessMappingDataSourceHelper;

  public List<CCMGroupBy> convertGroupBy(@NonNull List<QLCEViewGroupBy> groupByList) {
    List<CCMGroupBy> convertedGroupByList = new ArrayList<>();
    groupByList.forEach(groupBy -> {
      if (groupBy.getEntityGroupBy() != null) {
        switch (groupBy.getEntityGroupBy().getFieldId()) {
          case CLUSTER_NAME_FIELD_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.CLUSTER_NAME).build());
            break;
          case NAMESPACE_FIELD_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.NAMESPACE).build());
            break;
          case WORKLOAD_NAME_FIELD_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.WORKLOAD).build());
            break;
          case GCP_PROJECT_FIELD_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.GCP_PROJECT).build());
            break;
          case GCP_PRODUCT_FIELD_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.GCP_PRODUCT).build());
            break;
          case GCP_SKU_DESCRIPTION_FIELD_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.GCP_SKU_DESCRIPTION).build());
            break;
          case AWS_ACCOUNT_FIELD_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.AWS_ACCOUNT).build());
            break;
          case AWS_SERVICE_FIELD_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.AWS_SERVICE).build());
            break;
          case AWS_INSTANCE_TYPE_FIELD_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.AWS_INSTANCE_TYPE).build());
            break;
          case AWS_USAGE_TYPE_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.AWS_USAGE_TYPE).build());
            break;
          case AZURE_SUBSCRIPTION_GUID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.AZURE_SUBSCRIPTION_GUID).build());
            break;
          case AZURE_RESOURCE_GROUP:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.AZURE_RESOURCE_GROUP_NAME).build());
            break;
          case AZURE_METER_CATEGORY:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.AZURE_METER_CATEGORY).build());
            break;
          case REGION_FIELD_ID:
            convertedGroupByList.add(CCMGroupBy.builder().groupByField(CCMField.REGION).build());
            break;
          default:
        }
      }
    });
    return convertedGroupByList;
  }

  public CCMFilter convertFilters(List<QLCEViewFilterWrapper> filters) {
    List<CCMStringFilter> stringFilters = new ArrayList<>();
    List<CCMTimeFilter> timeFilters = new ArrayList<>();

    filters.forEach(filter -> {
      if (filter.getIdFilter() != null) {
        switch (filter.getIdFilter().getField().getFieldId()) {
          case CLUSTER_NAME_FIELD_ID:
            stringFilters.add(buildStringFilter(
                CCMField.CLUSTER_NAME, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          case NAMESPACE_FIELD_ID:
            stringFilters.add(buildStringFilter(
                CCMField.NAMESPACE, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          case WORKLOAD_NAME_FIELD_ID:
            stringFilters.add(buildStringFilter(
                CCMField.WORKLOAD, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          case GCP_PROJECT_FIELD_ID:
            stringFilters.add(buildStringFilter(
                CCMField.GCP_PROJECT, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          case GCP_PRODUCT_FIELD_ID:
            stringFilters.add(buildStringFilter(
                CCMField.GCP_PRODUCT, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          case GCP_SKU_DESCRIPTION_FIELD_ID:
            stringFilters.add(buildStringFilter(
                CCMField.GCP_SKU_DESCRIPTION, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          case AWS_ACCOUNT_FIELD_ID:
            stringFilters.add(buildStringFilter(CCMField.AWS_ACCOUNT,
                AwsAccountFieldHelper.removeAwsAccountNameFromValues(Arrays.asList(filter.getIdFilter().getValues()))
                    .toArray(new String[0]),
                filter.getIdFilter().getOperator()));
            break;
          case AWS_SERVICE_FIELD_ID:
            stringFilters.add(buildStringFilter(
                CCMField.AWS_SERVICE, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          case AWS_INSTANCE_TYPE_FIELD_ID:
            stringFilters.add(buildStringFilter(
                CCMField.AWS_INSTANCE_TYPE, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          case AWS_USAGE_TYPE_ID:
            stringFilters.add(buildStringFilter(
                CCMField.AWS_USAGE_TYPE, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          case AZURE_SUBSCRIPTION_GUID:
            stringFilters.add(buildStringFilter(CCMField.AZURE_SUBSCRIPTION_GUID, filter.getIdFilter().getValues(),
                filter.getIdFilter().getOperator()));
            break;
          case AZURE_RESOURCE_GROUP:
            stringFilters.add(buildStringFilter(CCMField.AZURE_RESOURCE_GROUP_NAME, filter.getIdFilter().getValues(),
                filter.getIdFilter().getOperator()));
            break;
          case AZURE_METER_CATEGORY:
            stringFilters.add(buildStringFilter(
                CCMField.AZURE_METER_CATEGORY, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          case REGION_FIELD_ID:
            stringFilters.add(buildStringFilter(
                CCMField.REGION, filter.getIdFilter().getValues(), filter.getIdFilter().getOperator()));
            break;
          default:
            if (filter.getIdFilter().getField().getIdentifier() == BUSINESS_MAPPING) {
              List<ViewRule> businessMappingRules = new ArrayList<>();
              BusinessMapping businessMapping =
                  businessMappingService.get(filter.getIdFilter().getField().getFieldId());
              List<CostTarget> costTargets = businessMapping.getCostTargets();
              if (costTargets != null) {
                businessMappingRules.addAll(
                    businessMappingDataSourceHelper.getBusinessMappingRules(businessMapping, filter.getIdFilter()));
              }
              List<CCMFilter> businessMappingConvertedFilters = getConvertedRulesForPerspective(businessMappingRules);
              businessMappingConvertedFilters.forEach(businessMappingConvertedFilter
                  -> stringFilters.addAll(businessMappingConvertedFilter.getStringFilters()));
            }
        }
      } else if (filter.getTimeFilter() != null) {
        timeFilters.add(CCMTimeFilter.builder()
                            .operator(convertFilterOperator(filter.getTimeFilter().getOperator()))
                            .timestamp(filter.getTimeFilter().getValue().longValue())
                            .build());
      }
    });

    return CCMFilter.builder()
        .stringFilters(stringFilters)
        .numericFilters(Collections.emptyList())
        .timeFilters(timeFilters)
        .build();
  }

  public CCMFilter covertGroupByToFilter(List<CCMGroupBy> groupBys) {
    List<CCMStringFilter> stringFilters = new ArrayList<>();
    groupBys.forEach(groupBy
        -> stringFilters.add(CCMStringFilter.builder()
                                 .operator(CCMOperator.NOT_NULL)
                                 .values(Collections.emptyList())
                                 .field(groupBy.getGroupByField())
                                 .build()));
    return CCMFilter.builder()
        .stringFilters(stringFilters)
        .timeFilters(Collections.emptyList())
        .numericFilters(Collections.emptyList())
        .build();
  }

  public CCMFilter convertBusinessMappingGroupByToFilters(List<QLCEViewGroupBy> businessMappingGroupBy) {
    List<CCMStringFilter> stringFilters = new ArrayList<>();
    businessMappingGroupBy.forEach(groupBy -> {
      List<ViewRule> businessMappingRules = new ArrayList<>();
      BusinessMapping businessMapping = businessMappingService.get(groupBy.getEntityGroupBy().getFieldId());
      List<CostTarget> costTargets = businessMapping.getCostTargets();
      if (costTargets != null) {
        costTargets.forEach(costTarget -> businessMappingRules.addAll(costTarget.getRules()));
      }
      businessMappingRules.addAll(businessMappingDataSourceHelper.getSharedCostTargetRules(businessMapping));
      List<CCMFilter> businessMappingConvertedFilters = getConvertedRulesForPerspective(businessMappingRules);
      businessMappingConvertedFilters.forEach(
          businessMappingConvertedFilter -> stringFilters.addAll(businessMappingConvertedFilter.getStringFilters()));
    });

    return CCMFilter.builder()
        .stringFilters(stringFilters)
        .timeFilters(Collections.emptyList())
        .numericFilters(Collections.emptyList())
        .build();
  }

  public CCMFilter getConvertedFiltersForPerspective(CEView view, PerspectiveQueryDTO perspectiveQuery) {
    if (perspectiveQuery == null) {
      return null;
    }
    List<CCMGroupBy> convertedGroupBy = new ArrayList<>();
    List<QLCEViewGroupBy> businessMappingGroupBy = new ArrayList<>();
    if (perspectiveQuery.getGroupBy() != null) {
      businessMappingGroupBy.addAll(perspectiveQuery.getGroupBy()
                                        .stream()
                                        .filter(groupBy
                                            -> groupBy.getEntityGroupBy() != null
                                                && groupBy.getEntityGroupBy().getIdentifier() == BUSINESS_MAPPING)
                                        .collect(Collectors.toList()));
    }

    convertedGroupBy =
        convertGroupBy(perspectiveQuery.getGroupBy() != null ? perspectiveQuery.getGroupBy() : Collections.emptyList());
    if (convertedGroupBy.isEmpty()) {
      List<QLCEViewGroupBy> defaultGroupBys = getPerspectiveDefaultGroupBy(view);
      businessMappingGroupBy.addAll(defaultGroupBys.stream()
                                        .filter(groupBy
                                            -> groupBy.getEntityGroupBy() != null
                                                && groupBy.getEntityGroupBy().getIdentifier() == BUSINESS_MAPPING)
                                        .collect(Collectors.toList()));
      convertedGroupBy = convertGroupBy(defaultGroupBys);
    }
    List<CCMFilter> allFilters = new ArrayList<>();
    // Filters from group by
    allFilters.add(covertGroupByToFilter(convertedGroupBy));
    // Filters from Cost categories group by
    allFilters.add(convertBusinessMappingGroupByToFilters(businessMappingGroupBy));
    // Filters from perspective query
    allFilters.add(convertFilters(
        perspectiveQuery.getFilters() != null ? perspectiveQuery.getFilters() : Collections.emptyList()));

    return combineFilters(allFilters);
  }

  public List<CCMFilter> getConvertedRulesForPerspective(CEView view) {
    return getConvertedRulesForPerspective(view.getViewRules());
  }

  public List<CCMFilter> getConvertedRulesForPerspective(List<ViewRule> viewRules) {
    List<CCMFilter> convertedRuleFilters = new ArrayList<>();
    if (viewRules != null) {
      for (ViewRule rule : viewRules) {
        List<QLCEViewFilterWrapper> ruleFilters = new ArrayList<>();
        for (ViewCondition condition : rule.getViewConditions()) {
          ruleFilters.add(QLCEViewFilterWrapper.builder()
                              .idFilter(viewsQueryBuilder.mapConditionToFilter((ViewIdCondition) condition))
                              .build());
        }
        convertedRuleFilters.add(convertFilters(ruleFilters));
      }
    }

    return convertedRuleFilters;
  }

  private CCMFilter combineFilters(List<CCMFilter> filters) {
    List<CCMStringFilter> stringFilters = new ArrayList<>();
    List<CCMNumberFilter> numberFilters = new ArrayList<>();
    List<CCMTimeFilter> timeFilters = new ArrayList<>();

    filters.forEach(filter -> {
      stringFilters.addAll(filter.getStringFilters());
      numberFilters.addAll(filter.getNumericFilters());
      timeFilters.addAll(filter.getTimeFilters());
    });

    return CCMFilter.builder()
        .stringFilters(stringFilters)
        .numericFilters(numberFilters)
        .timeFilters(timeFilters)
        .build();
  }

  public List<QLCEViewGroupBy> getPerspectiveDefaultGroupBy(CEView view) {
    List<QLCEViewGroupBy> defaultGroupBy = new ArrayList<>();
    if (view.getViewVisualization() != null) {
      ViewVisualization viewVisualization = view.getViewVisualization();
      ViewField defaultGroupByField = viewVisualization.getGroupBy();
      defaultGroupBy.add(QLCEViewGroupBy.builder()
                             .entityGroupBy(QLCEViewFieldInput.builder()
                                                .fieldId(defaultGroupByField.getFieldId())
                                                .fieldName(defaultGroupByField.getFieldName())
                                                .identifier(defaultGroupByField.getIdentifier())
                                                .identifierName(defaultGroupByField.getIdentifierName())
                                                .build())
                             .build());
    }
    return defaultGroupBy;
  }

  public List<QLCEViewFilterWrapper> getPerspectiveDefaultFilters(CEView view) {
    List<QLCEViewFilterWrapper> defaultFilters = new ArrayList<>();
    List<ViewRule> viewRules = view.getViewRules();

    for (ViewRule rule : viewRules) {
      rule.getViewConditions().forEach(condition
          -> defaultFilters.add(QLCEViewFilterWrapper.builder()
                                    .idFilter(viewsQueryBuilder.mapConditionToFilter((ViewIdCondition) condition))
                                    .build()));
    }
    return defaultFilters;
  }

  private CCMStringFilter buildStringFilter(CCMField field, String[] values, QLCEViewFilterOperator operator) {
    return CCMStringFilter.builder()
        .field(field)
        .values(Arrays.asList(values))
        .operator(convertFilterOperator(operator))
        .build();
  }

  private CCMOperator convertFilterOperator(QLCEViewFilterOperator operator) {
    switch (operator) {
      case IN:
        return CCMOperator.IN;
      case NOT_IN:
        return CCMOperator.NOT_IN;
      case EQUALS:
        return CCMOperator.EQUALS;
      case LIKE:
        return CCMOperator.LIKE;
      case NULL:
        return CCMOperator.NULL;
      case NOT_NULL:
        return CCMOperator.NOT_NULL;
      default:
        throw new InvalidAccessRequestException("Filter operator not supported");
    }
  }

  private CCMOperator convertFilterOperator(QLCEViewTimeFilterOperator operator) {
    switch (operator) {
      case AFTER:
        return CCMOperator.AFTER;
      case BEFORE:
        return CCMOperator.BEFORE;
      default:
        throw new InvalidAccessRequestException("Filter operator not supported");
    }
  }
}
