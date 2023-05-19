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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.graphql.CloudBillingAggregate;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;
import io.harness.ccm.billing.graphql.CloudBillingIdFilter;
import io.harness.ccm.billing.graphql.CloudBillingSortCriteria;
import io.harness.ccm.billing.graphql.CloudEntityGroupBy;
import io.harness.ccm.billing.graphql.CloudSortType;
import io.harness.ccm.billing.preaggregated.PreAggregateBillingEntityDataPoint;
import io.harness.ccm.billing.preaggregated.PreAggregateBillingEntityStatsDTO;
import io.harness.ccm.billing.preaggregated.PreAggregateBillingServiceImpl;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
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
public class CloudEntityStatsDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock CloudBillingHelper cloudBillingHelper;
  @Mock PreAggregateBillingServiceImpl preAggregateBillingService;
  @Mock CeAccountExpirationChecker accountChecker;
  @InjectMocks CloudEntityStatsDataFetcher cloudEntityStatsDataFetcher;

  private static final String UN_BLENDED_COST = "unblendedCost";
  private static final String BLENDED_COST = "blendedCost";
  private static final String COST = "cost";
  private static final String DISCOUNT = "discount";
  private static final String SERVICE_NAME = "service";
  private static final String CLOUD_PROVIDER = "AWS";
  private static final String LABELS_KEY = "labelKey";
  private static final String LABELS_VALUE = "labelValue";
  private static final String TAGS_KEY = "tagKey";
  private static final String TAGS_VALUE = "tagValue";
  private static final Double UN_BLENDED_COST_VALUE = 2.0;
  private static final Double BLENDED_COST_VALUE = 1.0;

  private List<CloudBillingAggregate> cloudBillingAggregates = new ArrayList<>();
  private List<CloudBillingFilter> filters = new ArrayList<>();
  private List<CloudBillingGroupBy> groupBy = new ArrayList<>();
  private List<CloudBillingSortCriteria> sort = new ArrayList<>();

  @Before
  public void setup() {
    doCallRealMethod().when(cloudBillingHelper).getAggregationMapper(anyBoolean(), anyBoolean());
    doCallRealMethod().when(cloudBillingHelper).getGroupByMapper(anyBoolean(), anyBoolean());
    doCallRealMethod().when(cloudBillingHelper).getFiltersMapper(anyBoolean(), anyBoolean());

    cloudBillingAggregates.add(getBillingAggregate(BLENDED_COST));
    cloudBillingAggregates.add(getBillingAggregate(UN_BLENDED_COST));
    filters.addAll(Arrays.asList(getCloudProviderFilter(new String[] {CLOUD_PROVIDER})));

    groupBy.addAll(Arrays.asList(getServiceGroupBy()));
    sort.addAll(Arrays.asList(getAscBlended(), getAscTime(), getDescUnBlended()));

    when(preAggregateBillingService.getPreAggregateBillingEntityStats(
             anyString(), anyList(), anyList(), anyList(), anyList(), any(), anyList(), any()))
        .thenReturn(PreAggregateBillingEntityStatsDTO.builder()
                        .stats(Arrays.asList(PreAggregateBillingEntityDataPoint.builder()
                                                 .awsService(SERVICE_NAME)
                                                 .awsBlendedCost(BLENDED_COST_VALUE)
                                                 .awsUnblendedCost(UN_BLENDED_COST_VALUE)
                                                 .build()))
                        .build());
    when(cloudBillingHelper.getCloudProviderTableName(anyString())).thenReturn("CLOUD_PROVIDER_TABLE_NAME");
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shoudFetch() {
    doCallRealMethod().when(cloudBillingHelper).getCloudProvider(anyList());
    PreAggregateBillingEntityStatsDTO data = (PreAggregateBillingEntityStatsDTO) cloudEntityStatsDataFetcher.fetch(
        ACCOUNT1_ID, cloudBillingAggregates, filters, groupBy, sort, 5, 0);
    assertThat(data.getStats()).isNotNull();
    assertThat(data.getStats().get(0).getAwsService()).isEqualTo(SERVICE_NAME);
    assertThat(data.getStats().get(0).getAwsBlendedCost()).isEqualTo(BLENDED_COST_VALUE);
    assertThat(data.getStats().get(0).getAwsUnblendedCost()).isEqualTo(UN_BLENDED_COST_VALUE);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldFetchWithLabels() {
    doCallRealMethod().when(cloudBillingHelper).fetchIfRawTableQueryRequired(anyList(), anyList());
    doCallRealMethod().when(cloudBillingHelper).getCloudProvider(anyList());
    doCallRealMethod().when(cloudBillingHelper).getLeftJoin(anyString());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderFilter(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderGroupBy(anyList());
    when(preAggregateBillingService.getPreAggregateBillingEntityStats(
             anyString(), anyList(), anyList(), anyList(), anyList(), any(), anyList(), any()))
        .thenReturn(null);

    PreAggregateBillingEntityStatsDTO data =
        (PreAggregateBillingEntityStatsDTO) cloudEntityStatsDataFetcher.fetch(ACCOUNT1_ID, cloudBillingAggregates,
            Arrays.asList(getLablesKeyFilter(new String[] {LABELS_KEY}),
                getLablesValueFilter(new String[] {LABELS_VALUE}), getCloudProviderFilter(new String[] {"GCP"})),
            Collections.emptyList(), null, 5, 0);
    assertThat(data).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldFetchWithLabelsAggregateDiscount() {
    doCallRealMethod().when(cloudBillingHelper).fetchIfRawTableQueryRequired(anyList(), anyList());
    doCallRealMethod().when(cloudBillingHelper).getCloudProvider(anyList());
    doCallRealMethod().when(cloudBillingHelper).getLeftJoin(anyString());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderFilter(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderGroupBy(anyList());
    doCallRealMethod().when(cloudBillingHelper).fetchIfDiscountsAggregationPresent(anyList());
    List<CloudBillingAggregate> aggregations = new ArrayList<>();
    aggregations.add(getBillingAggregate(DISCOUNT));
    aggregations.add(getBillingAggregate(COST));
    when(preAggregateBillingService.getPreAggregateBillingEntityStats(
             anyString(), anyList(), anyList(), anyList(), anyList(), any(), anyList(), any()))
        .thenReturn(null);

    PreAggregateBillingEntityStatsDTO data =
        (PreAggregateBillingEntityStatsDTO) cloudEntityStatsDataFetcher.fetch(ACCOUNT1_ID, aggregations,
            Arrays.asList(getLablesKeyFilter(new String[] {LABELS_KEY}),
                getLablesValueFilter(new String[] {LABELS_VALUE}), getCloudProviderFilter(new String[] {"GCP"})),
            Collections.emptyList(), null, 5, 0);
    assertThat(data).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldFetchWithTags() {
    doCallRealMethod().when(cloudBillingHelper).fetchIfRawTableQueryRequired(anyList(), anyList());
    doCallRealMethod().when(cloudBillingHelper).getLeftJoin(anyString());
    doCallRealMethod().when(cloudBillingHelper).getCloudProvider(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderFilter(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderGroupBy(anyList());
    when(preAggregateBillingService.getPreAggregateBillingEntityStats(
             anyString(), anyList(), anyList(), anyList(), anyList(), any(), anyList(), any()))
        .thenReturn(null);

    PreAggregateBillingEntityStatsDTO data =
        (PreAggregateBillingEntityStatsDTO) cloudEntityStatsDataFetcher.fetch(ACCOUNT1_ID, cloudBillingAggregates,
            Arrays.asList(getLablesKeyFilter(new String[] {TAGS_KEY}), getLablesValueFilter(new String[] {TAGS_VALUE}),
                getCloudProviderFilter(new String[] {CLOUD_PROVIDER})),
            Collections.emptyList(), null, 5, 0);
    assertThat(data).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void postFetch() {
    QLData postFetchData =
        cloudEntityStatsDataFetcher.postFetch(ACCOUNT1_ID, groupBy, cloudBillingAggregates, sort, null, 5, true);
    assertThat(postFetchData).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getEntityType() {
    String entityType = cloudEntityStatsDataFetcher.getEntityType();
    assertThat(entityType).isNull();
  }

  private CloudBillingAggregate getBillingAggregate(String columnName) {
    return CloudBillingAggregate.builder().operationType(QLCCMAggregateOperation.SUM).columnName(columnName).build();
  }

  private CloudBillingSortCriteria getAscBlended() {
    return CloudBillingSortCriteria.builder()
        .sortOrder(QLSortOrder.ASCENDING)
        .sortType(CloudSortType.awsBlendedCost)
        .build();
  }

  private CloudBillingSortCriteria getDescUnBlended() {
    return CloudBillingSortCriteria.builder()
        .sortOrder(QLSortOrder.DESCENDING)
        .sortType(CloudSortType.awsUnblendedCost)
        .build();
  }

  private CloudBillingSortCriteria getAscTime() {
    return CloudBillingSortCriteria.builder().sortOrder(QLSortOrder.ASCENDING).sortType(CloudSortType.Time).build();
  }

  private CloudBillingGroupBy getServiceGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.awsService);
    return cloudBillingGroupBy;
  }

  private CloudBillingFilter getCloudProviderFilter(String[] cloudProvider) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setCloudProvider(
        CloudBillingIdFilter.builder().operator(QLIdOperator.EQUALS).values(cloudProvider).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getLablesKeyFilter(String[] labelsKey) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setLabelsKey(CloudBillingIdFilter.builder().operator(QLIdOperator.IN).values(labelsKey).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getLablesValueFilter(String[] labelsValue) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setLabelsValue(
        CloudBillingIdFilter.builder().operator(QLIdOperator.IN).values(labelsValue).build());
    return cloudBillingFilter;
  }
}
