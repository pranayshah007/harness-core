/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.budget;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.budget.BudgetType.SPECIFIED_AMOUNT;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.SANDESH;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.billing.Budget.BudgetBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.features.CeBudgetFeature;
import software.wings.features.api.UsageLimitedFeature;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.billing.BillingDataHelper;
import software.wings.graphql.datafetcher.billing.BillingDataQueryBuilder;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.BillingDataMetaDataFields;
import software.wings.graphql.datafetcher.billing.QLBillingStatsHelper;
import software.wings.graphql.schema.type.aggregation.budget.QLBudgetDataList;
import software.wings.graphql.schema.type.aggregation.budget.QLBudgetTableData;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.name.Named;
import io.vavr.collection.Stream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BudgetServiceImplTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private BudgetDao budgetDao;
  @Mock private BillingDataQueryBuilder billingDataQueryBuilder;
  @Mock private DataFetcherUtils utils;
  @Mock private QLBillingStatsHelper statsHelper;
  @Mock private BillingDataHelper billingDataHelper;
  @Mock private BudgetUtils budgetUtils;
  @Mock CeAccountExpirationChecker accountChecker;
  @Mock @Named(CeBudgetFeature.FEATURE_NAME) private UsageLimitedFeature ceBudgetFeature;
  @InjectMocks BudgetServiceImpl budgetService;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private String accountId = "ACCOUNT_ID";
  private String applicationId1 = "APPLICATION_ID_1";
  private String applicationId2 = "APPLICATION_ID_2";
  private String[] applicationIds = {applicationId1, applicationId2};
  private String environment1 = "ENVIRONMENT_ID_1";
  private String[] environmentIds = {environment1};
  private String[] clusterIds = {"CLUSTER_ID"};
  private String budgetId1 = "BUDGET_ID_1";
  private String budgetId2 = "BUDGET_ID_2";
  private String budgetName = "BUDGET_NAME";
  private String entityName = "ENTITY_NAME";
  private BudgetType budgetType = SPECIFIED_AMOUNT;
  private double budgetAmount = 25000.0;
  private double actualCost = 15000.0;
  final EnvironmentType environmentType = EnvironmentType.PROD.PROD;
  private long createdAt = System.currentTimeMillis();
  private long lastUpdatedAt = System.currentTimeMillis();

  final int[] count = {0};
  final double[] doubleVal = {0};

  final long currentTime = System.currentTimeMillis();
  final long[] calendar = {currentTime};
  private AlertThreshold alertThreshold;
  private Budget budget1;
  private Budget budget2;

  @Mock BillingDataQueryMetadata queryData;
  @Mock Connection connection;
  @Mock Statement statement;
  @Mock ResultSet resultSet;

  @Before
  public void setUp() throws SQLException {
    alertThreshold = AlertThreshold.builder().percentage(0.5).basedOn(AlertThresholdBase.ACTUAL_COST).build();
    budget1 = Budget.builder()
                  .uuid(budgetId1)
                  .accountId(accountId)
                  .name("test_budget_1")
                  .scope(ApplicationBudgetScope.builder()
                             .applicationIds(applicationIds)
                             .environmentType(EnvironmentType.ALL)
                             .build())
                  .type(SPECIFIED_AMOUNT)
                  .budgetAmount(100.0)
                  .actualCost(50.0)
                  .alertThresholds(new AlertThreshold[] {alertThreshold})
                  .build();
    budget2 = Budget.builder()
                  .uuid(budgetId2)
                  .accountId(accountId)
                  .name("test_budget_2")
                  .scope(ApplicationBudgetScope.builder()
                             .applicationIds(applicationIds)
                             .environmentType(EnvironmentType.ALL)
                             .build())
                  .type(SPECIFIED_AMOUNT)
                  .budgetAmount(100.0)
                  .actualCost(50.0)
                  .alertThresholds(new AlertThreshold[] {alertThreshold})
                  .build();
    when(billingDataQueryBuilder.formBudgetInsightQuery(any(), any(), any(), any(), any())).thenReturn(queryData);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(any())).thenReturn(resultSet);
    when(ceBudgetFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(100);
    when(budgetDao.list(accountId)).thenReturn(Stream.of(budget1, budget2).collect(Collectors.toList()));
    when(budgetDao.list(accountId, budget1.getName())).thenReturn(Collections.singletonList(budget1));
    when(budgetDao.list(accountId, budget2.getName())).thenReturn(Collections.singletonList(budget2));
    when(budgetDao.get(budget1.getUuid())).thenReturn(budget1);
    when(budgetDao.get(budget2.getUuid())).thenReturn(budget1);
    doNothing().when(accountChecker).checkIsCeEnabled(any());
    when(statsHelper.validateIds(any(), any(), any())).thenReturn(true);
    resetValues();
    mockResultSet();
  }

  private Budget mockBudget(String scope) {
    BudgetBuilder budgetBuilder =
        Budget.builder()
            .uuid(budgetId1)
            .accountId(accountId)
            .name(budgetName)
            .createdAt(createdAt)
            .lastUpdatedAt(lastUpdatedAt)
            .alertThresholds(new AlertThreshold[] {AlertThreshold.builder()
                                                       .percentage(0.5)
                                                       .alertsSent(1)
                                                       .crossedAt(currentTime)
                                                       .basedOn(AlertThresholdBase.ACTUAL_COST)
                                                       .build()})
            .type(budgetType)
            .budgetAmount(budgetAmount)
            .actualCost(actualCost);
    if (scope.equals("CLUSTER")) {
      budgetBuilder.scope(ClusterBudgetScope.builder().clusterIds(clusterIds).build());
    } else {
      budgetBuilder.scope(
          ApplicationBudgetScope.builder().applicationIds(applicationIds).environmentType(environmentType).build());
    }
    return budgetBuilder.build();
  }

  private void mockResultSet() throws SQLException {
    when(resultSet.getDouble("COST")).thenAnswer((Answer<Double>) invocation -> 12500.0 + doubleVal[0]++);
    when(resultSet.getTimestamp(BillingDataMetaDataFields.TIME_SERIES.getFieldName(), utils.getDefaultCalendar()))
        .thenAnswer((Answer<Timestamp>) invocation -> {
          calendar[0] = calendar[0] + 3600000;
          return new Timestamp(calendar[0]);
        });
    returnResultSet(5);
  }

  private void resetValues() {
    count[0] = 0;
    doubleVal[0] = 0;
  }

  private void returnResultSet(int limit) throws SQLException {
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] < limit) {
        count[0]++;
        return true;
      }
      return false;
    });
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldCreate() {
    Budget newBudget = Budget.builder()
                           .accountId(budget1.getAccountId())
                           .name("newBudget")
                           .scope(budget1.getScope())
                           .type(budget1.getType())
                           .budgetAmount(budget1.getBudgetAmount())
                           .alertThresholds(budget1.getAlertThresholds())
                           .userGroupIds(budget1.getUserGroupIds())
                           .build();
    budgetService.create(newBudget);
    verify(budgetDao).save(newBudget);
  }
  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    Budget updatedBudget = Budget.builder()
                               .accountId(null)
                               .name(budget1.getName())
                               .scope(budget1.getScope())
                               .type(budget1.getType())
                               .budgetAmount(100.2)
                               .alertThresholds(budget1.getAlertThresholds())
                               .userGroupIds(budget1.getUserGroupIds())
                               .build();
    budgetService.update(budget1.getUuid(), updatedBudget);
    ArgumentCaptor<Budget> argument = ArgumentCaptor.forClass(Budget.class);
    verify(budgetDao).update(eq(budgetId1), argument.capture());
    assertThat(argument.getValue().getBudgetAmount()).isEqualTo(100.2);
  }
  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldRenameBudget() {
    Budget updatedBudget = Budget.builder()
                               .accountId(null)
                               .name("updatedBudget")
                               .scope(budget1.getScope())
                               .type(budget1.getType())
                               .budgetAmount(budget1.getBudgetAmount())
                               .alertThresholds(budget1.getAlertThresholds())
                               .userGroupIds(budget1.getUserGroupIds())
                               .build();
    budgetService.update(budget1.getUuid(), updatedBudget);
    ArgumentCaptor<Budget> argument = ArgumentCaptor.forClass(Budget.class);
    verify(budgetDao).update(eq(budgetId1), argument.capture());
    assertThat(argument.getValue().getName()).isEqualTo("updatedBudget");
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldStopRenameBudgetToExistingBudgetName() {
    Budget updatedBudget = Budget.builder()
                               .accountId(null)
                               .name("test_budget_2")
                               .scope(budget1.getScope())
                               .type(budget1.getType())
                               .budgetAmount(budget1.getBudgetAmount())
                               .alertThresholds(budget1.getAlertThresholds())
                               .userGroupIds(budget1.getUserGroupIds())
                               .build();
    assertThatThrownBy(() -> {
      budgetService.update(budgetId1, updatedBudget);
    }).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldIncAlertCount() {
    budgetService.incAlertCount(budget1, 0);
    ArgumentCaptor<Budget> argument = ArgumentCaptor.forClass(Budget.class);
    verify(budgetDao).update(eq(budgetId1), argument.capture());
    assertThat(argument.getValue().getAlertThresholds()[0].getAlertsSent()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldStopSavingSameBudgetTwice() {
    Budget duplicateBudget = Budget.builder()
                                 .accountId(budget1.getAccountId())
                                 .name(budget1.getName())
                                 .scope(budget1.getScope())
                                 .type(budget1.getType())
                                 .budgetAmount(budget1.getBudgetAmount())
                                 .alertThresholds(budget1.getAlertThresholds())
                                 .userGroupIds(budget1.getUserGroupIds())
                                 .build();
    assertThatThrownBy(() -> { budgetService.create(duplicateBudget); }).isInstanceOf(InvalidRequestException.class);
  }
  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testSetThresholdCrossedTimestamp() {
    ArgumentCaptor<Budget> argument = ArgumentCaptor.forClass(Budget.class);
    long timestamp1 = Instant.now().toEpochMilli();
    budgetService.setThresholdCrossedTimestamp(budget1, 0, timestamp1);
    verify(budgetDao).update(eq(budgetId1), argument.capture());
    assertThat(argument.getValue().getAlertThresholds()[0].getCrossedAt()).isEqualTo(timestamp1);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetBudgetDataForApplicationType() throws SQLException {
    when(budgetDao.get(budgetId1)).thenReturn(mockBudget("APPLICATION"));
    QLBudgetDataList data = budgetService.getBudgetData(budget1);
    verify(timeScaleDBService).getDBConnection();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetActualCost() throws Exception {
    budgetService.getActualCost(budget1);
    verify(timeScaleDBService).getDBConnection();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetForecastCost() {
    budgetService.getForecastCost(budget1);
    verify(budgetUtils).getForecastCost(eq(budget1));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldGetBudgetDetails() {
    when(statsHelper.getEntityName(any(), any(), anyString())).thenReturn(entityName);
    QLBudgetTableData budgetDetails = budgetService.getBudgetDetails(mockBudget("APPLICATION"));
    assertThat(budgetDetails.getName()).isEqualTo(budgetName);
    assertThat(budgetDetails.getId()).isEqualTo(budgetId1);
    assertThat(budgetDetails.getType()).isEqualTo(SPECIFIED_AMOUNT.toString());
    assertThat(budgetDetails.getActualAmount()).isEqualTo(actualCost);
    assertThat(budgetDetails.getBudgetedAmount()).isEqualTo(budgetAmount);
    assertThat(budgetDetails.getScopeType()).isEqualTo("APPLICATION");
    assertThat(budgetDetails.getAppliesTo()[0]).isEqualTo(entityName);
    assertThat(budgetDetails.getAppliesTo()[1]).isEqualTo(entityName);

    // when budget scope is cluster
    budgetDetails = budgetService.getBudgetDetails(mockBudget("CLUSTER"));
    assertThat(budgetDetails.getName()).isEqualTo(budgetName);
    assertThat(budgetDetails.getId()).isEqualTo(budgetId1);
    assertThat(budgetDetails.getType()).isEqualTo(SPECIFIED_AMOUNT.toString());
    assertThat(budgetDetails.getActualAmount()).isEqualTo(actualCost);
    assertThat(budgetDetails.getBudgetedAmount()).isEqualTo(budgetAmount);
    assertThat(budgetDetails.getScopeType()).isEqualTo("CLUSTER");
    assertThat(budgetDetails.getAppliesTo()[0]).isEqualTo(entityName);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldCloneBudget() {
    when(budgetDao.get(budgetId1, accountId)).thenReturn(budget1);
    when(budgetDao.save((Budget) any())).thenReturn(budgetId1);
    String cloneBudgetId = budgetService.clone(budgetId1, "CLONE", accountId);
    assertThat(cloneBudgetId).isNotNull();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldNotCloneBudget() {
    when(budgetDao.get(budgetId1, accountId)).thenReturn(budget1);
    when(budgetDao.save((Budget) any())).thenReturn(budgetId1);
    assertThatThrownBy(() -> budgetService.clone(budgetId1, "undefined", accountId))
        .isInstanceOf(InvalidRequestException.class);
  }
}
