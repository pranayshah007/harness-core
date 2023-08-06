/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.AWS;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.AZURE;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.CLUSTER;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.GCP;
import static io.harness.ccm.views.entities.ViewIdOperator.EQUALS;
import static io.harness.ccm.views.entities.ViewIdOperator.IN;
import static io.harness.ccm.views.entities.ViewIdOperator.NOT_IN;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.AFTER;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.BEFORE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.commons.constants.ViewFieldConstants;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dao.CEReportScheduleDao;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.dao.CEViewFolderDao;
import io.harness.ccm.views.dto.CEViewShortHand;
import io.harness.ccm.views.dto.DefaultViewIdDto;
import io.harness.ccm.views.dto.LinkedPerspectives;
import io.harness.ccm.views.dto.LinkedPerspectives.LinkedPerspectivesBuilder;
import io.harness.ccm.views.dto.ViewTimeRangeDto;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.ViewChartType;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewPreferences;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.entities.ViewTimeGranularity;
import io.harness.ccm.views.entities.ViewTimeRange;
import io.harness.ccm.views.entities.ViewTimeRangeType;
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.entities.ViewVisualization;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewField;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.graphql.ViewCostData;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.helper.ViewFilterBuilderHelper;
import io.harness.ccm.views.helper.ViewTimeRangeHelper;
import io.harness.ccm.views.service.CEViewPreferenceService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewCustomFieldService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.exception.InvalidRequestException;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jooq.tools.StringUtils;
import org.springframework.util.CollectionUtils;

@Slf4j
@Singleton
@OwnedBy(CE)
public class CEViewServiceImpl implements CEViewService {
  private static final String VIEW_NAME_DUPLICATE_EXCEPTION = "Perspective with given name already exists";
  private static final String CLONE_NAME_DUPLICATE_EXCEPTION = "A clone for this perspective already exists";
  private static final String INVALID_COST_CATEGORY_ID_EXCEPTION =
      "The cost category/business mapping id %s is invalid";
  private static final String VIEW_LIMIT_REACHED_EXCEPTION =
      "Maximum allowed custom views limit(1000) has been reached";

  private static final String DEFAULT_AZURE_VIEW_NAME = "Azure";
  private static final String DEFAULT_AZURE_FIELD_ID = "azureSubscriptionGuid";
  private static final String DEFAULT_AZURE_FIELD_NAME = "Subscription id";

  private static final String DEFAULT_AWS_VIEW_NAME = "AWS";
  private static final String DEFAULT_AWS_FIELD_ID = "awsUsageAccountId";
  private static final String DEFAULT_AWS_FIELD_NAME = "Account";

  private static final String DEFAULT_GCP_VIEW_NAME = "GCP";
  private static final String DEFAULT_GCP_FIELD_ID = "gcpProjectId";
  private static final String DEFAULT_GCP_FIELD_NAME = "Project";

  private static final String DEFAULT_FIELD_ID = "cloudProvider";
  private static final String DEFAULT_FIELD_NAME = "Cloud Provider";

  private static final String DEFAULT_CLUSTER_VIEW_NAME = "Cluster";
  private static final String DEFAULT_CLUSTER_FIELD_ID = "clusterName";
  private static final String DEFAULT_CLUSTER_FIELD_NAME = "Cluster Name";

  private static final int VIEW_COUNT = 10000;

  @Inject private CEViewDao ceViewDao;
  @Inject private CEViewFolderDao ceViewFolderDao;
  @Inject private CEReportScheduleDao ceReportScheduleDao;
  @Inject private ViewsBillingService viewsBillingService;
  @Inject private ViewCustomFieldService viewCustomFieldService;
  @Inject private ViewTimeRangeHelper viewTimeRangeHelper;
  @Inject private ViewFilterBuilderHelper viewFilterBuilderHelper;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private BusinessMappingService businessMappingService;
  @Inject private CEViewPreferenceService ceViewPreferenceService;

  @Override
  public CEView save(CEView ceView, boolean clone) {
    validateView(ceView, clone);
    if (ceView.getViewState() != null && ceView.getViewState() == ViewState.COMPLETED) {
      ceView.setViewState(ViewState.COMPLETED);
    } else {
      ceView.setViewState(ViewState.DRAFT);
    }
    if (StringUtils.isEmpty(ceView.getFolderId())) {
      ceView.setFolderId(getDefaultFolderId(ceView.getAccountId()));
    }
    CEViewFolder sampleFolder = ceViewFolderDao.getSampleFolder(ceView.getAccountId());
    if (ceView.getFolderId().equals(sampleFolder.getUuid())) {
      ceView.setFolderId(getDefaultFolderId(ceView.getAccountId()));
    }
    ceView.setUuid(null);
    ceViewDao.save(ceView);
    // For now, we are not returning AWS account Name in case of AWS Account rules
    return ceView;
  }

