/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.TimeSeriesDataPoints;
import io.harness.ccm.billing.graphql.CloudBillingAggregate;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;
import io.harness.ccm.billing.graphql.CloudBillingIdFilter;
import io.harness.ccm.billing.graphql.CloudBillingTimeFilter;
import io.harness.ccm.billing.graphql.CloudEntityGroupBy;
import io.harness.ccm.billing.preaggregated.PreAggregateBillingServiceImpl;
import io.harness.ccm.billing.preaggregated.PreAggregateBillingTimeSeriesStatsDTO;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.type.aggregation.QLBillingDataPoint;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLReference;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class CloudTimeSeriesStatsDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock CloudBillingHelper cloudBillingHelper;
  @Mock PreAggregateBillingServiceImpl preAggregateBillingService;
  @Mock BillingDataHelper billingDataHelper;
  @Mock CeAccountExpirationChecker accountChecker;
  @InjectMocks CloudTimeSeriesStatsDataFetcher cloudTimeSeriesStatsDataFetcher;

  private static final String COST = "unblendedCost";
  private static final String DISCOUNT = "discount";
  private static final String SERVICE = "service";
  private static final String LINKED_ACCOUNT = "linkedAccount";
  private static final String USAGE_TYPE = "usageType";
  private static final String INSTANCE_TYPE = "instanceType";
  private static final String CLOUD_PROVIDER = "AWS";
  private static final String ID1 = "id1";
  private static final String ID2 = "id2";
  private static final String ID3 = "id3";
  private static final String ID4 = "id4";
  private static final String TYPE = "awsUsagetype";
  private static final Integer LIMIT = 2;

  private List<CloudBillingAggregate> cloudBillingAggregates = new ArrayList<>();
  private List<CloudBillingFilter> filters = new ArrayList<>();
  private List<CloudBillingGroupBy> groupBy = new ArrayList<>();
  private PreAggregateBillingTimeSeriesStatsDTO qlData;

  @Before
  public void setup() {
    doCallRealMethod().when(cloudBillingHelper).getCloudProvider(anyList());
    doCallRealMethod().when(cloudBillingHelper).getAggregationMapper(anyBoolean(), anyBoolean());
    doCallRealMethod().when(cloudBillingHelper).getGroupByMapper(anyBoolean(), anyBoolean());
    doCallRealMethod().when(cloudBillingHelper).getFiltersMapper(anyBoolean(), anyBoolean());
    cloudBillingAggregates.add(getBillingAggregate(COST));
    cloudBillingAggregates.add(getBillingAggregate(DISCOUNT));
    filters.addAll(Arrays.asList(getStartTimeAwsBillingFilter(0L), getServiceAwsFilter(new String[] {SERVICE}),
        getLinkedAccountsAwsFilter(new String[] {LINKED_ACCOUNT}), getUsageTypeAwsFilter(new String[] {USAGE_TYPE}),
        getRegionFilter(new String[] {INSTANCE_TYPE}), getCloudProviderFilter(new String[] {CLOUD_PROVIDER}),
        getInstanceTypeAwsFilter(new String[] {INSTANCE_TYPE})));

    groupBy.addAll(Arrays.asList(getServiceGroupBy(), getLinkedAccountsGroupBy(), getInstanceTypeGroupBy(),
        getUsageTypeGroupBy(), getRegionGroupBy()));

    when(preAggregateBillingService.getPreAggregateBillingTimeSeriesStats(
             anyList(), anyList(), anyList(), anyList(), any(), any()))
        .thenReturn(PreAggregateBillingTimeSeriesStatsDTO.builder().build());
    when(cloudBillingHelper.getCloudProviderTableName(anyString())).thenReturn("CLOUD_PROVIDER_TABLE_NAME");

    List<TimeSeriesDataPoints> dataPoints = new ArrayList<>();
    dataPoints.add(TimeSeriesDataPoints.builder()
                       .time(1589328000000L)
                       .values(Arrays.asList(QLBillingDataPoint.builder()
                                                 .key(QLReference.builder().id(ID1).name(ID1).type(TYPE).build())
                                                 .value(30)
                                                 .build(),
                           QLBillingDataPoint.builder()
                               .key(QLReference.builder().id(ID2).name(ID2).type(TYPE).build())
                               .value(40)
                               .build(),
                           QLBillingDataPoint.builder()
                               .key(QLReference.builder().id(ID3).name(ID3).type(TYPE).build())
                               .value(50)
                               .build(),
                           QLBillingDataPoint.builder()
                               .key(QLReference.builder().id(ID4).name(ID4).type(TYPE).build())
                               .value(10)
                               .build()))
                       .build());

    dataPoints.add(TimeSeriesDataPoints.builder()
                       .time(1589414400000L)
                       .values(Arrays.asList(QLBillingDataPoint.builder()
                                                 .key(QLReference.builder().id(ID1).name(ID1).type(TYPE).build())
                                                 .value(10)
                                                 .build(),
                           QLBillingDataPoint.builder()
                               .key(QLReference.builder().id(ID2).name(ID2).type(TYPE).build())
                               .value(60)
                               .build(),
                           QLBillingDataPoint.builder()
                               .key(QLReference.builder().id(ID3).name(ID3).type(TYPE).build())
                               .value(30)
                               .build()))
                       .build());

    qlData = PreAggregateBillingTimeSeriesStatsDTO.builder().stats(dataPoints).build();
    doCallRealMethod().when(billingDataHelper).getRoundedDoubleValue(anyDouble());
    doCallRealMethod().when(billingDataHelper).getElementIdsAfterLimit(any(), anyInt());
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testTimeSeriesDataFetcher() {
    QLData data =
        cloudTimeSeriesStatsDataFetcher.fetch(ACCOUNT1_ID, cloudBillingAggregates, filters, groupBy, null, 5, 0);
    assertThat(data).isEqualTo(PreAggregateBillingTimeSeriesStatsDTO.builder().build());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testTimeSeriesDataFetcherWithLabels() {
    doCallRealMethod().when(cloudBillingHelper).fetchIfRawTableQueryRequired(anyList(), anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderFilter(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderGroupBy(anyList());
    QLData data = cloudTimeSeriesStatsDataFetcher.fetch(ACCOUNT1_ID, null,
        Collections.singletonList(getCloudProviderFilter(new String[] {"GCP"})),
        Arrays.asList(getLabelsKeyGroupBy(), getLabelsValueGroupBy()), null, 5, 0);
    assertThat(data).isEqualTo(PreAggregateBillingTimeSeriesStatsDTO.builder().build());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testTimeSeriesDataFetcherWithLabelsAggregationDiscount() {
    doCallRealMethod().when(cloudBillingHelper).fetchIfRawTableQueryRequired(anyList(), anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderFilter(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderGroupBy(anyList());
    doCallRealMethod().when(cloudBillingHelper).fetchIfDiscountsAggregationPresent(cloudBillingAggregates);
    QLData data = cloudTimeSeriesStatsDataFetcher.fetch(ACCOUNT1_ID, cloudBillingAggregates,
        Collections.singletonList(getCloudProviderFilter(new String[] {"GCP"})),
        Arrays.asList(getLabelsKeyGroupBy(), getLabelsValueGroupBy()), null, 5, 0);
    assertThat(data).isEqualTo(PreAggregateBillingTimeSeriesStatsDTO.builder().build());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testTimeSeriesDataFetcherWithAwsTags() {
    doCallRealMethod().when(cloudBillingHelper).fetchIfRawTableQueryRequired(anyList(), anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderFilter(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderGroupBy(anyList());
    QLData data = cloudTimeSeriesStatsDataFetcher.fetch(ACCOUNT1_ID, null,
        Collections.singletonList(getCloudProviderFilter(new String[] {CLOUD_PROVIDER})),
        Arrays.asList(getTagsKeyGroupBy(), getTagsValueGroupBy()), null, 5, 0);
    assertThat(data).isEqualTo(PreAggregateBillingTimeSeriesStatsDTO.builder().build());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testPostFetchTimeSeriesDataFetcherIncludeOthers() {
    PreAggregateBillingTimeSeriesStatsDTO postFetchData =
        (PreAggregateBillingTimeSeriesStatsDTO) cloudTimeSeriesStatsDataFetcher.postFetch(
            ACCOUNT1_ID, null, null, null, this.qlData, LIMIT, true);
    assertThat(postFetchData.getStats().get(0).getValues().size()).isEqualTo(LIMIT + 1);
    assertThat(postFetchData.getStats().get(0).getValues().get(0).getValue()).isEqualTo(40);
    assertThat(postFetchData.getStats().get(0).getValues().get(1).getValue()).isEqualTo(50);
    assertThat(postFetchData.getStats().get(0).getValues().get(2).getValue()).isEqualTo(40.0);
    assertThat(postFetchData.getStats().get(1).getValues().get(2).getValue()).isEqualTo(10.0);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testPostFetchTimeSeriesDataFetcherExcludeOthers() {
    PreAggregateBillingTimeSeriesStatsDTO postFetchData =
        (PreAggregateBillingTimeSeriesStatsDTO) cloudTimeSeriesStatsDataFetcher.postFetch(
            ACCOUNT1_ID, null, null, null, this.qlData, LIMIT, false);
    assertThat(postFetchData.getStats().get(0).getValues().size()).isEqualTo(LIMIT);
    assertThat(postFetchData.getStats().get(0).getValues().get(0).getValue()).isEqualTo(40);
    assertThat(postFetchData.getStats().get(0).getValues().get(1).getValue()).isEqualTo(50);
    assertThat(postFetchData.getStats().get(1).getValues().get(0).getValue()).isEqualTo(60);
    assertThat(postFetchData.getStats().get(1).getValues().get(1).getValue()).isEqualTo(30);
  }

  private CloudBillingAggregate getBillingAggregate(String columnName) {
    return CloudBillingAggregate.builder().operationType(QLCCMAggregateOperation.SUM).columnName(columnName).build();
  }

  private CloudBillingFilter getStartTimeAwsBillingFilter(Long filterTime) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setPreAggregatedTableStartTime(
        CloudBillingTimeFilter.builder().operator(QLTimeOperator.AFTER).value(filterTime).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getServiceAwsFilter(String[] service) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setAwsService(
        CloudBillingIdFilter.builder().operator(QLIdOperator.EQUALS).values(service).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getLinkedAccountsAwsFilter(String[] linkedAccounts) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setAwsLinkedAccount(
        CloudBillingIdFilter.builder().operator(QLIdOperator.IN).values(linkedAccounts).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getUsageTypeAwsFilter(String[] usageType) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setAwsUsageType(
        CloudBillingIdFilter.builder().operator(QLIdOperator.NOT_IN).values(usageType).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getInstanceTypeAwsFilter(String[] instanceType) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setAwsInstanceType(
        CloudBillingIdFilter.builder().operator(QLIdOperator.NOT_NULL).values(instanceType).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getRegionFilter(String[] region) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setRegion(CloudBillingIdFilter.builder().operator(QLIdOperator.EQUALS).values(region).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getCloudProviderFilter(String[] cloudProvider) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setCloudProvider(
        CloudBillingIdFilter.builder().operator(QLIdOperator.IN).values(cloudProvider).build());
    return cloudBillingFilter;
  }

  private CloudBillingGroupBy getServiceGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.awsService);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getLinkedAccountsGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.awsLinkedAccount);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getUsageTypeGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.awsUsageType);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getInstanceTypeGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.awsInstanceType);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getRegionGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.region);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getLabelsKeyGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.labelsKey);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getLabelsValueGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.labelsValue);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getTagsKeyGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.tagsKey);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getTagsValueGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.tagsValue);
    return cloudBillingGroupBy;
  }
}
