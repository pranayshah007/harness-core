/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.msp;

import static io.harness.ccm.commons.entities.CCMField.AWS_ACCOUNT;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_GRID_ENTRY_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_MARKUP_IDENTIFIER;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.graphql.core.msp.intf.ManagedAccountDataService;
import io.harness.ccm.msp.entities.AmountTrendStats;
import io.harness.ccm.msp.entities.ManagedAccountStats;
import io.harness.ccm.msp.entities.ManagedAccountTimeSeriesData;
import io.harness.ccm.views.dto.DataPoint;
import io.harness.ccm.views.dto.Reference;
import io.harness.ccm.views.dto.TimeSeriesDataPoints;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ManagedAccountDataResourceTest {
  @Mock private ManagedAccountDataService managedAccountDataService;
  @InjectMocks @Inject private ManagedAccountDataResource managedAccountDataResource;

  private static final String MANAGED_ACCOUNT_ID = "account_id";
  private static final String MSP_ACCOUNT_ID = "msp_account_id";
  private static final String AWS_ACCOUNT_ENTITY = "aws_Account";
  private static final String SEARCH_PARAM = "search_param";
  private static final CCMField ENTITY = AWS_ACCOUNT;
  private static final Integer LIMIT = 100;
  private static final Integer OFFSET = 0;
  private static final long START_TIME = 0;
  private static final long END_TIME = 86400000;
  private static final long DATAPOINT_TIME = 86400000;

  private static final double CURRENT_PERIOD_TOTAL_SPEND = 1000;
  private static final double PREVIOUS_PERIOD_TOTAL_SPEND = 500;
  private static final double TOTAL_SPEND_TREND = 50;
  private static final double CURRENT_PERIOD_TOTAL_MARKUP = 200;
  private static final double PREVIOUS_PERIOD_TOTAL_MARKUP = 100;
  private static final double TOTAL_MARKUP_TREND = 50;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    when(managedAccountDataService.getEntityList(MANAGED_ACCOUNT_ID, ENTITY, SEARCH_PARAM, LIMIT, OFFSET))
        .thenReturn(Collections.singletonList(AWS_ACCOUNT_ENTITY));
    when(managedAccountDataService.getManagedAccountStats(MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID, START_TIME, END_TIME))
        .thenReturn(getDummyManagedAccountStats());
    when(managedAccountDataService.getManagedAccountTimeSeriesData(
             MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID, START_TIME, END_TIME))
        .thenReturn(getDummyManagedAccountTimeSeriesData());
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetEntityList() {
    ResponseDTO<List<String>> response = managedAccountDataResource.getEntityList(
        MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID, ENTITY, SEARCH_PARAM, LIMIT, OFFSET);
    assertThat(response.getData()).isNotNull();
    List<String> values = response.getData();
    assertThat(values.size()).isEqualTo(1);
    assertThat(values.get(0)).isEqualTo(AWS_ACCOUNT_ENTITY);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetTotalMarkupAndSpend() {
    ResponseDTO<ManagedAccountStats> response =
        managedAccountDataResource.getTotalMarkupAndSpend(MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID, START_TIME, END_TIME);
    assertThat(response.getData()).isNotNull();
    ManagedAccountStats stats = response.getData();
    assertThat(stats.getTotalSpendStats().getCurrentPeriod()).isEqualTo(CURRENT_PERIOD_TOTAL_SPEND);
    assertThat(stats.getTotalSpendStats().getPreviousPeriod()).isEqualTo(PREVIOUS_PERIOD_TOTAL_SPEND);
    assertThat(stats.getTotalSpendStats().getTrend()).isEqualTo(TOTAL_SPEND_TREND);
    assertThat(stats.getTotalMarkupStats().getCurrentPeriod()).isEqualTo(CURRENT_PERIOD_TOTAL_MARKUP);
    assertThat(stats.getTotalMarkupStats().getPreviousPeriod()).isEqualTo(PREVIOUS_PERIOD_TOTAL_MARKUP);
    assertThat(stats.getTotalMarkupStats().getTrend()).isEqualTo(TOTAL_MARKUP_TREND);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetManagedAccountTimeSeriesData() {
    ResponseDTO<ManagedAccountTimeSeriesData> response = managedAccountDataResource.getManagedAccountTimeSeriesData(
        MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID, START_TIME, END_TIME);
    assertThat(response.getData()).isNotNull();
    ManagedAccountTimeSeriesData timeSeriesStats = response.getData();
    assertThat(timeSeriesStats.getTotalSpendStats().get(0).getTime()).isEqualTo(DATAPOINT_TIME);
    assertThat(timeSeriesStats.getTotalSpendStats().get(0).getValues().get(0).getValue())
        .isEqualTo(CURRENT_PERIOD_TOTAL_SPEND);
    assertThat(timeSeriesStats.getTotalSpendStats().get(0).getValues().get(0).getKey().getName())
        .isEqualTo(DEFAULT_GRID_ENTRY_NAME);
    assertThat(timeSeriesStats.getTotalMarkupStats().get(0).getTime()).isEqualTo(DATAPOINT_TIME);
    assertThat(timeSeriesStats.getTotalMarkupStats().get(0).getValues().get(0).getValue())
        .isEqualTo(CURRENT_PERIOD_TOTAL_MARKUP);
    assertThat(timeSeriesStats.getTotalMarkupStats().get(0).getValues().get(0).getKey().getName())
        .isEqualTo(DEFAULT_MARKUP_IDENTIFIER);
  }

  private ManagedAccountStats getDummyManagedAccountStats() {
    return ManagedAccountStats.builder()
        .totalMarkupStats(getDummyTotalMarkupStats())
        .totalSpendStats(getDummyTotalSpendStats())
        .build();
  }

  private AmountTrendStats getDummyTotalSpendStats() {
    return AmountTrendStats.builder()
        .trend(TOTAL_SPEND_TREND)
        .currentPeriod(CURRENT_PERIOD_TOTAL_SPEND)
        .previousPeriod(PREVIOUS_PERIOD_TOTAL_SPEND)
        .build();
  }

  private AmountTrendStats getDummyTotalMarkupStats() {
    return AmountTrendStats.builder()
        .trend(TOTAL_MARKUP_TREND)
        .currentPeriod(CURRENT_PERIOD_TOTAL_MARKUP)
        .previousPeriod(PREVIOUS_PERIOD_TOTAL_MARKUP)
        .build();
  }

  private ManagedAccountTimeSeriesData getDummyManagedAccountTimeSeriesData() {
    return ManagedAccountTimeSeriesData.builder()
        .totalSpendStats(Collections.singletonList(
            TimeSeriesDataPoints.builder()
                .time(DATAPOINT_TIME)
                .values(Collections.singletonList(DataPoint.builder()
                                                      .value(CURRENT_PERIOD_TOTAL_SPEND)
                                                      .key(Reference.builder().name(DEFAULT_GRID_ENTRY_NAME).build())
                                                      .build()))
                .build()))
        .totalMarkupStats(Collections.singletonList(
            TimeSeriesDataPoints.builder()
                .time(DATAPOINT_TIME)
                .values(Collections.singletonList(DataPoint.builder()
                                                      .value(CURRENT_PERIOD_TOTAL_MARKUP)
                                                      .key(Reference.builder().name(DEFAULT_MARKUP_IDENTIFIER).build())
                                                      .build()))
                .build()))
        .build();
  }
}
