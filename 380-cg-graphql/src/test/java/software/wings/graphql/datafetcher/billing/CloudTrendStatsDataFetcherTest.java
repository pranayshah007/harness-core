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
import static org.mockito.Matchers.anyInt;
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
import io.harness.ccm.billing.preaggregated.PreAggregateBillingServiceImpl;
import io.harness.ccm.billing.preaggregated.PreAggregateBillingTrendStatsDTO;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingStatsInfo;
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
public class CloudTrendStatsDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock PreAggregateBillingServiceImpl preAggregateBillingService;
  @Mock CloudBillingHelper cloudBillingHelper;
  @Mock CeAccountExpirationChecker accountChecker;
  @InjectMocks CloudTrendStatsDataFetcher cloudTrendStatsDataFetcher;

  private static final String UN_BLENDED_COST = "unblendedCost";
  private static final String BLENDED_COST = "awsBlendedCost";
  private static final String DISCOUNTS = "discount";
  private static final String START_TIME = "startTime";
  private static final String CLOUD_PROVIDER = "AWS";
  private static final String CLOUD_PROVIDER_GCP = "GCP";
  private static final String LABELS_KEY = "lablesKey";
  private static final String STATS_LABEL = "statsLabel";
  private static final String STATS_VALUE = "statsValue";
  private static final String STATS_DESCRIPTION = "statsDescription";

  private List<CloudBillingAggregate> cloudBillingAggregates = new ArrayList<>();
  private List<CloudBillingFilter> filters = new ArrayList<>();
  private List<CloudBillingGroupBy> groupBy = new ArrayList<>();
  private List<CloudBillingSortCriteria> sort = new ArrayList<>();

  @Before
  public void setup() {
    doCallRealMethod().when(cloudBillingHelper).getCloudProvider(anyList());
    doCallRealMethod().when(cloudBillingHelper).getAggregationMapper(anyBoolean(), anyBoolean());
    doCallRealMethod().when(cloudBillingHelper).getGroupByMapper(anyBoolean(), anyBoolean());
    doCallRealMethod().when(cloudBillingHelper).getFiltersMapper(anyBoolean(), anyBoolean());
    cloudBillingAggregates.add(getBillingAggregate(QLCCMAggregateOperation.SUM, BLENDED_COST));
    cloudBillingAggregates.add(getBillingAggregate(QLCCMAggregateOperation.SUM, UN_BLENDED_COST));
    cloudBillingAggregates.add(getBillingAggregate(QLCCMAggregateOperation.MIN, START_TIME));
    cloudBillingAggregates.add(getBillingAggregate(QLCCMAggregateOperation.MAX, START_TIME));
    filters.addAll(Arrays.asList(getCloudProviderFilter(new String[] {CLOUD_PROVIDER})));

    when(preAggregateBillingService.getPreAggregateBillingTrendStats(anyList(), anyList(), any(), anyList(), any()))
        .thenReturn(PreAggregateBillingTrendStatsDTO.builder()
                        .blendedCost(QLBillingStatsInfo.builder()
                                         .statsValue(STATS_VALUE)
                                         .statsLabel(STATS_LABEL)
                                         .statsDescription(STATS_DESCRIPTION)
                                         .build())
                        .build());
    when(cloudBillingHelper.getCloudProviderTableName(anyString())).thenReturn("CLOUD_PROVIDER_TABLE_NAME");
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testFetch() {
    PreAggregateBillingTrendStatsDTO stats = (PreAggregateBillingTrendStatsDTO) cloudTrendStatsDataFetcher.fetch(
        ACCOUNT1_ID, cloudBillingAggregates, filters, groupBy, sort);
    assertThat(stats).isNotNull();
    assertThat(stats.getBlendedCost().getStatsValue()).isEqualTo(STATS_VALUE);
    assertThat(stats.getBlendedCost().getStatsLabel()).isEqualTo(STATS_LABEL);
    assertThat(stats.getBlendedCost().getStatsDescription()).isEqualTo(STATS_DESCRIPTION);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void fetchWithLabels() {
    doCallRealMethod().when(cloudBillingHelper).fetchIfRawTableQueryRequired(anyList(), anyList());
    doCallRealMethod().when(cloudBillingHelper).getCloudProvider(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderFilter(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderGroupBy(anyList());
    doCallRealMethod().when(cloudBillingHelper).fetchIfDiscountsAggregationPresent(anyList());
    List<CloudBillingFilter> billingFilters = Arrays.asList(
        getCloudProviderFilter(new String[] {CLOUD_PROVIDER_GCP}), getLabelsKeyFilter(new String[] {LABELS_KEY}));

    when(preAggregateBillingService.getPreAggregateFilterValueStats(
             anyString(), anyList(), anyList(), anyString(), any(), anyInt(), anyInt()))
        .thenReturn(null);
    PreAggregateBillingTrendStatsDTO data = (PreAggregateBillingTrendStatsDTO) cloudTrendStatsDataFetcher.fetch(
        ACCOUNT1_ID, Collections.singletonList(getBillingAggregate(QLCCMAggregateOperation.SUM, DISCOUNTS)),
        billingFilters, null, null);
    assertThat(data).isNotNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testPostFetch() {
    QLData postFetchData =
        cloudTrendStatsDataFetcher.postFetch(ACCOUNT1_ID, groupBy, cloudBillingAggregates, sort, null);
    assertThat(postFetchData).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetEntityType() {
    String entityType = cloudTrendStatsDataFetcher.getEntityType();
    assertThat(entityType).isNull();
  }

  private CloudBillingFilter getCloudProviderFilter(String[] cloudProvider) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setCloudProvider(
        CloudBillingIdFilter.builder().operator(QLIdOperator.IN).values(cloudProvider).build());
    return cloudBillingFilter;
  }

  private CloudBillingAggregate getBillingAggregate(QLCCMAggregateOperation operation, String columnName) {
    return CloudBillingAggregate.builder().operationType(operation).columnName(columnName).build();
  }

  private CloudBillingFilter getLabelsKeyFilter(String[] labelsKey) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setLabelsKey(CloudBillingIdFilter.builder().operator(QLIdOperator.IN).values(labelsKey).build());
    return cloudBillingFilter;
  }
}
