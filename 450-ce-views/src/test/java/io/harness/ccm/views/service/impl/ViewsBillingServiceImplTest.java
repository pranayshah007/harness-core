/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.rule.OwnerRule.ROHIT;
import static io.harness.rule.OwnerRule.SAHILDEEP;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.helper.ModuleLicenseHelper;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.currency.Currency;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.helper.BusinessMappingTestHelper;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.CEView;
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
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.entities.ViewVisualization;
import io.harness.ccm.views.graphql.QLCESortOrder;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGridData;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewSortType;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewTimeGroupType;
import io.harness.ccm.views.graphql.QLCEViewTimeTruncGroupBy;
import io.harness.ccm.views.graphql.QLCEViewTrendData;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.graphql.ViewCostData;
import io.harness.ccm.views.graphql.ViewsMetaDataFields;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.helper.BusinessMappingDataSourceHelper;
import io.harness.ccm.views.helper.BusinessMappingSharedCostHelper;
import io.harness.ccm.views.helper.ViewBillingServiceHelper;
import io.harness.ccm.views.helper.ViewBusinessMappingResponseHelper;
import io.harness.ccm.views.helper.ViewParametersHelper;
import io.harness.ccm.views.service.CEViewPreferenceService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.DataResponseService;
import io.harness.ccm.views.service.LabelFlattenedService;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValue.Attribute;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableResult;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class ViewsBillingServiceImplTest extends CategoryTest {
  private static final String CLUSTER_ID = "clusterId";
  private static final String CLUSTER_NAME = "clusterName";
  private static final String CLUSTER = "cluster";
  private static final String LABEL_KEY = "labelKey";
  private static final String LABEL_KEY_NAME = "labelKeyName";
  private static final String LABEL_VALUE = "labelValue";
  private static final String LABEL_FIELD_NAME = "labelFieldName";
  private static final String BUSINESS_MAPPING_ID = "businessMappingId";
  private static final String BUSINESS_MAPPING_NAME = "businessMappingName";
  private static final String COST_BUCKET = "costBucket";
  private static final String GCP_INVOICE_MONTH = "gcpInvoiceMonth";
  private static final String AWS_ACCOUNT = "awsUsageaccountid";
  private static final String AWS_ACCOUNT_ID = "awsAccountId";
  private static final String WORKLOAD_NAME = "workloadName";
  private static final String NAMESPACE = "namespace";
  private static final String ACCOUNT_ID = "accountId";
  private static final String AWS_USAGE_ACCOUNT_ID = "awsUsageAccountId";
  private static final String CLUSTER_PERSPECTIVE_ID = "clusterPerspectiveId";
  private static final String AWS_PERSPECTIVE_ID = "awsPerspectiveId";
  private static final String LABEL_PERSPECTIVE_ID = "labelPerspectiveId";
  private static final String BUSINESS_MAPPING_PERSPECTIVE_ID = "businessMappingPerspectiveId";
  private static final String StART_TIME_MIN = "startTime_MIN";
  private static final String StART_TIME_MAX = "startTime_MAX";
  private static final String COST = "1000";
  private static final String IDLE_COST = "100";
  private static final String UNALLOCATED_COST = "150";
  private static final String SYSTEM_COST = "50";
  private static final String TOTAL_COUNT = "324";
  private static final String CLOUD_PROVIDER_TABLE = "project.dataset.table";
  private static final int LIMIT = 2;
  private static final long ONE_DAY_IN_MILLIS = 86400000L;
  private static final String MONTH_TWO_DIGITS_FORMAT_SPECIFIER = "%02d";
  private static final String YYYY_MM = "%s%s";
  private static final String COST_COLUMN = "cost";
  private static final String ACCOUNT_FIELD_NAME = "Account";

  @InjectMocks @Spy private ViewsBillingServiceImpl viewsBillingService;
  @InjectMocks @Spy private ViewBillingServiceHelper viewBillingServiceHelper;
  @InjectMocks @Spy private ViewParametersHelper viewParametersHelper;
  @InjectMocks @Spy private ViewBusinessMappingResponseHelper viewBusinessMappingResponseHelper;
  @InjectMocks @Spy private ViewsQueryBuilder viewsQueryBuilder;
  @InjectMocks @Spy private BusinessMappingSharedCostHelper businessMappingSharedCostHelper;
  @Mock private DataResponseService dataResponseService;
  @Mock private ViewsQueryHelper viewsQueryHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private CEViewService viewService;
  @Mock private CEViewPreferenceService ceViewPreferenceService;
  @Mock private AwsAccountFieldHelper awsAccountFieldHelper;
  @Mock private BusinessMappingDataSourceHelper businessMappingDataSourceHelper;
  @Mock private BusinessMappingService businessMappingService;
  @Mock private BigQuery bigQuery;
  @Mock private TableResult resultSet;
  @Mock private Job job;
  @Mock private FieldValueList row;
  @Mock private CEMetadataRecordDao ceMetadataRecordDao;
  @Mock private BigQueryService bigQueryService;
  @Mock private BigQueryHelper bigQueryHelper;
  @Mock private LabelFlattenedService labelFlattenedService;
  @Mock private ModuleLicenseHelper moduleLicenseHelper;

  private Schema schema;
  private List<Field> fields;
  private static QLCEViewFieldInput clusterId;
  private static QLCEViewFieldInput labelKey;
  private static QLCEViewFieldInput labelValue;
  private long currentTime;
  private long startTime;
  private int count = 0;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doCallRealMethod().when(viewsQueryBuilder).getAliasFromField(any());
    doCallRealMethod()
        .when(viewsQueryBuilder)
        .getFilterValuesQuery(any(), any(), any(), anyString(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
    doCallRealMethod()
        .when(viewsQueryBuilder)
        .getQuery(any(), any(), any(), any(), any(), any(), anyString(), any(), any());
    doCallRealMethod()
        .when(viewsQueryBuilder)
        .getQuery(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), any(), any(), any(), anyMap());
    doCallRealMethod()
        .when(viewsQueryBuilder)
        .getQuery(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), any(), any(), any(), anyMap());
    doCallRealMethod()
        .when(viewsQueryBuilder)
        .getQuery(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), any(), any(), any(), anyMap());
    doCallRealMethod()
        .when(viewsQueryBuilder)
        .getTotalCountQuery(any(), any(), any(), any(), anyString(), any(), anyMap());
    doReturn(resultSet).when(bigQuery).query(any());
    doReturn(job).when(bigQuery).create(any(JobInfo.class));
    doReturn(JobId.of(UUID.randomUUID().toString())).when(job).getJobId();
    doReturn(resultSet).when(job).getQueryResults();
    doCallRealMethod().when(viewsQueryHelper).buildQueryParams(any(), anyBoolean());
    doCallRealMethod().when(viewsQueryHelper).buildQueryParams(any(), anyBoolean(), anyBoolean());
    doCallRealMethod()
        .when(viewsQueryHelper)
        .buildQueryParams(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    doCallRealMethod()
        .when(viewsQueryHelper)
        .buildQueryParams(
            any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), anyBoolean(), anyBoolean());
    doCallRealMethod().when(viewsQueryHelper).getDefaultViewGroupBy(any());
    doCallRealMethod().when(viewsQueryHelper).getViewFieldInput(any());
    doCallRealMethod().when(viewsQueryHelper).getUpdatedFiltersForPrevPeriod(any());
    doCallRealMethod().when(viewsQueryHelper).getTrendFilters(any());
    doCallRealMethod().when(viewsQueryHelper).getTimeFilters(any());
    doCallRealMethod().when(viewsQueryHelper).getTimeFilter(any(), any());
    doCallRealMethod().when(viewsQueryHelper).getTrendBillingFilter(any(), any());
    doCallRealMethod().when(viewsQueryHelper).getRoundedDoubleValue(anyDouble());
    doCallRealMethod().when(viewsQueryHelper).getSearchValueFromBusinessMappingFilter(anyList(), anyString());
    doCallRealMethod().when(viewsQueryHelper).getBusinessMappingIdFromFilters(anyList());
    doCallRealMethod().when(viewsQueryHelper).isGcpInvoiceMonthFilterPresent(anyList());
    doCallRealMethod().when(viewsQueryHelper).getBusinessMappingIdFromGroupBy(anyList());
    doCallRealMethod().when(viewsQueryHelper).isGroupByBusinessMappingPresent(anyList());
    doCallRealMethod().when(viewsQueryHelper).getModifiedBusinessMappingLimit(anyInt(), anyBoolean(), anyBoolean());
    doCallRealMethod().when(viewsQueryHelper).getModifiedBusinessMappingOffset(anyInt(), anyBoolean(), anyBoolean());
    doCallRealMethod().when(viewsQueryHelper).buildQueryParamsWithSkipGroupBy(any(), anyBoolean());
    doCallRealMethod().when(viewsQueryHelper).getSelectedCostTargetsFromFilters(any(), anyList(), any());
    doCallRealMethod().when(viewsQueryHelper).removeBusinessMappingFilter(anyList(), anyString());
    doCallRealMethod().when(viewsQueryHelper).isGroupByNonePresent(anyList());
    doCallRealMethod().when(viewsQueryHelper).removeGroupByNone(anyList());

    doCallRealMethod().when(viewsQueryBuilder).getViewFieldInput(any());
    doCallRealMethod().when(viewsQueryBuilder).mapConditionToFilter(any());
    doCallRealMethod().when(viewsQueryBuilder).getModifiedQLCEViewFieldInput(any(), anyBoolean());
    doCallRealMethod()
        .when(viewsQueryBuilder)
        .getSharedCostQuery(anyList(), anyList(), anyMap(), anyDouble(), any(), any(), any(), anyString(), anyBoolean(),
            any(), anyMap());
    doCallRealMethod()
        .when(viewsQueryBuilder)
        .getSharedCostOuterQuery(anyList(), anyList(), anyList(), any(), anyString(), anyBoolean(), any(), anyMap());

    doCallRealMethod().when(viewParametersHelper).isDataFilteredByAwsAccount(anyList());
    doCallRealMethod().when(viewParametersHelper).getBusinessMappingIds(anyList(), anyString());
    doCallRealMethod().when(viewParametersHelper).getStartTimeForTrendFilters(anyList());
    doCallRealMethod().when(viewParametersHelper).getFiltersForEntityStatsCostTrend(anyList());
    doCallRealMethod().when(viewParametersHelper).getAggregationsForEntityStatsCostTrend(anyList());
    doCallRealMethod().when(viewParametersHelper).isClusterPerspective(anyList(), anyList());
    doCallRealMethod().when(viewParametersHelper).addAdditionalRequiredGroupBy(anyList());
    doCallRealMethod().when(viewParametersHelper).getModifiedAggregations(anyList());
    doCallRealMethod().when(viewParametersHelper).getModifiedSort(anyList());

    doCallRealMethod()
        .when(viewBillingServiceHelper)
        .getQuery(anyList(), anyList(), anyList(), anyList(), anyString(), any(), anyList(), anyMap(), any());
    doCallRealMethod()
        .when(viewBillingServiceHelper)
        .getSharedCostOuterQuery(anyList(), anyList(), anyList(), anyBoolean(), any(), anyString(), any(), anyMap());
    doCallRealMethod()
        .when(viewBillingServiceHelper)
        .getSharedCostUnionQuery(anyList(), anyList(), anyList(), anyString(), any(), any(), anyMap(), anyDouble(),
            anySet(), anyBoolean(), anyMap(), any());

    doCallRealMethod()
        .when(businessMappingSharedCostHelper)
        .getEntityStatsSharedCostDataQueryForCostTrend(
            anyList(), anyList(), anyList(), anyList(), anyString(), any(), anyList(), anyList(), anyMap(), any());

    doCallRealMethod().when(awsAccountFieldHelper).spiltAndSortAWSAccountIdListBasedOnAccountName(anyList());
    doCallRealMethod()
        .when(viewBusinessMappingResponseHelper)
        .costCategoriesPostFetchResponseUpdate(any(), anyString(), anyList(), anyMap(), anyInt(), anyInt());

    clusterId = QLCEViewFieldInput.builder()
                    .fieldId(CLUSTER_ID)
                    .fieldName(CLUSTER)
                    .identifier(ViewFieldIdentifier.CLUSTER)
                    .identifierName(ViewFieldIdentifier.CLUSTER.getDisplayName())
                    .build();

    labelKey = QLCEViewFieldInput.builder()
                   .fieldId(ViewsMetaDataFields.LABEL_KEY.getFieldName())
                   .identifier(ViewFieldIdentifier.LABEL)
                   .identifierName(ViewFieldIdentifier.LABEL.getDisplayName())
                   .build();
    doReturn(Collections.singletonList(LABEL_KEY))
        .when(viewsBillingService)
        .convertToFilterValuesData(resultSet, Collections.singletonList(labelKey), false);

    labelValue = QLCEViewFieldInput.builder()
                     .fieldId(ViewsMetaDataFields.LABEL_VALUE.getFieldName())
                     .fieldName(LABEL_KEY_NAME)
                     .identifier(ViewFieldIdentifier.LABEL)
                     .identifierName(ViewFieldIdentifier.LABEL.getDisplayName())
                     .build();
    doReturn(Collections.singletonList(LABEL_VALUE))
        .when(viewsBillingService)
        .convertToFilterValuesData(resultSet, Collections.singletonList(labelValue), false);

    currentTime = System.currentTimeMillis();
    startTime = currentTime - 7 * ONE_DAY_IN_MILLIS;

    when(row.get(CLUSTER_ID)).thenReturn(FieldValue.of(Attribute.PRIMITIVE, CLUSTER));
    when(row.get(CLUSTER_NAME)).thenReturn(FieldValue.of(Attribute.PRIMITIVE, CLUSTER));
    when(row.get(WORKLOAD_NAME)).thenReturn(FieldValue.of(Attribute.PRIMITIVE, WORKLOAD_NAME));
    when(row.get(NAMESPACE)).thenReturn(FieldValue.of(Attribute.PRIMITIVE, NAMESPACE));
    when(row.get(AWS_USAGE_ACCOUNT_ID)).thenReturn(FieldValue.of(Attribute.PRIMITIVE, AWS_USAGE_ACCOUNT_ID));
    when(row.get(LABEL_KEY)).thenReturn(FieldValue.of(Attribute.PRIMITIVE, LABEL_KEY));
    when(row.get(BUSINESS_MAPPING_NAME)).thenReturn(FieldValue.of(Attribute.PRIMITIVE, BUSINESS_MAPPING_NAME));
    when(row.get(COST_COLUMN)).thenReturn(FieldValue.of(Attribute.PRIMITIVE, COST));
    when(row.get("billingamount")).thenReturn(FieldValue.of(Attribute.PRIMITIVE, COST));
    when(row.get("actualidlecost")).thenReturn(FieldValue.of(Attribute.PRIMITIVE, IDLE_COST));
    when(row.get("unallocatedcost")).thenReturn(FieldValue.of(Attribute.PRIMITIVE, UNALLOCATED_COST));
    when(row.get("systemcost")).thenReturn(FieldValue.of(Attribute.PRIMITIVE, SYSTEM_COST));
    when(row.get("memoryactualidlecost")).thenReturn(FieldValue.of(Attribute.PRIMITIVE, IDLE_COST));
    when(row.get("totalCount")).thenReturn(FieldValue.of(Attribute.PRIMITIVE, TOTAL_COUNT));
    when(row.get(StART_TIME_MIN)).thenReturn(FieldValue.of(Attribute.PRIMITIVE, String.valueOf(startTime)));
    when(row.get(StART_TIME_MAX)).thenReturn(FieldValue.of(Attribute.PRIMITIVE, String.valueOf(currentTime)));

    when(resultSet.iterateAll()).thenReturn(new Iterable<FieldValueList>() {
      @NotNull
      @Override
      public Iterator<FieldValueList> iterator() {
        return new Iterator<FieldValueList>() {
          @Override
          public boolean hasNext() {
            if (count < LIMIT) {
              count++;
              return true;
            } else {
              count = 0;
              return false;
            }
          }

          @Override
          public FieldValueList next() {
            return row;
          }
        };
      }
    });

    when(viewService.get(CLUSTER_PERSPECTIVE_ID))
        .thenReturn(getMockPerspective(CLUSTER_NAME, "Cluster Name", ViewFieldIdentifier.CLUSTER,
            ViewIdOperator.NOT_NULL, Collections.singletonList("")));
    when(viewService.get(AWS_PERSPECTIVE_ID))
        .thenReturn(getMockPerspective(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS,
            ViewIdOperator.NOT_NULL, Collections.singletonList("")));
    when(viewService.get(LABEL_PERSPECTIVE_ID))
        .thenReturn(getMockPerspective(LABEL_KEY, LABEL_FIELD_NAME, ViewFieldIdentifier.LABEL, ViewIdOperator.NOT_NULL,
            Collections.singletonList("")));
    when(viewService.get(BUSINESS_MAPPING_PERSPECTIVE_ID))
        .thenReturn(getMockPerspective(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING,
            ViewIdOperator.NOT_NULL, Collections.singletonList("")));
    when(businessMappingService.get(anyString())).thenReturn(getMockBusinessMapping());
    when(businessMappingDataSourceHelper.getBusinessMappingViewFieldIdentifiersFromIdFilters(anyList()))
        .thenReturn(Collections.emptySet());
    when(businessMappingDataSourceHelper.getBusinessMappingViewFieldIdentifiersFromRuleFilters(anyList()))
        .thenReturn(Collections.emptySet());
    when(businessMappingDataSourceHelper.getBusinessMappingViewFieldIdentifiersFromGroupBys(anyList()))
        .thenReturn(Collections.emptySet());
    when(businessMappingDataSourceHelper.getBusinessMappingViewFieldIdentifiersFromViewRules(anyList()))
        .thenReturn(Collections.emptySet());
    when(ceMetadataRecordDao.getDestinationCurrency(anyString())).thenReturn(Currency.USD);
    when(bigQueryService.get()).thenReturn(bigQuery);
    when(bigQueryHelper.getCloudProviderTableName(any(), any())).thenReturn(CLOUD_PROVIDER_TABLE);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getFilterValueStats() {
    doReturn(Collections.singletonList(CLUSTER))
        .when(viewsBillingService)
        .convertToFilterValuesData(resultSet, Collections.singletonList(clusterId), true);
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder().field(clusterId).values(new String[] {""}).build())
                    .build());
    doReturn(Collections.singletonList(filters.get(0).getIdFilter()))
        .when(awsAccountFieldHelper)
        .addAccountIdsByAwsAccountNameFilter(Collections.singletonList(filters.get(0).getIdFilter()), null);
    List<String> filterValueStats = viewsBillingService.getFilterValueStats(filters, 10, 0);
    assertThat(filterValueStats.get(0)).isEqualTo(CLUSTER);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getFilterValueStatsQuery() {
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder().field(clusterId).values(new String[] {CLUSTER}).build())
                    .build());
    doReturn(Collections.singletonList(filters.get(0).getIdFilter()))
        .when(awsAccountFieldHelper)
        .addAccountIdsByAwsAccountNameFilter(Collections.singletonList(filters.get(0).getIdFilter()), null);
    List<String> filterValueStats = viewsBillingService.getFilterValueStats(filters, 10, 0);
    assertThat(filterValueStats.get(0)).isEqualTo(CLUSTER);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getFilterValueStatsLabelKey() {
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder().field(labelKey).values(new String[] {""}).build())
                    .build());
    doReturn(Collections.singletonList(filters.get(0).getIdFilter()))
        .when(awsAccountFieldHelper)
        .addAccountIdsByAwsAccountNameFilter(Collections.singletonList(filters.get(0).getIdFilter()), null);
    List<String> filterValueStats = viewsBillingService.getFilterValueStats(filters, 10, 0);
    assertThat(filterValueStats.get(0)).isEqualTo(LABEL_KEY);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getFilterValueStatsLabelValue() {
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder().field(labelValue).values(new String[] {""}).build())
                    .build());
    doReturn(Collections.singletonList(filters.get(0).getIdFilter()))
        .when(awsAccountFieldHelper)
        .addAccountIdsByAwsAccountNameFilter(Collections.singletonList(filters.get(0).getIdFilter()), null);
    List<String> filterValueStats = viewsBillingService.getFilterValueStats(filters, 10, 0);
    assertThat(filterValueStats.get(0)).isEqualTo(LABEL_VALUE);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void getFilterValueStatsNg() {
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(QLCEViewFilterWrapper.builder()
                    .idFilter(QLCEViewFilter.builder().field(clusterId).values(new String[] {CLUSTER}).build())
                    .build());
    doReturn(Collections.singletonList(filters.get(0).getIdFilter()))
        .when(awsAccountFieldHelper)
        .addAccountIdsByAwsAccountNameFilter(Collections.singletonList(filters.get(0).getIdFilter()), ACCOUNT_ID);
    List<String> filterValueStats =
        viewsBillingService.getFilterValueStatsNg(filters, 10, 0, getMockViewQueryParams(false));
    assertThat(filterValueStats.get(0)).isEqualTo(CLUSTER);

    // Cluster table query
    filterValueStats = viewsBillingService.getFilterValueStatsNg(filters, 10, 0, getMockViewQueryParams(true));
    assertThat(filterValueStats.get(0)).isEqualTo(CLUSTER);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGetFilterValueStatsNgBusinessMapping() {
    QLCEViewFieldInput businessMappingFieldInput =
        QLCEViewFieldInput.builder()
            .fieldId(BUSINESS_MAPPING_ID)
            .identifier(ViewFieldIdentifier.BUSINESS_MAPPING)
            .identifierName(ViewFieldIdentifier.BUSINESS_MAPPING.getDisplayName())
            .build();
    doReturn(Collections.singletonList(COST_BUCKET))
        .when(businessMappingService)
        .getCostTargetNames(BUSINESS_MAPPING_ID, ACCOUNT_ID, "");
    List<QLCEViewFilterWrapper> filters = Collections.singletonList(
        QLCEViewFilterWrapper.builder()
            .idFilter(QLCEViewFilter.builder().field(businessMappingFieldInput).values(new String[] {""}).build())
            .build());
    List<String> filterValueStats =
        viewsBillingService.getFilterValueStatsNg(filters, 10, 0, getMockViewQueryParams(false));
    assertThat(filterValueStats.get(0)).isEqualTo(COST_BUCKET);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGetFilterValueStatsNgGCPInvoiceMonthFreeEditionModuleLicense() {
    QLCEViewFieldInput gcpInvoiceMonthFieldInput = QLCEViewFieldInput.builder()
                                                       .fieldId(GCP_INVOICE_MONTH)
                                                       .identifier(ViewFieldIdentifier.GCP)
                                                       .identifierName(ViewFieldIdentifier.GCP.getDisplayName())
                                                       .build();
    doReturn(true).when(moduleLicenseHelper).isFreeEditionModuleLicense(ACCOUNT_ID);
    List<QLCEViewFilterWrapper> filters = Collections.singletonList(
        QLCEViewFilterWrapper.builder()
            .idFilter(QLCEViewFilter.builder().field(gcpInvoiceMonthFieldInput).values(new String[] {""}).build())
            .build());
    List<String> filterValueStats =
        viewsBillingService.getFilterValueStatsNg(filters, 100, 0, getMockViewQueryParams(false));
    YearMonth yearMonth = YearMonth.now();
    assertThat(filterValueStats.size()).isEqualTo(2);
    assertThat(filterValueStats.get(0))
        .isEqualTo(String.format(YYYY_MM, yearMonth.getYear(),
            String.format(MONTH_TWO_DIGITS_FORMAT_SPECIFIER, yearMonth.getMonth().getValue())));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGetFilterValueStatsNgGCPInvoiceMonthPaidEditionModuleLicense() {
    QLCEViewFieldInput gcpInvoiceMonthFieldInput = QLCEViewFieldInput.builder()
                                                       .fieldId(GCP_INVOICE_MONTH)
                                                       .identifier(ViewFieldIdentifier.GCP)
                                                       .identifierName(ViewFieldIdentifier.GCP.getDisplayName())
                                                       .build();
    doReturn(false).when(moduleLicenseHelper).isFreeEditionModuleLicense(ACCOUNT_ID);
    List<QLCEViewFilterWrapper> filters = Collections.singletonList(
        QLCEViewFilterWrapper.builder()
            .idFilter(QLCEViewFilter.builder().field(gcpInvoiceMonthFieldInput).values(new String[] {""}).build())
            .build());
    List<String> filterValueStats =
        viewsBillingService.getFilterValueStatsNg(filters, 100, 0, getMockViewQueryParams(false));
    YearMonth yearMonth = YearMonth.now();
    assertThat(filterValueStats.size()).isEqualTo(12);
    assertThat(filterValueStats.get(0))
        .isEqualTo(String.format(YYYY_MM, yearMonth.getYear(),
            String.format(MONTH_TWO_DIGITS_FORMAT_SPECIFIER, yearMonth.getMonth().getValue())));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGetFilterValueStatsNgAWSAccount() {
    QLCEViewFieldInput awsInvoiceMonthFieldInput = QLCEViewFieldInput.builder()
                                                       .fieldId(AWS_ACCOUNT)
                                                       .identifier(ViewFieldIdentifier.AWS)
                                                       .identifierName(ViewFieldIdentifier.AWS.getDisplayName())
                                                       .build();
    doReturn(Collections.singletonList(AWS_ACCOUNT_ID))
        .when(viewsBillingService)
        .convertToFilterValuesData(resultSet, Collections.singletonList(awsInvoiceMonthFieldInput), false);
    List<QLCEViewFilterWrapper> filters = Collections.singletonList(
        QLCEViewFilterWrapper.builder()
            .idFilter(QLCEViewFilter.builder().field(awsInvoiceMonthFieldInput).values(new String[] {""}).build())
            .build());
    doReturn(Collections.singletonList(filters.get(0).getIdFilter()))
        .when(awsAccountFieldHelper)
        .addAccountIdsByAwsAccountNameFilter(Collections.singletonList(filters.get(0).getIdFilter()), ACCOUNT_ID);
    doReturn(Collections.singletonList(AWS_ACCOUNT_ID))
        .when(awsAccountFieldHelper)
        .mergeAwsAccountNameWithValues(Collections.singletonList(AWS_ACCOUNT_ID), ACCOUNT_ID);
    List<String> filterValueStats =
        viewsBillingService.getFilterValueStatsNg(filters, 100, 0, getMockViewQueryParams(false));
    assertThat(filterValueStats.get(0)).isEqualTo(AWS_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testClusterDataSourcesTrue() {
    assertThat(viewParametersHelper.isClusterDataSources(ImmutableSet.of(ViewFieldIdentifier.CLUSTER))).isTrue();
    assertThat(viewParametersHelper.isClusterDataSources(
                   ImmutableSet.of(ViewFieldIdentifier.CLUSTER, ViewFieldIdentifier.COMMON)))
        .isTrue();
    assertThat(viewParametersHelper.isClusterDataSources(
                   ImmutableSet.of(ViewFieldIdentifier.CLUSTER, ViewFieldIdentifier.LABEL)))
        .isTrue();
    assertThat(viewParametersHelper.isClusterDataSources(
                   ImmutableSet.of(ViewFieldIdentifier.CLUSTER, ViewFieldIdentifier.COMMON, ViewFieldIdentifier.LABEL)))
        .isTrue();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testClusterDataSourcesFalse() {
    assertThat(viewParametersHelper.isClusterDataSources(ImmutableSet.of(ViewFieldIdentifier.AWS))).isFalse();
    assertThat(viewParametersHelper.isClusterDataSources(ImmutableSet.of(ViewFieldIdentifier.COMMON))).isFalse();
    assertThat(viewParametersHelper.isClusterDataSources(ImmutableSet.of(ViewFieldIdentifier.LABEL))).isFalse();
    assertThat(viewParametersHelper.isClusterDataSources(
                   ImmutableSet.of(ViewFieldIdentifier.COMMON, ViewFieldIdentifier.LABEL)))
        .isFalse();
    assertThat(
        viewParametersHelper.isClusterDataSources(ImmutableSet.of(ViewFieldIdentifier.AZURE, ViewFieldIdentifier.GCP)))
        .isFalse();
    assertThat(viewParametersHelper.isClusterDataSources(
                   ImmutableSet.of(ViewFieldIdentifier.AZURE, ViewFieldIdentifier.COMMON, ViewFieldIdentifier.LABEL)))
        .isFalse();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testClusterPerspectiveGrid() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(CLUSTER_NAME, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(CLUSTER_NAME, "Cluster Name", ViewFieldIdentifier.CLUSTER));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    List<QLCEViewEntityStatsDataPoint> data =
        viewsBillingService.getEntityStatsDataPoints(filters, groupBy, aggregations, sortCriteria, 100, 0);

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.get(0).getName()).isEqualTo(CLUSTER);
    assertThat(data.get(0).getCost()).isEqualTo(Double.valueOf(COST));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testClusterPerspectiveGridNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder("billingamount", LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder("actualidlecost", LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder("memoryactualidlecost", LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(CLUSTER_NAME, LegacySQLTypeName.STRING).build());
    fields.add(Field.newBuilder(NAMESPACE, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));
    aggregations.add(getAggregation("actualidlecost", QLCEViewAggregateOperation.SUM));
    aggregations.add(getAggregation("memoryactualidlecost", QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(NAMESPACE, "Namespace Id", ViewFieldIdentifier.CLUSTER));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    QLCEViewGridData data = viewsBillingService.getEntityStatsDataPointsNg(
        filters, groupBy, aggregations, sortCriteria, 100, 0, null, getMockViewQueryParams(true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getClusterData().getClusterName()).isEqualTo(CLUSTER);
    assertThat(data.getData().get(0).getClusterData().getNamespace()).isEqualTo(NAMESPACE);
    assertThat(data.getData().get(0).getCost()).isEqualTo(Double.valueOf(COST));
    assertThat(data.getData().get(0).getClusterData().getIdleCost()).isEqualTo(Double.valueOf(IDLE_COST));
    assertThat(data.getData().get(0).getClusterData().getMemoryActualIdleCost()).isEqualTo(Double.valueOf(IDLE_COST));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testCloudPerspectiveGridNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(AWS_USAGE_ACCOUNT_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(AWS_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    QLCEViewGridData data = viewsBillingService.getEntityStatsDataPointsNg(
        filters, groupBy, aggregations, sortCriteria, 100, 0, null, getMockViewQueryParams(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getName()).isEqualTo(AWS_USAGE_ACCOUNT_ID);
    assertThat(data.getData().get(0).getCost()).isEqualTo(Double.valueOf(COST));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testClusterPerspectiveGridNgWithoutViewMetadata() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder("billingamount", LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder("actualidlecost", LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder("memoryactualidlecost", LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(WORKLOAD_NAME, LegacySQLTypeName.STRING).build());
    fields.add(Field.newBuilder(CLUSTER_NAME, LegacySQLTypeName.STRING).build());
    fields.add(Field.newBuilder(NAMESPACE, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveFilter(
        NAMESPACE, "Namespace", ViewFieldIdentifier.CLUSTER, QLCEViewFilterOperator.IN, new String[] {NAMESPACE}));
    filters.add(getPerspectiveFilter(
        CLUSTER_NAME, "Cluster Name", ViewFieldIdentifier.CLUSTER, QLCEViewFilterOperator.IN, new String[] {CLUSTER}));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));
    aggregations.add(getAggregation("actualidlecost", QLCEViewAggregateOperation.SUM));
    aggregations.add(getAggregation("memoryactualidlecost", QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(WORKLOAD_NAME, "Workload Id", ViewFieldIdentifier.CLUSTER));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    QLCEViewGridData data = viewsBillingService.getEntityStatsDataPointsNg(
        filters, groupBy, aggregations, sortCriteria, 100, 0, null, getMockViewQueryParams(true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getClusterData().getClusterName()).isEqualTo(CLUSTER);
    assertThat(data.getData().get(0).getClusterData().getNamespace()).isEqualTo(NAMESPACE);
    assertThat(data.getData().get(0).getClusterData().getWorkloadName()).isEqualTo(WORKLOAD_NAME);
    assertThat(data.getData().get(0).getCost()).isEqualTo(Double.valueOf(COST));
    assertThat(data.getData().get(0).getClusterData().getIdleCost()).isEqualTo(Double.valueOf(IDLE_COST));
    assertThat(data.getData().get(0).getClusterData().getMemoryActualIdleCost()).isEqualTo(Double.valueOf(IDLE_COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testLabelPerspectiveGridNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(LABEL_KEY, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(LABEL_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(LABEL_KEY, LABEL_FIELD_NAME, ViewFieldIdentifier.LABEL));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    QLCEViewGridData data = viewsBillingService.getEntityStatsDataPointsNg(
        filters, groupBy, aggregations, sortCriteria, 100, 0, null, getMockViewQueryParams(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getName()).isEqualTo(LABEL_KEY);
    assertThat(data.getData().get(0).getCost()).isEqualTo(Double.valueOf(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveGridNgGroupByCostCategory() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(BUSINESS_MAPPING_NAME, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    QLCEViewGridData data = viewsBillingService.getEntityStatsDataPointsNg(
        filters, groupBy, aggregations, sortCriteria, 100, 0, null, getMockViewQueryParams(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getName()).isEqualTo(BUSINESS_MAPPING_NAME);
    assertThat(data.getData().get(0).getCost()).isEqualTo(Double.valueOf(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveGridNgGroupByAccount() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(AWS_USAGE_ACCOUNT_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    QLCEViewGridData data = viewsBillingService.getEntityStatsDataPointsNg(
        filters, groupBy, aggregations, sortCriteria, 100, 0, null, getMockViewQueryParams(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getName()).isEqualTo(AWS_USAGE_ACCOUNT_ID);
    assertThat(data.getData().get(0).getCost()).isEqualTo(Double.valueOf(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveGridNgGroupByAccountSharedCostQuery() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(AWS_USAGE_ACCOUNT_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);
    when(viewParametersHelper.getSharedCostBusinessMappings(anyList()))
        .thenReturn(Collections.singletonList(getMockBusinessMapping()));
    when(viewService.get(BUSINESS_MAPPING_PERSPECTIVE_ID))
        .thenReturn(getMockPerspective(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING,
            ViewIdOperator.IN, Collections.singletonList(BusinessMappingTestHelper.TEST_NAME)));
    when(dataResponseService.getCostBucketEntityCost(
             anyList(), anyList(), anyList(), anyString(), any(), anyBoolean(), any(), anyMap(), any()))
        .thenReturn(Collections.singletonMap(AWS_USAGE_ACCOUNT_ID, Double.valueOf(COST)));

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    QLCEViewGridData data = viewsBillingService.getEntityStatsDataPointsNg(
        filters, groupBy, aggregations, sortCriteria, 100, 0, null, getMockViewQueryParams(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getName()).isEqualTo(AWS_USAGE_ACCOUNT_ID);
    assertThat(data.getData().get(0).getCost()).isEqualTo(Double.valueOf(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveGridNgNullCostCategory() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(BUSINESS_MAPPING_NAME, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));
    filters.add(
        getPerspectiveFilter(BUSINESS_MAPPING_PERSPECTIVE_ID, ViewFieldIdentifier.BUSINESS_MAPPING.getDisplayName(),
            ViewFieldIdentifier.BUSINESS_MAPPING, QLCEViewFilterOperator.NULL, null));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    QLCEViewGridData data = viewsBillingService.getEntityStatsDataPointsNg(
        filters, groupBy, aggregations, sortCriteria, 100, 0, null, getMockViewQueryParams(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getName()).isEqualTo(BUSINESS_MAPPING_NAME);
    assertThat(data.getData().get(0).getCost()).isEqualTo(Double.valueOf(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveGridNgNotNullCostCategory() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(BUSINESS_MAPPING_NAME, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));
    filters.add(
        getPerspectiveFilter(BUSINESS_MAPPING_PERSPECTIVE_ID, ViewFieldIdentifier.BUSINESS_MAPPING.getDisplayName(),
            ViewFieldIdentifier.BUSINESS_MAPPING, QLCEViewFilterOperator.NOT_NULL, null));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    QLCEViewGridData data = viewsBillingService.getEntityStatsDataPointsNg(
        filters, groupBy, aggregations, sortCriteria, 100, 0, null, getMockViewQueryParams(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getName()).isEqualTo(BUSINESS_MAPPING_NAME);
    assertThat(data.getData().get(0).getCost()).isEqualTo(Double.valueOf(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testLabelCostCategoryPerspectiveGridNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(BUSINESS_MAPPING_NAME, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);
    when(businessMappingService.get(anyString())).thenReturn(getMockLabelBusinessMapping());

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective grid query
    QLCEViewGridData data = viewsBillingService.getEntityStatsDataPointsNg(
        filters, groupBy, aggregations, sortCriteria, 100, 0, null, getMockViewQueryParams(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getData().get(0).getName()).isEqualTo(BUSINESS_MAPPING_NAME);
    assertThat(data.getData().get(0).getCost()).isEqualTo(Double.valueOf(COST));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testClusterPerspectiveChart() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(CLUSTER_NAME, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(CLUSTER_NAME, "Cluster Name", ViewFieldIdentifier.CLUSTER));
    groupBy.add(getTimeGroupBy());

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective chart query
    TableResult data = viewsBillingService.getTimeSeriesStats(ACCOUNT_ID, filters, groupBy, aggregations, sortCriteria);

    // Assertions on result
    assertThat(data).isNotNull();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testClusterPerspectiveChartNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(CLUSTER_NAME, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(CLUSTER_NAME, "Cluster Name", ViewFieldIdentifier.CLUSTER));
    groupBy.add(getTimeGroupBy());

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective chart query
    TableResult data = viewsBillingService.getTimeSeriesStatsNg(
        filters, groupBy, aggregations, sortCriteria, false, 12, null, getMockViewQueryParams(true, true));

    // Assertions on result
    assertThat(data).isNotNull();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testCloudPerspectiveChartNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(AWS_USAGE_ACCOUNT_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(AWS_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective chart query
    TableResult data = viewsBillingService.getTimeSeriesStatsNg(
        filters, groupBy, aggregations, sortCriteria, false, 12, null, getMockViewQueryParams(true, true));

    // Assertions on result
    assertThat(data).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testLabelPerspectiveChartNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(LABEL_KEY, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(LABEL_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(LABEL_KEY, LABEL_KEY_NAME, ViewFieldIdentifier.LABEL));
    groupBy.add(getTimeGroupBy());

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective chart query
    TableResult data = viewsBillingService.getTimeSeriesStatsNg(
        filters, groupBy, aggregations, sortCriteria, false, 100, null, getMockViewQueryParams(false, true));

    // Assertions on result
    assertThat(data).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveChartNgGroupByCostCategory() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(BUSINESS_MAPPING_NAME, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING));
    groupBy.add(getTimeGroupBy());

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective chart query
    TableResult data = viewsBillingService.getTimeSeriesStatsNg(
        filters, groupBy, aggregations, sortCriteria, false, 12, null, getMockViewQueryParams(false, true));

    // Assertions on result
    assertThat(data).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveChartNgGroupByAccount() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(AWS_USAGE_ACCOUNT_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective chart query
    TableResult data = viewsBillingService.getTimeSeriesStatsNg(
        filters, groupBy, aggregations, sortCriteria, false, 12, null, getMockViewQueryParams(false, true));

    // Assertions on result
    assertThat(data).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveChartNgGroupByAccountSharedCostQuery() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(AWS_USAGE_ACCOUNT_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);
    when(viewParametersHelper.getSharedCostBusinessMappings(anyList()))
        .thenReturn(Collections.singletonList(getMockBusinessMapping()));
    when(viewService.get(BUSINESS_MAPPING_PERSPECTIVE_ID))
        .thenReturn(getMockPerspective(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING,
            ViewIdOperator.IN, Collections.singletonList(BusinessMappingTestHelper.TEST_NAME)));
    when(dataResponseService.getCostBucketEntityCost(
             anyList(), anyList(), anyList(), anyString(), any(), anyBoolean(), any(), anyMap(), any()))
        .thenReturn(Collections.singletonMap(AWS_USAGE_ACCOUNT_ID, Double.valueOf(COST)));

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective chart query
    TableResult data = viewsBillingService.getTimeSeriesStatsNg(
        filters, groupBy, aggregations, sortCriteria, false, 12, null, getMockViewQueryParams(false, true));

    // Assertions on result
    assertThat(data).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveChartNgCostCategoryNull() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(AWS_USAGE_ACCOUNT_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));
    filters.add(
        getPerspectiveFilter(BUSINESS_MAPPING_PERSPECTIVE_ID, ViewFieldIdentifier.BUSINESS_MAPPING.getDisplayName(),
            ViewFieldIdentifier.BUSINESS_MAPPING, QLCEViewFilterOperator.NULL, null));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective chart query
    TableResult data = viewsBillingService.getTimeSeriesStatsNg(
        filters, groupBy, aggregations, sortCriteria, false, 12, null, getMockViewQueryParams(false, true));

    // Assertions on result
    assertThat(data).isNotNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testLabelCostCategoryPerspectiveChartNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(AWS_USAGE_ACCOUNT_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);
    when(businessMappingService.get(anyString())).thenReturn(getMockLabelBusinessMapping());

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    List<QLCEViewSortCriteria> sortCriteria = Collections.singletonList(getSortCriteria());

    // Perspective chart query
    TableResult data = viewsBillingService.getTimeSeriesStatsNg(
        filters, groupBy, aggregations, sortCriteria, false, 12, null, getMockViewQueryParams(false, true));

    // Assertions on result
    assertThat(data).isNotNull();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testClusterPerspectiveSummaryCard() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.NUMERIC).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.NUMERIC).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);
    when(ceMetadataRecordDao.getDestinationCurrency(null)).thenReturn(Currency.USD);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    // Perspective SummaryCard query
    QLCEViewTrendInfo data = viewsBillingService.getTrendStatsData(filters, aggregations);

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testClusterPerspectiveSummaryCardNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder("actualidlecost", LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder("unallocatedcost", LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder("systemcost", LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.NUMERIC).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.NUMERIC).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));
    aggregations.add(getAggregation("actualidlecost", QLCEViewAggregateOperation.SUM));
    aggregations.add(getAggregation("unallocatedcost", QLCEViewAggregateOperation.SUM));
    aggregations.add(getAggregation("systemcost", QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(CLUSTER_NAME, "Cluster Name", ViewFieldIdentifier.CLUSTER));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    QLCEViewTrendData data = viewsBillingService.getTrendStatsDataNg(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(true), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getTotalCost().getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
    assertThat(data.getIdleCost().getValue()).isEqualTo(Double.valueOf(IDLE_COST));
    assertThat(data.getUnallocatedCost().getValue()).isEqualTo(Double.valueOf(UNALLOCATED_COST));
    assertThat(data.getSystemCost().getValue()).isEqualTo(Double.valueOf(SYSTEM_COST));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testCloudPerspectiveSummaryCardNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(AWS_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    QLCEViewTrendData data = viewsBillingService.getTrendStatsDataNg(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(false), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getTotalCost().getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testLabelPerspectiveSummaryCardNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(LABEL_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(LABEL_KEY, LABEL_KEY_NAME, ViewFieldIdentifier.LABEL));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    QLCEViewTrendData data = viewsBillingService.getTrendStatsDataNg(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(false), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getTotalCost().getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveSummaryCardNgGroupByCostCategory() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    QLCEViewTrendData data =
        viewsBillingService.getTrendStatsDataNg(filters, groupBy, aggregations, null, getMockViewQueryParams(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getTotalCost().getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveSummaryCardNgGroupByAccount() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    QLCEViewTrendData data = viewsBillingService.getTrendStatsDataNg(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(false), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getTotalCost().getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveSummaryCardNgGroupByAccountSharedCostQuery() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);
    when(viewParametersHelper.getSharedCostBusinessMappings(anyList()))
        .thenReturn(Collections.singletonList(getMockBusinessMapping()));
    when(viewService.get(BUSINESS_MAPPING_PERSPECTIVE_ID))
        .thenReturn(getMockPerspective(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING,
            ViewIdOperator.IN, Collections.singletonList(BusinessMappingTestHelper.TEST_NAME)));
    when(dataResponseService.getCostBucketEntityCost(
             anyList(), anyList(), anyList(), anyString(), any(), anyBoolean(), any(), anyMap(), any()))
        .thenReturn(Collections.singletonMap(AWS_USAGE_ACCOUNT_ID, Double.valueOf(COST)));

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    QLCEViewTrendData data = viewsBillingService.getTrendStatsDataNg(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(false), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getTotalCost().getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveSummaryCardNgCostCategoryNull() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(LABEL_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));
    filters.add(
        getPerspectiveFilter(BUSINESS_MAPPING_PERSPECTIVE_ID, ViewFieldIdentifier.BUSINESS_MAPPING.getDisplayName(),
            ViewFieldIdentifier.BUSINESS_MAPPING, QLCEViewFilterOperator.NULL, null));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    QLCEViewTrendData data = viewsBillingService.getTrendStatsDataNg(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(false), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getTotalCost().getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testLabelCostCategoryPerspectiveSummaryCardNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);
    when(businessMappingService.get(anyString())).thenReturn(getMockLabelBusinessMapping());

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(LABEL_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    QLCEViewTrendData data = viewsBillingService.getTrendStatsDataNg(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(false), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getTotalCost().getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testClusterPerspectiveTotalCount() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder("totalCount", LegacySQLTypeName.NUMERIC).build());
    fields.add(Field.newBuilder(CLUSTER_NAME, LegacySQLTypeName.STRING).build());
    fields.add(Field.newBuilder(NAMESPACE, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(NAMESPACE, "Namespace Id", ViewFieldIdentifier.CLUSTER));

    // Total count query
    Integer data =
        viewsBillingService.getTotalCountForQuery(filters, groupBy, null, getMockViewQueryParamsForTotalCount(true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data).isEqualTo(Integer.valueOf(TOTAL_COUNT));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCloudPerspectiveTotalCount() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder("totalCount", LegacySQLTypeName.NUMERIC).build());
    fields.add(Field.newBuilder(AWS_USAGE_ACCOUNT_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(AWS_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));

    // Total count query
    Integer data =
        viewsBillingService.getTotalCountForQuery(filters, groupBy, null, getMockViewQueryParamsForTotalCount(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data).isEqualTo(Integer.valueOf(TOTAL_COUNT));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testLabelPerspectiveTotalCount() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder("totalCount", LegacySQLTypeName.NUMERIC).build());
    fields.add(Field.newBuilder(LABEL_KEY, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(LABEL_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(LABEL_KEY, LABEL_KEY_NAME, ViewFieldIdentifier.LABEL));

    // Total count query
    Integer data =
        viewsBillingService.getTotalCountForQuery(filters, groupBy, null, getMockViewQueryParamsForTotalCount(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data).isEqualTo(Integer.valueOf(TOTAL_COUNT));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveTotalCount() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder("totalCount", LegacySQLTypeName.NUMERIC).build());
    fields.add(Field.newBuilder(BUSINESS_MAPPING_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING));

    // Total count query
    Integer data =
        viewsBillingService.getTotalCountForQuery(filters, groupBy, null, getMockViewQueryParamsForTotalCount(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data).isEqualTo(Integer.valueOf(TOTAL_COUNT));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveTotalCountSharedCostQuery() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder("totalCount", LegacySQLTypeName.NUMERIC).build());
    fields.add(Field.newBuilder(BUSINESS_MAPPING_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);
    when(viewParametersHelper.getSharedCostBusinessMappings(anyList()))
        .thenReturn(Collections.singletonList(getMockBusinessMapping()));
    when(viewService.get(BUSINESS_MAPPING_PERSPECTIVE_ID))
        .thenReturn(getMockPerspective(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING,
            ViewIdOperator.IN, Collections.singletonList(BusinessMappingTestHelper.TEST_NAME)));
    when(dataResponseService.getCostBucketEntityCost(
             anyList(), anyList(), anyList(), anyString(), any(), anyBoolean(), any(), anyMap(), any()))
        .thenReturn(Collections.singletonMap(AWS_USAGE_ACCOUNT_ID, Double.valueOf(COST)));

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));

    // Total count query
    Integer data =
        viewsBillingService.getTotalCountForQuery(filters, groupBy, null, getMockViewQueryParamsForTotalCount(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data).isEqualTo(Integer.valueOf(TOTAL_COUNT));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCostCategoryPerspectiveTotalCountCostCategoryNull() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder("totalCount", LegacySQLTypeName.NUMERIC).build());
    fields.add(Field.newBuilder(BUSINESS_MAPPING_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));
    filters.add(
        getPerspectiveFilter(BUSINESS_MAPPING_PERSPECTIVE_ID, ViewFieldIdentifier.BUSINESS_MAPPING.getDisplayName(),
            ViewFieldIdentifier.BUSINESS_MAPPING, QLCEViewFilterOperator.NULL, null));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING));

    // Total count query
    Integer data =
        viewsBillingService.getTotalCountForQuery(filters, groupBy, null, getMockViewQueryParamsForTotalCount(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data).isEqualTo(Integer.valueOf(TOTAL_COUNT));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testLabelCostCategoryPerspectiveTotalCount() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder("totalCount", LegacySQLTypeName.NUMERIC).build());
    fields.add(Field.newBuilder(BUSINESS_MAPPING_ID, LegacySQLTypeName.STRING).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);
    when(businessMappingService.get(anyString())).thenReturn(getMockLabelBusinessMapping());

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(BUSINESS_MAPPING_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(BUSINESS_MAPPING_ID, BUSINESS_MAPPING_NAME, ViewFieldIdentifier.BUSINESS_MAPPING));

    // Total count query
    Integer data =
        viewsBillingService.getTotalCountForQuery(filters, groupBy, null, getMockViewQueryParamsForTotalCount(false));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data).isEqualTo(Integer.valueOf(TOTAL_COUNT));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testClusterPerspectiveUnallocatedCostDataNg() {
    doReturn(true).when(viewBillingServiceHelper).shouldShowUnallocatedCost(anyList());
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder("unallocatedcost", LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.NUMERIC).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.NUMERIC).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(NAMESPACE, NAMESPACE, ViewFieldIdentifier.CLUSTER));
    groupBy.add(getTimeGroupBy());

    Map<Long, Double> data = viewsBillingService.getUnallocatedCostDataNg(filters, groupBy, Collections.emptyList(),
        null, viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(true), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data).isNotEmpty();
    assertThat(data.get(0L).doubleValue()).isEqualTo(Double.parseDouble(UNALLOCATED_COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testClusterPerspectiveOthersTotalCostDataNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(NAMESPACE, NAMESPACE, ViewFieldIdentifier.CLUSTER));
    groupBy.add(getTimeGroupBy());

    Map<Long, Double> data = viewsBillingService.getOthersTotalCostDataNg(filters, groupBy, Collections.emptyList(),
        null, viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(true), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data).isNotEmpty();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCloudPerspectiveOthersTotalCostDataNg() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(AWS_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    Map<Long, Double> data = viewsBillingService.getOthersTotalCostDataNg(filters, groupBy, Collections.emptyList(),
        null, viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(false), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data).isNotEmpty();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testClusterPerspectiveForecastCostData() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));
    when(viewsQueryHelper.getEndInstantForForecastCost(filters))
        .thenReturn(Instant.ofEpochMilli(currentTime + ONE_DAY_IN_MILLIS));
    when(viewBillingServiceHelper.getForecastCost(any(ViewCostData.class), any(Instant.class)))
        .thenReturn(LIMIT * Double.parseDouble(COST));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(CLUSTER_NAME, "Cluster Name", ViewFieldIdentifier.CLUSTER));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    QLCEViewTrendInfo data = viewsBillingService.getForecastCostData(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(true), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCloudPerspectiveForecastCostData() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));
    when(viewsQueryHelper.getEndInstantForForecastCost(filters))
        .thenReturn(Instant.ofEpochMilli(currentTime + ONE_DAY_IN_MILLIS));
    when(viewBillingServiceHelper.getForecastCost(any(ViewCostData.class), any(Instant.class)))
        .thenReturn(LIMIT * Double.parseDouble(COST));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    QLCEViewTrendInfo data = viewsBillingService.getForecastCostData(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(false), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getValue()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testClusterPerspectiveCostData() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(CLUSTER_NAME, "Cluster Name", ViewFieldIdentifier.CLUSTER));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    ViewCostData data = viewsBillingService.getCostData(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(true), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getCost()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testCloudPerspectiveCostData() {
    // Mock fields returned by result set
    fields = new ArrayList<>();
    fields.add(Field.newBuilder(COST_COLUMN, LegacySQLTypeName.FLOAT).build());
    fields.add(Field.newBuilder(StART_TIME_MIN, LegacySQLTypeName.TIMESTAMP).build());
    fields.add(Field.newBuilder(StART_TIME_MAX, LegacySQLTypeName.TIMESTAMP).build());
    schema = Schema.of(fields);
    when(resultSet.getSchema()).thenReturn(schema);

    // Build query parameters
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getPerspectiveMetadataFilter(CLUSTER_PERSPECTIVE_ID));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.AFTER, startTime));
    filters.add(getPerspectiveTimeFilter(QLCEViewTimeFilterOperator.BEFORE, currentTime));

    List<QLCEViewAggregation> aggregations = new ArrayList<>();
    aggregations.add(getAggregation(COST_COLUMN, QLCEViewAggregateOperation.SUM));

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(getEntityGroupBy(AWS_USAGE_ACCOUNT_ID, ACCOUNT_FIELD_NAME, ViewFieldIdentifier.AWS));
    groupBy.add(getTimeGroupBy());

    // Perspective SummaryCard query
    ViewCostData data = viewsBillingService.getCostData(filters, groupBy, aggregations, null,
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(getMockViewQueryParams(false), true));

    // Assertions on result
    assertThat(data).isNotNull();
    assertThat(data.getCost()).isEqualTo(LIMIT * Double.parseDouble(COST));
  }

  // Methods to build aggregations
  private QLCEViewAggregation getAggregation(String columnName, QLCEViewAggregateOperation operation) {
    return QLCEViewAggregation.builder().columnName(columnName).operationType(operation).build();
  }

  // Methods to build group by
  private QLCEViewGroupBy getEntityGroupBy(String fieldId, String fieldName, ViewFieldIdentifier identifier) {
    return QLCEViewGroupBy.builder()
        .entityGroupBy(QLCEViewFieldInput.builder()
                           .fieldId(fieldId)
                           .fieldName(fieldName)
                           .identifier(identifier)
                           .identifierName(identifier.getDisplayName())
                           .build())
        .build();
  }

  private QLCEViewGroupBy getTimeGroupBy() {
    return QLCEViewGroupBy.builder()
        .timeTruncGroupBy(QLCEViewTimeTruncGroupBy.builder().resolution(QLCEViewTimeGroupType.DAY).build())
        .build();
  }

  // Methods to build filters
  private QLCEViewFilterWrapper getPerspectiveMetadataFilter(String perspectiveId) {
    return QLCEViewFilterWrapper.builder()
        .viewMetadataFilter(QLCEViewMetadataFilter.builder().viewId(perspectiveId).isPreview(false).build())
        .build();
  }

  private QLCEViewFilterWrapper getPerspectiveTimeFilter(QLCEViewTimeFilterOperator operator, long value) {
    QLCEViewFieldInput field = QLCEViewFieldInput.builder()
                                   .fieldId("startTime")
                                   .fieldName("startTime")
                                   .identifier(ViewFieldIdentifier.COMMON)
                                   .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                                   .build();
    return QLCEViewFilterWrapper.builder()
        .timeFilter(QLCEViewTimeFilter.builder().field(field).operator(operator).value(value).build())
        .build();
  }

  private QLCEViewFilterWrapper getPerspectiveFilter(String fieldId, String fieldName, ViewFieldIdentifier identifier,
      QLCEViewFilterOperator operator, String[] values) {
    QLCEViewFieldInput field = QLCEViewFieldInput.builder()
                                   .fieldId(fieldId)
                                   .fieldName(fieldName)
                                   .identifier(identifier)
                                   .identifierName(identifier.getDisplayName())
                                   .build();
    return QLCEViewFilterWrapper.builder()
        .idFilter(QLCEViewFilter.builder().field(field).values(values).operator(operator).build())
        .build();
  }

  // Method to get sort
  private QLCEViewSortCriteria getSortCriteria() {
    return QLCEViewSortCriteria.builder().sortOrder(QLCESortOrder.DESCENDING).sortType(QLCEViewSortType.COST).build();
  }

  // Methods to get mock data
  private CEView getMockPerspective(String fieldId, String fieldName, ViewFieldIdentifier identifier,
      ViewIdOperator viewIdOperator, List<String> values) {
    ViewCondition condition = ViewIdCondition.builder()
                                  .viewField(ViewField.builder()
                                                 .fieldId(fieldId)
                                                 .fieldName(fieldName)
                                                 .identifier(identifier)
                                                 .identifierName(identifier.getDisplayName())
                                                 .build())
                                  .viewOperator(viewIdOperator)
                                  .values(values)
                                  .build();
    ViewRule rule = ViewRule.builder().viewConditions(Collections.singletonList(condition)).build();

    return CEView.builder()
        .accountId(ACCOUNT_ID)
        .name("Mock Perspective")
        .viewVersion("v1")
        .viewType(ViewType.DEFAULT)
        .viewState(ViewState.COMPLETED)
        .viewRules(Collections.singletonList(rule))
        .viewVisualization(ViewVisualization.builder()
                               .granularity(ViewTimeGranularity.DAY)
                               .chartType(ViewChartType.STACKED_TIME_SERIES)
                               .groupBy(ViewField.builder()
                                            .fieldId(fieldId)
                                            .fieldName(fieldName)
                                            .identifier(identifier)
                                            .identifierName(identifier.getDisplayName())
                                            .build())
                               .build())
        .dataSources(Collections.singletonList(identifier))
        .viewPreferences(ViewPreferences.builder().build())
        .build();
  }

  private ViewQueryParams getMockViewQueryParams(boolean isClusterQuery) {
    return ViewQueryParams.builder().accountId(ACCOUNT_ID).isClusterQuery(isClusterQuery).timeOffsetInDays(0).build();
  }

  private ViewQueryParams getMockViewQueryParamsForTotalCount(boolean isClusterQuery) {
    return ViewQueryParams.builder()
        .accountId(ACCOUNT_ID)
        .isClusterQuery(isClusterQuery)
        .isTotalCountQuery(true)
        .timeOffsetInDays(0)
        .build();
  }

  private ViewQueryParams getMockViewQueryParams(boolean isClusterQuery, boolean isTimeTruncGroupByRequired) {
    return ViewQueryParams.builder()
        .accountId(ACCOUNT_ID)
        .isClusterQuery(isClusterQuery)
        .isTimeTruncGroupByRequired(isTimeTruncGroupByRequired)
        .timeOffsetInDays(0)
        .build();
  }

  private BusinessMapping getMockBusinessMapping() {
    return BusinessMappingTestHelper.getBusinessMapping(BusinessMappingTestHelper.TEST_ID);
  }

  private BusinessMapping getMockLabelBusinessMapping() {
    return BusinessMappingTestHelper.getLabelBusinessMapping(BusinessMappingTestHelper.TEST_ID);
  }
}
