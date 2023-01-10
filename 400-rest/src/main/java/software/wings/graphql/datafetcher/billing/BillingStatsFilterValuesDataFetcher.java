/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.cluster.dao.K8sWorkloadDao;
import io.harness.ccm.cluster.entities.K8sLabelFilter;
import io.harness.ccm.cluster.entities.TagFilter;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.service.intf.InstanceDataService;
import io.harness.exception.InvalidRequestException;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.HarnessTag;
import software.wings.graphql.datafetcher.AbstractStatsDataFetcherWithAggregationListAndLimit;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.BillingDataMetaDataFields;
import software.wings.graphql.datafetcher.k8sLabel.K8sLabelConnectionDataFetcher;
import software.wings.graphql.schema.type.QLK8sLabel;
import software.wings.graphql.schema.type.QLTags;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLFilterValuesData;
import software.wings.graphql.schema.type.aggregation.billing.QLFilterValuesData.QLFilterValuesDataBuilder;
import software.wings.graphql.schema.type.aggregation.billing.QLFilterValuesListData;
import software.wings.graphql.schema.type.aggregation.k8sLabel.QLK8sLabelFilter;
import software.wings.graphql.schema.type.aggregation.k8sLabel.QLK8sLabelFilter.QLK8sLabelFilterBuilder;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BillingStatsFilterValuesDataFetcher
    extends AbstractStatsDataFetcherWithAggregationListAndLimit<QLCCMAggregationFunction, QLBillingDataFilter,
        QLCCMGroupBy, QLBillingSortCriteria> {
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject BillingDataHelper billingDataHelper;
  @Inject QLBillingStatsHelper statsHelper;
  @Inject BillingDataQueryBuilder billingDataQueryBuilder;
  @Inject K8sLabelConnectionDataFetcher k8sLabelConnectionDataFetcher;
  @Inject InstanceDataService instanceDataService;
  @Inject K8sWorkloadDao k8sWorkloadDao;
  @Inject HarnessTagService harnessTagService;
  @Inject CeAccountExpirationChecker accountChecker;
  private static final String TOTAL = "total";
  private static final String EMPTY = "";

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  protected QLData fetchSelectedFields(String accountId, List<QLCCMAggregationFunction> aggregateFunction,
      List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy, List<QLBillingSortCriteria> sortCriteria,
      Integer limit, Integer offset, boolean skipRoundOff, DataFetchingEnvironment dataFetchingEnvironment) {
    accountChecker.checkIsCeEnabled(accountId);
    try {
      if (timeScaleDBService.isValid()) {
        List<String> selectedFields = getSelectedFields(dataFetchingEnvironment);
        return getEntityData(accountId, filters, groupBy, sortCriteria, limit, offset, selectedFields);
      } else {
        throw new InvalidRequestException("Cannot process request in BillingStatsFilterValuesDataFetcher");
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Error while fetching billing data {}", e);
    }
  }

  protected QLFilterValuesListData getEntityData(@NotNull String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMGroupBy> groupByList, List<QLBillingSortCriteria> sortCriteria, Integer limit, Integer offset,
      List<String> selectedFields) {
    BillingDataQueryMetadata queryData;
    ResultSet resultSet = null;
    boolean successful = false;
    int retryCount = 0;

    K8sLabelFilter labelFilter = null;
    if (isGroupByLabelPresent(groupByList)) {
      labelFilter = prepareLabelFilters(filters, groupByList, accountId, limit, offset);
    }

    TagFilter tagFilter = null;
    if (isGroupByTagPresent(groupByList)) {
      tagFilter = prepareTagFilters(filters, groupByList, accountId, limit, offset);
    }

    List<QLCCMEntityGroupBy> groupByEntityList = billingDataQueryBuilder.getGroupByEntity(groupByList);
    List<QLCCMEntityGroupBy> groupByNodeAndPodList = new ArrayList<>();
    List<QLCCMEntityGroupBy> groupByEntityListExcludingNodeAndPod = new ArrayList<>();

    Long totalCount = 0L;
    if (selectedFields.contains(TOTAL) && groupByEntityList.size() == 1) {
      totalCount = getTotalCount(accountId, filters, groupByEntityList);
    }

    groupByEntityList.forEach(entityGroupBy -> {
      if (entityGroupBy == QLCCMEntityGroupBy.Node || entityGroupBy == QLCCMEntityGroupBy.Pod) {
        groupByNodeAndPodList.add(entityGroupBy);
      } else {
        groupByEntityListExcludingNodeAndPod.add(entityGroupBy);
      }
    });

    Set<String> instanceIds = new HashSet<>();
    if (!groupByNodeAndPodList.isEmpty()) {
      instanceIds = getInstanceIdValues(accountId,
          getFiltersForInstanceIdQuery(filters, isGroupByPodPresent(groupByNodeAndPodList)), groupByNodeAndPodList,
          sortCriteria, limit, offset);
      filters = getFiltersExcludingInstanceTypeFilter(filters);
    }

    queryData = billingDataQueryBuilder.formFilterValuesQuery(
        accountId, filters, groupByEntityListExcludingNodeAndPod, sortCriteria, limit, offset);
    log.info("BillingStatsFilterValuesDataFetcher query!! {}", queryData.getQuery());

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(queryData.getQuery());
        successful = true;
        return generateFilterValuesData(
            queryData, resultSet, instanceIds, accountId, labelFilter, tagFilter, totalCount);
      } catch (SQLException e) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query in BillingStatsFilterValuesDataFetcher, max retry count reached, query=[{}],accountId=[{}]",
              queryData.getQuery(), accountId, e);
        } else {
          log.warn(
              "Failed to execute query in BillingStatsFilterValuesDataFetcher, query=[{}],accountId=[{}], retryCount=[{}]",
              queryData.getQuery(), accountId, retryCount);
        }
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return null;
  }

  private Set<String> getInstanceIdValues(String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMEntityGroupBy> groupByNodeAndPodList, List<QLBillingSortCriteria> sortCriteria, Integer limit,
      Integer offset) {
    ResultSet resultSet = null;
    boolean successful = false;
    int retryCount = 0;
    BillingDataQueryMetadata queryData = billingDataQueryBuilder.formFilterValuesQuery(
        accountId, filters, groupByNodeAndPodList, sortCriteria, limit, offset);
    log.info("BillingStatsFilterValuesDataFetcher query to get InstanceIds!! {}", queryData.getQuery());
    Set<String> instanceIds = new HashSet<>();
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(queryData.getQuery());
        successful = true;
        while (resultSet != null && resultSet.next()) {
          for (BillingDataMetaDataFields field : queryData.getFieldNames()) {
            switch (field) {
              case INSTANCEID:
                instanceIds.add(resultSet.getString(field.getFieldName()));
                break;
              default:
                break;
            }
          }
        }
        return instanceIds;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query in BillingStatsFilterValuesDataFetcher to get InstanceIds, max retry count reached, query=[{}],accountId=[{}]",
              queryData.getQuery(), accountId, e);
        } else {
          log.warn(
              "Failed to execute query in BillingStatsFilterValuesDataFetcher to get InstanceIds, query=[{}],accountId=[{}], retryCount=[{}]",
              queryData.getQuery(), accountId, retryCount);
        }
        retryCount++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return instanceIds;
  }

  private long getTotalCount(String accountId, List<QLBillingDataFilter> filters, List<QLCCMEntityGroupBy> groupBy) {
    ResultSet resultSet = null;
    boolean successful = false;
    int retryCount = 0;
    BillingDataQueryMetadata queryData = billingDataQueryBuilder.formTotalCountQuery(accountId, filters, groupBy);
    log.info("BillingStatsFilterValuesDataFetcher query to get total count!! {}", queryData.getQuery());
    long totalCount = 0L;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(queryData.getQuery());
        successful = true;
        while (resultSet.next()) {
          for (BillingDataMetaDataFields field : queryData.getFieldNames()) {
            switch (field) {
              case COUNT:
                totalCount = resultSet.getLong(field.getFieldName());
                break;
              default:
                break;
            }
          }
        }
      } catch (SQLException e) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query in BillingStatsFilterValuesDataFetcher to get total count!, max retry count reached, query=[{}],accountId=[{}]",
              queryData.getQuery(), accountId, e);
        } else {
          log.warn(
              "Failed to execute query in BillingStatsFilterValuesDataFetcher to get total count!, query=[{}],accountId=[{}], retryCount=[{}]",
              queryData.getQuery(), accountId, retryCount);
        }
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return totalCount;
  }

  private QLFilterValuesListData generateFilterValuesData(BillingDataQueryMetadata queryData, ResultSet resultSet,
      Set<String> instanceIds, String accountId, K8sLabelFilter labelFilter, TagFilter tagFilter, Long totalCount)
      throws SQLException {
    QLFilterValuesDataBuilder filterValuesDataBuilder = QLFilterValuesData.builder();
    Set<String> cloudServiceNames = new HashSet<>();
    Set<String> workloadNames = new HashSet<>();
    Set<String> launchTypes = new HashSet<>();
    Set<String> taskIds = new HashSet<>();
    Set<String> namespaces = new HashSet<>();
    Set<String> applicationIds = new HashSet<>();
    Set<String> environmentIds = new HashSet<>();
    Set<String> cloudProviders = new HashSet<>();
    Set<String> serviceIds = new HashSet<>();
    List<QLEntityData> clusters = new ArrayList<>();
    List<QLEntityData> instances = new ArrayList<>();
    List<QLK8sLabel> k8sLabels = new ArrayList<>();
    List<QLTags> tags = new ArrayList<>();
    boolean fetchLabels = labelFilter != null;

    while (resultSet != null && resultSet.next()) {
      for (BillingDataMetaDataFields field : queryData.getFieldNames()) {
        switch (field) {
          case CLOUDSERVICENAME:
            cloudServiceNames.add(resultSet.getString(field.getFieldName()));
            break;
          case LAUNCHTYPE:
            launchTypes.add(resultSet.getString(field.getFieldName()));
            break;
          case TASKID:
            taskIds.add(resultSet.getString(field.getFieldName()));
            break;
          case CLUSTERID:
            String entityId = resultSet.getString(field.getFieldName());
            clusters.add(QLEntityData.builder()
                             .name(statsHelper.getEntityName(field, entityId))
                             .id(entityId)
                             .type(resultSet.getString(BillingDataMetaDataFields.CLUSTERTYPE.getFieldName()))
                             .build());
            break;
          case NAMESPACE:
            namespaces.add(resultSet.getString(field.getFieldName()));
            break;
          case WORKLOADNAME:
            workloadNames.add(resultSet.getString(field.getFieldName()));
            break;
          case APPID:
            applicationIds.add(resultSet.getString(field.getFieldName()));
            break;
          case CLOUDPROVIDERID:
            cloudProviders.add(resultSet.getString(field.getFieldName()));
            break;
          case ENVID:
            environmentIds.add(resultSet.getString(field.getFieldName()));
            break;
          case SERVICEID:
            serviceIds.add(resultSet.getString(field.getFieldName()));
            break;
          default:
            break;
        }
      }
    }

    // Fetching K8s labels if workload names are fetched
    if (!workloadNames.isEmpty()) {
      k8sLabels = k8sLabelConnectionDataFetcher.fetchAllLabels(
          Arrays.asList(prepareLabelFilters(getClusterIdsFromFilters(queryData.getFilters()),
              workloadNames.toArray(new String[0]), namespaces.toArray(new String[0]), accountId)));
    } else if (fetchLabels) {
      List<String> labels;
      if (labelFilter.getLabelName().equals(EMPTY)) {
        labels = k8sWorkloadDao.listLabelKeys(labelFilter);
        for (String labelName : labels) {
          k8sLabels.add(QLK8sLabel.builder().name(labelName).build());
        }
      } else {
        labels = k8sWorkloadDao.listLabelValues(labelFilter);
        k8sLabels.add(
            QLK8sLabel.builder().name(labelFilter.getLabelName()).values(labels.toArray(new String[0])).build());
      }
    }

    // Fetching tags
    if (tagFilter != null) {
      if (tagFilter.getTagName().equals(EMPTY)) {
        List<HarnessTag> harnessTags = harnessTagService.listTags(accountId);
        for (HarnessTag tag : harnessTags) {
          tags.add(QLTags.builder().name(tag.getKey()).build());
        }
      } else {
        HarnessTag tag = harnessTagService.get(accountId, tagFilter.getTagName());
        if (tag.getAllowedValues() != null) {
          tags.add(QLTags.builder().name(tag.getKey()).values(tag.getAllowedValues().toArray(new String[0])).build());
        }
      }
    }

    if (!instanceIds.isEmpty()) {
      List<InstanceData> instanceData =
          instanceDataService.fetchInstanceDataForGivenInstances(accountId, new ArrayList<>(instanceIds));
      Map<String, String> instanceIdToName = instanceData.stream().collect(Collectors.toMap(
          InstanceData::getInstanceId, InstanceData::getInstanceName, (entry1, entry2) -> { return entry1; }));
      instanceIds.forEach(instanceId
          -> instances.add(QLEntityData.builder()
                               .name(instanceIdToName.get(instanceId))
                               .id(instanceId)
                               .type(BillingDataMetaDataFields.INSTANCEID.getFieldName())
                               .build()));
    }

    filterValuesDataBuilder.cloudServiceNames(getEntity(BillingDataMetaDataFields.CLOUDSERVICENAME, cloudServiceNames))
        .taskIds(getEntity(BillingDataMetaDataFields.TASKID, taskIds))
        .launchTypes(getEntity(BillingDataMetaDataFields.LAUNCHTYPE, launchTypes))
        .clusters(clusters)
        .namespaces(getEntity(BillingDataMetaDataFields.NAMESPACE, namespaces))
        .workloadNames(getEntity(BillingDataMetaDataFields.WORKLOADNAME, workloadNames))
        .applications(getEntity(BillingDataMetaDataFields.APPID, applicationIds))
        .environments(getEntity(BillingDataMetaDataFields.ENVID, environmentIds))
        .services(getEntity(BillingDataMetaDataFields.SERVICEID, serviceIds))
        .cloudProviders(getEntity(BillingDataMetaDataFields.CLOUDPROVIDERID, cloudProviders))
        .k8sLabels(k8sLabels)
        .instances(instances)
        .tags(tags);

    List<QLFilterValuesData> filterValuesDataList = new ArrayList<>();
    filterValuesDataList.add(filterValuesDataBuilder.build());
    return QLFilterValuesListData.builder()
        .data(filterValuesDataList)
        .isHourlyDataPresent(true)
        .total(totalCount)
        .build();
  }

  private List<QLEntityData> getEntity(BillingDataMetaDataFields field, Set<String> entityIds) {
    List<QLEntityData> entityData = new ArrayList<>();
    entityIds = entityIds.stream().filter(entityId -> !entityId.equals("")).collect(Collectors.toSet());
    for (String entityId : entityIds) {
      entityData.add(QLEntityData.builder()
                         .name(statsHelper.getEntityName(field, entityId))
                         .id(entityId)
                         .type(field.toString())
                         .build());
    }
    return entityData;
  }

  private String[] getClusterIdsFromFilters(List<QLBillingDataFilter> filters) {
    List<String> clusterIds = new ArrayList<>();
    filters.forEach(filter -> {
      if (filter.getCluster() != null) {
        for (String value : filter.getCluster().getValues()) {
          clusterIds.add(value);
        }
      }
    });
    return clusterIds.toArray(new String[0]);
  }

  private QLK8sLabelFilter prepareLabelFilters(
      String[] clusterIds, String[] workloadNames, String[] namespaces, String accountId) {
    QLK8sLabelFilterBuilder builder = QLK8sLabelFilter.builder();
    builder.accountId(QLIdFilter.builder().operator(QLIdOperator.IN).values(new String[] {accountId}).build());
    if (clusterIds.length != 0) {
      builder.cluster(QLIdFilter.builder().operator(QLIdOperator.IN).values(clusterIds).build());
    }
    if (workloadNames.length != 0) {
      builder.workloadName(QLIdFilter.builder().operator(QLIdOperator.IN).values(workloadNames).build());
    }
    if (namespaces.length != 0) {
      builder.namespace(QLIdFilter.builder().operator(QLIdOperator.IN).values(namespaces).build());
    }
    return builder.build();
  }

  private List<QLBillingDataFilter> getFiltersExcludingInstanceTypeFilter(List<QLBillingDataFilter> filters) {
    List<QLBillingDataFilter> filtersExcludingInstanceTypeFilter = new ArrayList<>();
    filters.forEach(filter -> {
      if (filter.getInstanceType() == null) {
        filtersExcludingInstanceTypeFilter.add(filter);
      }
    });
    return filtersExcludingInstanceTypeFilter;
  }

  private List<QLBillingDataFilter> getFiltersForInstanceIdQuery(
      List<QLBillingDataFilter> filters, boolean isGroupByPodPresent) {
    if (isGroupByPodPresent) {
      return filters;
    }
    List<QLBillingDataFilter> filtersForInstanceIdQuery = new ArrayList<>();
    filters.forEach(filter -> {
      if (filter.getInstanceType() != null || filter.getCluster() != null || filter.getStartTime() != null
          || filter.getEndTime() != null || filter.getInstanceName() != null) {
        filtersForInstanceIdQuery.add(filter);
      } else if (filter.getNodeInstanceId() != null) {
        QLIdFilter filterValue = filter.getNodeInstanceId();
        if (filterValue.getOperator() == QLIdOperator.LIKE) {
          filtersForInstanceIdQuery.add(
              QLBillingDataFilter.builder()
                  .instanceName(
                      QLIdFilter.builder().operator(filterValue.getOperator()).values(filterValue.getValues()).build())
                  .build());
        } else {
          filtersForInstanceIdQuery.add(filter);
        }
      }
    });
    return filtersForInstanceIdQuery;
  }

  private boolean isGroupByPodPresent(List<QLCCMEntityGroupBy> groupByNodeAndPodList) {
    for (QLCCMEntityGroupBy entityGroupBy : groupByNodeAndPodList) {
      if (entityGroupBy == QLCCMEntityGroupBy.Pod) {
        return true;
      }
    }
    return false;
  }

  private boolean isGroupByLabelPresent(List<QLCCMGroupBy> groupByList) {
    for (QLCCMGroupBy groupBy : groupByList) {
      if (groupBy.getLabelAggregation() != null) {
        return true;
      }
    }
    return false;
  }

  private boolean isGroupByTagPresent(List<QLCCMGroupBy> groupByList) {
    for (QLCCMGroupBy groupBy : groupByList) {
      if (groupBy.getTagAggregation() != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected QLData postFetch(String accountId, List<QLCCMGroupBy> groupByList,
      List<QLCCMAggregationFunction> aggregationFunctions, List<QLBillingSortCriteria> sortCriteria, QLData qlData,
      Integer limit, boolean includeOthers) {
    return null;
  }

  @Override
  public String getEntityType() {
    return null;
  }

  @Override
  protected QLData fetch(String accountId, List<QLCCMAggregationFunction> aggregateFunction,
      List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy, List<QLBillingSortCriteria> sort, Integer limit,
      Integer offset) {
    return null;
  }

  private List<String> getSelectedFields(DataFetchingEnvironment dataFetchingEnvironment) {
    Set<String> selectedFields = new HashSet<>();
    List<SelectedField> selectionSet = dataFetchingEnvironment.getSelectionSet().getFields();
    selectionSet.forEach(field -> selectedFields.add(field.getName()));
    return new ArrayList<>(selectedFields);
  }

  private K8sLabelFilter prepareLabelFilters(
      List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy, String accountId, int limit, int offset) {
    Optional<QLBillingDataFilter> labelFilter =
        filters.stream().filter(filter -> filter.getLabelSearch() != null).findFirst();
    Optional<QLCCMGroupBy> labelGroupBy =
        groupBy.stream().filter(entry -> entry.getLabelAggregation() != null).findFirst();
    String searchString = EMPTY;
    String labelName = EMPTY;
    if (labelGroupBy.isPresent()) {
      labelName = labelGroupBy.get().getLabelAggregation().getName();
    }
    if (labelFilter.isPresent()) {
      searchString = labelFilter.get().getLabelSearch().getValues()[0];
    }
    QLTimeFilter startTimeFilter = billingDataQueryBuilder.getStartTimeFilter(filters);
    long startTime = startTimeFilter.getValue().longValue();
    QLTimeFilter endTimeFilter = billingDataQueryBuilder.getEndTimeFilter(filters);
    long endTime = endTimeFilter.getValue().longValue();
    return K8sLabelFilter.builder()
        .accountId(accountId)
        .startTime(startTime)
        .endTime(endTime)
        .limit(limit)
        .offset(offset)
        .labelName(labelName)
        .searchString(searchString)
        .build();
  }

  private TagFilter prepareTagFilters(
      List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy, String accountId, int limit, int offset) {
    Optional<QLBillingDataFilter> tagFilter =
        filters.stream().filter(filter -> filter.getTagSearch() != null).findFirst();
    Optional<QLCCMGroupBy> tagGroupBy = groupBy.stream().filter(entry -> entry.getTagAggregation() != null).findFirst();
    String searchString = EMPTY;
    String tagName = EMPTY;
    if (tagGroupBy.isPresent()) {
      tagName = tagGroupBy.get().getTagAggregation().getTagName();
    }
    if (tagFilter.isPresent()) {
      searchString = tagFilter.get().getTagSearch().getValues()[0];
    }
    return TagFilter.builder()
        .accountId(accountId)
        .limit(limit)
        .offset(offset)
        .tagName(tagName)
        .searchString(searchString)
        .build();
  }

  @Override
  public boolean isCESampleAccountIdAllowed() {
    return true;
  }
}