  @Override
  public CEView clone(String accountId, String perspectiveId, String clonePerspectiveName) {
    CEView view = get(perspectiveId);
    view.setName(clonePerspectiveName);
    view.setCreatedBy(null);
    view.setCreatedAt(0);
    view.setUuid(null);
    view.setViewType(ViewType.CUSTOMER);
    return save(view, true);
  }

  @Override
  public Double getLastMonthCostForPerspective(String accountId, String perspectiveId) {
    if (this.get(perspectiveId) == null) {
      throw new InvalidRequestException(BudgetUtils.INVALID_PERSPECTIVE_ID_EXCEPTION);
    }
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(viewsQueryHelper.getViewMetadataFilter(perspectiveId));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(BudgetUtils.getStartOfMonth(true), AFTER));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(
        BudgetUtils.getStartOfMonth(false) - BudgetUtils.ONE_DAY_MILLIS, BEFORE));
    return getCostForPerspective(accountId, filters);
  }

  private double getCostForPerspective(String accountId, List<QLCEViewFilterWrapper> filters) {
    ViewCostData costData = viewsBillingService.getCostData(filters,
        viewsQueryHelper.getPerspectiveTotalCostAggregation(), viewsQueryHelper.buildQueryParams(accountId, false));
    return costData.getCost();
  }

  @Override
  public Double getForecastCostForPerspective(String accountId, String perspectiveId) {
    if (this.get(perspectiveId) == null) {
      throw new InvalidRequestException(BudgetUtils.INVALID_PERSPECTIVE_ID_EXCEPTION);
    }
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    long startTime = BudgetUtils.getStartTimeForForecasting();
    long endTime = BudgetUtils.getEndOfMonthForCurrentBillingCycle();
    filters.add(viewsQueryHelper.getViewMetadataFilter(perspectiveId));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(startTime, AFTER));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(endTime, BEFORE));
    ViewCostData costDataForForecast =
        ViewCostData.builder()
            .cost(viewsBillingService
                      .getCostData(filters, viewsQueryHelper.getPerspectiveTotalCostAggregation(),
                          viewsQueryHelper.buildQueryParams(accountId, false))
                      .getCost())
            .minStartTime(1000 * startTime)
            .maxStartTime(1000 * BudgetUtils.getStartOfCurrentDay() - BudgetUtils.ONE_DAY_MILLIS)
            .build();
    double costTillNow = getActualCostForPerspectiveBudget(accountId, perspectiveId);
    return viewsQueryHelper.getRoundedDoubleValue(
        costTillNow + viewsQueryHelper.getForecastCost(costDataForForecast, Instant.ofEpochMilli(endTime)));
  }

  @Override
  public double getActualCostForPerspectiveBudget(String accountId, String perspectiveId) {
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(viewsQueryHelper.getViewMetadataFilter(perspectiveId));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(BudgetUtils.getStartOfMonthForCurrentBillingCycle(), AFTER));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(BudgetUtils.getEndOfMonthForCurrentBillingCycle(), BEFORE));
    return getCostForPerspective(accountId, filters);
  }

  public boolean validateView(CEView ceView, boolean clone) {
    CEView savedCEView = ceViewDao.findByName(ceView.getAccountId(), ceView.getName());
    if (null != savedCEView) {
      if (clone) {
        throw new InvalidRequestException(CLONE_NAME_DUPLICATE_EXCEPTION);
      }
      throw new InvalidRequestException(VIEW_NAME_DUPLICATE_EXCEPTION);
    }
    List<CEView> views = ceViewDao.findByAccountId(ceView.getAccountId(), null);
    if (views.size() >= VIEW_COUNT) {
      throw new InvalidRequestException(VIEW_LIMIT_REACHED_EXCEPTION);
    }
    modifyCEViewAndSetDefaults(ceView);
    return true;
  }

  private void modifyCEViewAndSetDefaults(CEView ceView) {
    Set<String> validBusinessMappingIds = null;
    if (ceView.getViewVisualization() == null || ceView.getViewVisualization().getGroupBy() == null) {
      ceView.setViewVisualization(getDefaultViewVisualization());
    } else if (ceView.getViewVisualization().getGroupBy().getIdentifier() == ViewFieldIdentifier.BUSINESS_MAPPING) {
      validBusinessMappingIds = businessMappingService.getBusinessMappingIds(ceView.getAccountId());
      validateBusinessMappingId(validBusinessMappingIds, ceView.getViewVisualization().getGroupBy().getFieldId());
    }

    if (ceView.getViewTimeRange() == null) {
      ceView.setViewTimeRange(ViewTimeRange.builder().viewTimeRangeType(ViewTimeRangeType.LAST_7).build());
    }

    Set<ViewFieldIdentifier> viewFieldIdentifierSet = getViewFieldIdentifiers(ceView, validBusinessMappingIds);
    setDataSources(ceView, viewFieldIdentifierSet);
    ceView.setViewPreferences(ceViewPreferenceService.getCEViewPreferences(ceView, Collections.emptySet()));
  }

  private ViewVisualization getDefaultViewVisualization() {
    return ViewVisualization.builder()
        .granularity(ViewTimeGranularity.DAY)
        .chartType(ViewChartType.STACKED_TIME_SERIES)
        .groupBy(ViewField.builder()
                     .fieldId("product")
                     .fieldName("Product")
                     .identifier(ViewFieldIdentifier.COMMON)
                     .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                     .build())
        .build();
  }

  @NotNull
  private Set<ViewFieldIdentifier> getViewFieldIdentifiers(CEView ceView, Set<String> validBusinessMappingIds) {
    Set<ViewFieldIdentifier> viewFieldIdentifierSet = new HashSet<>();

    ViewFieldIdentifier groupByViewFieldIdentifier = ceView.getViewVisualization().getGroupBy().getIdentifier();
    if (groupByViewFieldIdentifier != ViewFieldIdentifier.LABEL
        && groupByViewFieldIdentifier != ViewFieldIdentifier.COMMON) {
      viewFieldIdentifierSet.add(groupByViewFieldIdentifier);
    }

    if (ceView.getViewRules() != null) {
      for (ViewRule rule : ceView.getViewRules()) {
        for (ViewCondition condition : rule.getViewConditions()) {
          ViewIdCondition viewIdCondition = (ViewIdCondition) condition;
          if (viewIdCondition.getViewField().getIdentifier() == CLUSTER) {
            viewFieldIdentifierSet.add(CLUSTER);
          }
          if (viewIdCondition.getViewField().getIdentifier() == ViewFieldIdentifier.AWS) {
            viewFieldIdentifierSet.add(ViewFieldIdentifier.AWS);
            if (AWS_ACCOUNT_FIELD.equals(viewIdCondition.getViewField().getFieldName())) {
              viewIdCondition.setValues(
                  AwsAccountFieldHelper.removeAwsAccountNameFromValues(viewIdCondition.getValues()));
            }
          }
          if (viewIdCondition.getViewField().getIdentifier() == ViewFieldIdentifier.GCP) {
            viewFieldIdentifierSet.add(ViewFieldIdentifier.GCP);
          }
          if (viewIdCondition.getViewField().getIdentifier() == ViewFieldIdentifier.AZURE) {
            viewFieldIdentifierSet.add(ViewFieldIdentifier.AZURE);
          }
          if (viewIdCondition.getViewField().getIdentifier() == ViewFieldIdentifier.CUSTOM) {
            String viewId = viewIdCondition.getViewField().getFieldId();
            List<ViewField> customFieldViewFields = viewCustomFieldService.get(viewId).getViewFields();
            for (ViewField field : customFieldViewFields) {
              viewFieldIdentifierSet.add(field.getIdentifier());
            }
            viewFieldIdentifierSet.add(ViewFieldIdentifier.CUSTOM);
          }
          if (viewIdCondition.getViewField().getIdentifier() == ViewFieldIdentifier.BUSINESS_MAPPING) {
            if (validBusinessMappingIds == null) {
              validBusinessMappingIds = businessMappingService.getBusinessMappingIds(ceView.getAccountId());
            }
            validateBusinessMappingId(validBusinessMappingIds, viewIdCondition.getViewField().getFieldId());
            viewFieldIdentifierSet.add(ViewFieldIdentifier.BUSINESS_MAPPING);
          }
          if (viewIdCondition.getViewField().getIdentifier() == ViewFieldIdentifier.COMMON) {
            viewFieldIdentifierSet.addAll(getDataSourcesFromCloudProviderField(viewIdCondition, ceView.getAccountId()));
          }
        }
      }
    }
    return viewFieldIdentifierSet;
  }

  public void validateBusinessMappingId(Set<String> validBusinessMappingIds, String id) {
    if (!validBusinessMappingIds.contains(id)) {
      throw new InvalidRequestException(String.format(INVALID_COST_CATEGORY_ID_EXCEPTION, id));
    }
  }

  @Override
  public Set<ViewFieldIdentifier> getDataSourcesFromCloudProviderField(
      final ViewIdCondition viewIdCondition, String accountId) {
    Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    if (ViewFieldConstants.CLOUD_PROVIDER_FIELD_ID.equals(viewIdCondition.getViewField().getFieldId())) {
      ViewIdOperator operator = viewIdCondition.getViewOperator();
      Set<ViewFieldIdentifier> dataSourcesFromValues = new HashSet<>();
      for (final String value : viewIdCondition.getValues()) {
        if (ViewFieldIdentifier.AWS.name().equals(value)) {
          dataSourcesFromValues.add(ViewFieldIdentifier.AWS);
        } else if (ViewFieldIdentifier.GCP.name().equals(value)) {
          dataSourcesFromValues.add(ViewFieldIdentifier.GCP);
        } else if (ViewFieldIdentifier.AZURE.name().equals(value)) {
          dataSourcesFromValues.add(ViewFieldIdentifier.AZURE);
        } else if (ViewFieldIdentifier.CLUSTER.name().equals(value)) {
          dataSourcesFromValues.add(ViewFieldIdentifier.CLUSTER);
        }
      }
      if (operator == IN || operator == EQUALS) {
        viewFieldIdentifiers = dataSourcesFromValues;
      } else if (operator == NOT_IN) {
        Set<ViewFieldIdentifier> allDataSources = getAllPossibleDataSourcesForAccount(accountId);
        for (ViewFieldIdentifier dataSource : allDataSources) {
          if (!dataSourcesFromValues.contains(dataSource)) {
            viewFieldIdentifiers.add(dataSource);
          }
        }
      }
    }
    return viewFieldIdentifiers;
  }

  private Set<ViewFieldIdentifier> getAllPossibleDataSourcesForAccount(String accountId) {
    Set<ViewFieldIdentifier> dataSources = new HashSet<>();
    DefaultViewIdDto defaultViewIds = getDefaultViewIds(accountId);
    if (defaultViewIds.getAwsViewId() != null) {
      dataSources.add(AWS);
    }
    if (defaultViewIds.getAzureViewId() != null) {
      dataSources.add(AZURE);
    }
    if (defaultViewIds.getGcpViewId() != null) {
      dataSources.add(GCP);
    }
    if (defaultViewIds.getClusterViewId() != null) {
      dataSources.add(CLUSTER);
    }
    return dataSources;
  }

  private void setDataSources(final CEView ceView, final Set<ViewFieldIdentifier> viewFieldIdentifierSet) {
    if (ceView.getViewType() == ViewType.DEFAULT) {
      if (DEFAULT_AZURE_VIEW_NAME.equals(ceView.getName())) {
        ceView.setDataSources(Collections.singletonList(ViewFieldIdentifier.AZURE));
      } else if (DEFAULT_AWS_VIEW_NAME.equals(ceView.getName())) {
        ceView.setDataSources(Collections.singletonList(ViewFieldIdentifier.AWS));
      } else if (DEFAULT_GCP_VIEW_NAME.equals(ceView.getName())) {
        ceView.setDataSources(Collections.singletonList(ViewFieldIdentifier.GCP));
      } else {
        ceView.setDataSources(new ArrayList<>(viewFieldIdentifierSet));
      }
    } else {
      ceView.setDataSources(new ArrayList<>(viewFieldIdentifierSet));
    }
  }

  @Override
  public CEView get(String uuid) {
    CEView view = ceViewDao.get(uuid);
    if (view != null && view.getViewRules() == null) {
      view.setViewRules(Collections.emptyList());
    }
    return view;
  }

  @Override
  public CEView update(CEView ceView) {
    modifyCEViewAndSetDefaults(ceView);
    // For now, we are not returning AWS account Name in case of AWS Account rules
    return ceViewDao.update(ceView);
  }

  @Override
  public Set<String> getPerspectiveFolderIds(String accountId, List<String> ceViewIds) {
    if (ceViewIds == null) {
      return null;
    }
    List<CEView> ceViews = ceViewDao.getPerspectivesByIds(accountId, ceViewIds);
    return ceViews.stream().map(CEView::getFolderId).collect(Collectors.toSet());
  }

  @Override
  public HashMap<String, String> getPerspectiveIdAndFolderId(String accountId, List<String> ceViewIds) {
    if (ceViewIds == null) {
      return null;
    }
    List<CEView> ceViews = ceViewDao.getPerspectivesByIds(accountId, ceViewIds);
    HashMap<String, String> perspectiveIdAndFolderIds = new HashMap<>();
    for (CEView ceView : ceViews) {
      perspectiveIdAndFolderIds.put(ceView.getUuid(), ceView.getFolderId());
    }
    return perspectiveIdAndFolderIds;
  }

  @Override
  public void updateBusinessMappingName(String accountId, String buinessMappingUuid, String newBusinessMappingName) {
    ceViewDao.updateBusinessMappingName(accountId, buinessMappingUuid, newBusinessMappingName);
  }

  @Override
  public CEView updateTotalCost(CEView ceView) {
    if (ceView.getViewState() != null && ceView.getViewState() == ViewState.COMPLETED) {
      List<QLCEViewAggregation> totalCostAggregationFunction = Collections.singletonList(
          QLCEViewAggregation.builder().columnName("cost").operationType(QLCEViewAggregateOperation.SUM).build());
      List<QLCEViewFilterWrapper> filters = new ArrayList<>();
      filters.add(
          QLCEViewFilterWrapper.builder()
              .viewMetadataFilter(QLCEViewMetadataFilter.builder().viewId(ceView.getUuid()).isPreview(false).build())
              .build());
      ViewTimeRange viewTimeRange = ceView.getViewTimeRange();
      ViewTimeRangeDto startEndTime = viewTimeRangeHelper.getStartEndTime(viewTimeRange);
      filters.add(
          viewFilterBuilderHelper.getViewTimeFilter(startEndTime.getStartTime(), QLCEViewTimeFilterOperator.AFTER));
      filters.add(
          viewFilterBuilderHelper.getViewTimeFilter(startEndTime.getEndTime(), QLCEViewTimeFilterOperator.BEFORE));

      QLCEViewTrendInfo trendData =
          viewsBillingService
              .getTrendStatsDataNg(filters, Collections.emptyList(), totalCostAggregationFunction,
                  ceView.getViewPreferences(), getViewQueryParamsForTrendStats(ceView))
              .getTotalCost();
      double totalCost = trendData.getValue().doubleValue();
      log.info("Total cost of view {}", totalCost);
      return ceViewDao.updateTotalCost(ceView.getUuid(), ceView.getAccountId(), totalCost);
    }
    return ceView;
  }

  private ViewQueryParams getViewQueryParamsForTrendStats(CEView ceView) {
    ViewQueryParams viewQueryParams = viewsQueryHelper.buildQueryParams(ceView.getAccountId(), false);

    // Group by is only needed in case of business mapping
    if (!viewsQueryHelper.isGroupByBusinessMappingPresent(viewsQueryHelper.getDefaultViewGroupBy(ceView))) {
      viewQueryParams = viewsQueryHelper.buildQueryParamsWithSkipGroupBy(viewQueryParams, true);
    }

    return viewQueryParams;
  }

  @Override
  public boolean delete(String uuid, String accountId) {
    return ceViewDao.delete(uuid, accountId);
  }

  @Override
  public List<QLCEView> getAllViews(String accountId, boolean includeDefault, QLCEViewSortCriteria sortCriteria) {
    List<CEView> viewList = ceViewDao.findByAccountId(accountId, sortCriteria);
    List<CEViewFolder> folderList = ceViewFolderDao.getFolders(accountId, "");
    if (!includeDefault) {
      viewList = viewList.stream()
                     .filter(view -> ImmutableSet.of(ViewType.SAMPLE, ViewType.CUSTOMER).contains(view.getViewType()))
                     .collect(Collectors.toList());
    }
    return getQLCEViewsFromCEViews(accountId, viewList, folderList);
  }

  @Override
  public List<QLCEView> getAllViews(
      String accountId, String folderId, boolean includeDefault, QLCEViewSortCriteria sortCriteria) {
    List<CEView> viewList = ceViewDao.findByAccountIdAndFolderId(accountId, folderId, sortCriteria);
    List<CEViewFolder> folderList = ceViewFolderDao.getFolders(accountId, Collections.singletonList(folderId));
    if (!includeDefault) {
      viewList = viewList.stream()
                     .filter(view -> ImmutableSet.of(ViewType.SAMPLE, ViewType.CUSTOMER).contains(view.getViewType()))
                     .collect(Collectors.toList());
    }
    return getQLCEViewsFromCEViews(accountId, viewList, folderList);
  }

  @Override
  public List<CEView> getAllViews(String accountId) {
    return ceViewDao.list(accountId);
  }

  @Override
  public void updateAllPerspectiveWithPerspectivePreferenceDefaultSettings(
      String accountId, Set<String> viewPreferencesFieldsToUpdateWithDefaultSettings) {
    for (CEView ceView : getAllViews(accountId)) {
      try {
        ViewPreferences viewPreferences =
            ceViewPreferenceService.getCEViewPreferences(ceView, viewPreferencesFieldsToUpdateWithDefaultSettings);
        ceViewDao.updateViewPreferences(ceView.getUuid(), accountId, viewPreferences);
      } catch (Exception ex) {
        log.error("Unable to update view preferences with default settings for accountId {}, viewId {}, fields {}",
            accountId, ceView.getUuid(), viewPreferencesFieldsToUpdateWithDefaultSettings, ex);
      }
    }
  }

  @Override
  public List<CEViewShortHand> getAllViewsShortHand(String accountId) {
    List<CEView> viewList = getAllViews(accountId);
    List<CEViewShortHand> viewShortHandList = new ArrayList<>();
    for (CEView view : viewList) {
      viewShortHandList.add(CEViewShortHand.builder()
                                .uuid(view.getUuid())
                                .accountId(view.getAccountId())
                                .name(view.getName())
                                .folderId(view.getFolderId())
                                .build());
    }
    return viewShortHandList;
  }

  private List<QLCEView> getQLCEViewsFromCEViews(
      String accountId, List<CEView> viewList, List<CEViewFolder> folderList) {
    List<QLCEView> graphQLViewObjList = new ArrayList<>();
    Map<String, String> folderIdToNameMapping =
        folderList.stream().collect(Collectors.toMap(CEViewFolder::getUuid, CEViewFolder::getName));
    Map<String[], List<CEReportSchedule>> viewIdReportSchedule = getViewIdReportSchedule(accountId, viewList);
    for (CEView view : viewList) {
      String[] viewIdList = {view.getUuid()};
      List<CEReportSchedule> reportSchedules = viewIdReportSchedule.get(viewIdList);
      ViewChartType vChartType = null;
      if (view.getViewVisualization() != null) {
        // For DRAFT support, no visualizations have been set at this point
        vChartType = view.getViewVisualization().getChartType();
      }
      ViewField groupBy = view.getViewVisualization().getGroupBy();
      graphQLViewObjList.add(
          QLCEView.builder()
              .id(view.getUuid())
              .name(view.getName())
              .folderId(view.getFolderId())
              .folderName((view.getFolderId() != null) ? folderIdToNameMapping.get(view.getFolderId()) : null)
              .totalCost(view.getTotalCost())
              .createdBy(null != view.getCreatedBy() ? view.getCreatedBy().getEmail() : "")
              .createdAt(view.getCreatedAt())
              .lastUpdatedAt(view.getLastUpdatedAt())
              .chartType(vChartType)
              .viewType(view.getViewType())
              .viewState(view.getViewState())
              .groupBy(QLCEViewField.builder()
                           .fieldId(groupBy.getFieldId())
                           .fieldName(groupBy.getFieldName())
                           .identifier(groupBy.getIdentifier())
                           .identifierName(groupBy.getIdentifier().getDisplayName())
                           .build())
              .timeRange(view.getViewTimeRange().getViewTimeRangeType())
              .dataSources(view.getDataSources())
              .viewPreferences(view.getViewPreferences())
              .isReportScheduledConfigured(!CollectionUtils.isEmpty(reportSchedules))
              .build());
    }
    return graphQLViewObjList;
  }

  private Map<String[], List<CEReportSchedule>> getViewIdReportSchedule(String accountId, List<CEView> viewList) {
    List<String> viewIds = viewList.stream().map(CEView::getUuid).collect(Collectors.toList());
    List<CEReportSchedule> reportSettingByViewIds = ceReportScheduleDao.getReportSettingByViewIds(viewIds, accountId);
    return reportSettingByViewIds.stream().collect(Collectors.groupingBy(CEReportSchedule::getViewsId));
  }

  @Override
  public List<CEView> getViewByState(String accountId, ViewState viewState) {
    return ceViewDao.findByAccountIdAndState(accountId, viewState);
  }

  @Override
  public List<LinkedPerspectives> getViewsByBusinessMapping(String accountId, List<String> businessMappingUuids) {
    List<LinkedPerspectives> perspectiveListMessageList = new ArrayList<>();
    for (String businessMappingUuid : businessMappingUuids) {
      List<CEView> ceViewList = ceViewDao.findByAccountIdAndBusinessMapping(accountId, businessMappingUuid);
      LinkedPerspectivesBuilder perspectiveListMessageBuilder =
          LinkedPerspectives.builder().costCategoryId(businessMappingUuid);
      if (!Lists.isNullOrEmpty(ceViewList)) {
        perspectiveListMessageBuilder.perspectiveIdAndName(
            ceViewList.stream().collect(Collectors.toMap(CEView::getUuid, CEView::getName)));
      }
      perspectiveListMessageList.add(perspectiveListMessageBuilder.build());
    }
    return perspectiveListMessageList;
  }

  private ViewIdCondition getDefaultViewIdCondition(String fieldId, String fieldName, ViewFieldIdentifier identifier) {
    ViewIdCondition viewIdCondition;
    if (ViewFieldIdentifier.AZURE == identifier || ViewFieldIdentifier.AWS == identifier
        || ViewFieldIdentifier.GCP == identifier) {
      viewIdCondition = getCloudProvidersDefaultViewIdCondition(fieldId, fieldName, identifier);
    } else {
      viewIdCondition = getClusterDefaultViewIdCondition(fieldId, fieldName, identifier);
    }
    return viewIdCondition;
  }

  private ViewIdCondition getCloudProvidersDefaultViewIdCondition(
      String fieldId, String fieldName, ViewFieldIdentifier identifier) {
    ViewIdCondition viewIdCondition;
    viewIdCondition = ViewIdCondition.builder()
                          .viewField(ViewField.builder()
                                         .fieldId(fieldId)
                                         .fieldName(fieldName)
                                         .identifier(ViewFieldIdentifier.COMMON)
                                         .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                                         .build())
                          .viewOperator(ViewIdOperator.EQUALS)
                          .values(Collections.singletonList(identifier.name().toUpperCase(Locale.ROOT)))
                          .build();
    return viewIdCondition;
  }

  private ViewIdCondition getClusterDefaultViewIdCondition(
      String fieldId, String fieldName, ViewFieldIdentifier identifier) {
    ViewIdCondition viewIdCondition;
    viewIdCondition = ViewIdCondition.builder()
                          .viewField(ViewField.builder()
                                         .fieldId(fieldId)
                                         .fieldName(fieldName)
                                         .identifier(identifier)
                                         .identifierName(identifier.getDisplayName())
                                         .build())
                          .viewOperator(ViewIdOperator.NOT_NULL)
                          .values(Collections.singletonList(""))
                          .build();
    return viewIdCondition;
  }

  private ViewVisualization getDefaultViewVisualization(
      String fieldId, String fieldName, ViewFieldIdentifier identifier) {
    return ViewVisualization.builder()
        .granularity(ViewTimeGranularity.DAY)
        .chartType(ViewChartType.STACKED_TIME_SERIES)
        .groupBy(ViewField.builder()
                     .fieldId(fieldId)
                     .fieldName(fieldName)
                     .identifier(identifier)
                     .identifierName(identifier.getDisplayName())
                     .build())
        .build();
  }

  private CEView getDefaultView(String accountId, String viewName) {
    return CEView.builder()
        .accountId(accountId)
        .name(viewName)
        .viewVersion("v1")
        .viewType(ViewType.DEFAULT)
        .viewState(ViewState.COMPLETED)
        .folderId(ceViewFolderDao.getSampleFolder(accountId).getUuid())
        .build();
  }

  @Override
  public void createDefaultView(String accountId, ViewFieldIdentifier viewFieldIdentifier) {
    ViewIdCondition condition = null;
    CEView defaultView = null;
    ViewVisualization viewVisualization = null;
    switch (viewFieldIdentifier) {
      case AZURE:
        condition = getDefaultViewIdCondition(DEFAULT_FIELD_ID, DEFAULT_FIELD_NAME, viewFieldIdentifier);
        viewVisualization =
            getDefaultViewVisualization(DEFAULT_AZURE_FIELD_ID, DEFAULT_AZURE_FIELD_NAME, viewFieldIdentifier);
        defaultView = getDefaultView(accountId, DEFAULT_AZURE_VIEW_NAME);
        break;
      case AWS:
        condition = getDefaultViewIdCondition(DEFAULT_FIELD_ID, DEFAULT_FIELD_NAME, viewFieldIdentifier);
        viewVisualization =
            getDefaultViewVisualization(DEFAULT_AWS_FIELD_ID, DEFAULT_AWS_FIELD_NAME, viewFieldIdentifier);
        defaultView = getDefaultView(accountId, DEFAULT_AWS_VIEW_NAME);
        break;
      case GCP:
        condition = getDefaultViewIdCondition(DEFAULT_FIELD_ID, DEFAULT_FIELD_NAME, viewFieldIdentifier);
        viewVisualization =
            getDefaultViewVisualization(DEFAULT_GCP_FIELD_ID, DEFAULT_GCP_FIELD_NAME, viewFieldIdentifier);
        defaultView = getDefaultView(accountId, DEFAULT_GCP_VIEW_NAME);
        break;
      case CLUSTER:
        condition =
            getDefaultViewIdCondition(DEFAULT_CLUSTER_FIELD_ID, DEFAULT_CLUSTER_FIELD_NAME, viewFieldIdentifier);
        viewVisualization =
            getDefaultViewVisualization(DEFAULT_CLUSTER_FIELD_ID, DEFAULT_CLUSTER_FIELD_NAME, viewFieldIdentifier);
        defaultView = getDefaultView(accountId, DEFAULT_CLUSTER_VIEW_NAME);
        break;
      default:
        break;
    }

    if (null != condition && null != defaultView) {
      ViewRule rule = ViewRule.builder().viewConditions(Collections.singletonList(condition)).build();
      defaultView.setViewRules(Collections.singletonList(rule));
      defaultView.setViewVisualization(viewVisualization);
      modifyCEViewAndSetDefaults(defaultView);
      ceViewDao.save(defaultView);
    }
  }

  @Override
  public DefaultViewIdDto getDefaultViewIds(String accountId) {
    List<CEView> views = ceViewDao.findByAccountIdAndType(accountId, ViewType.DEFAULT);
    return DefaultViewIdDto.builder()
        .awsViewId(getViewId(views, ViewFieldIdentifier.AWS))
        .azureViewId(getViewId(views, ViewFieldIdentifier.AZURE))
        .gcpViewId(getViewId(views, ViewFieldIdentifier.GCP))
        .clusterViewId(getViewId(views, CLUSTER))
        .build();
  }

  @Override
  public void updateDefaultClusterViewVisualization(String viewId) {
    try {
      CEView view = ceViewDao.get(viewId);
      ViewVisualization viewVisualization =
          getDefaultViewVisualization(DEFAULT_CLUSTER_FIELD_ID, DEFAULT_CLUSTER_FIELD_NAME, CLUSTER);
      view.setViewVisualization(viewVisualization);
      ceViewDao.update(view);
    } catch (Exception e) {
      log.error("Error while updating ViewVisualization of default cluster perspective {}", viewId, e);
    }
  }

  @Override
  public Map<String, String> getPerspectiveIdToNameMapping(String accountId, List<String> perspectiveIds) {
    List<CEView> perspectives = ceViewDao.list(accountId, perspectiveIds);
    return perspectives.stream().collect(Collectors.toMap(CEView::getUuid, CEView::getName));
  }

  private String getViewId(List<CEView> views, ViewFieldIdentifier viewFieldIdentifier) {
    if (views != null && views.size() > 0) {
      Optional<CEView> view =
          views.stream().filter(ceView -> ceView.getDataSources().contains(viewFieldIdentifier)).findFirst();
      if (view.isPresent()) {
        return view.get().getUuid();
      }
    }
    return null;
  }

  public String getDefaultFolderId(String accountId) {
    CEViewFolder defaultFolder = ceViewFolderDao.getDefaultFolder(accountId);
    if (defaultFolder == null) {
      return ceViewFolderDao.createDefaultOrSampleFolder(accountId, ViewType.DEFAULT);
    } else {
      return defaultFolder.getUuid();
    }
  }

  public String getSampleFolderId(String accountId) {
    CEViewFolder sampleFolder = ceViewFolderDao.getSampleFolder(accountId);
    if (sampleFolder == null) {
      return ceViewFolderDao.createDefaultOrSampleFolder(accountId, ViewType.SAMPLE);
    } else {
      return sampleFolder.getUuid();
    }
  }

  @Override
  public boolean setFolderId(
      CEView ceView, Set<String> allowedFolderIds, List<CEViewFolder> ceViewFolders, String defaultFolderId) {
    List<CEViewFolder> allowedCeViewFolders =
        ceViewFolders.stream()
            .filter(ceViewFolder -> allowedFolderIds.contains(ceViewFolder.getUuid()))
            .collect(Collectors.toList());
    if (allowedCeViewFolders.size() == 0) {
      return false;
    }
    if (allowedCeViewFolders.size() == 1 && allowedCeViewFolders.get(0).getName().equals("By Harness")) {
      return false;
    }
    if (allowedFolderIds.contains(defaultFolderId)) {
      ceView.setFolderId(defaultFolderId);
      return true;
    }
    if (allowedCeViewFolders.get(0).getName().equals("By Harness")) {
      ceView.setFolderId(allowedCeViewFolders.get(1).getUuid());
      return true;
    }
    ceView.setFolderId(allowedCeViewFolders.get(0).getUuid());
    return true;
  }
}
