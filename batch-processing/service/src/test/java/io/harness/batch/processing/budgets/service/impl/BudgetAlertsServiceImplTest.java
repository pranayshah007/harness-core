/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.budgets.service.impl;

import static io.harness.ccm.budget.BudgetType.SPECIFIED_AMOUNT;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.BillingDataPipelineConfig;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.batch.processing.tasklet.util.CurrencyPreferenceHelper;
import io.harness.category.element.UnitTests;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.AlertThresholdBase;
import io.harness.ccm.budget.ApplicationBudgetScope;
import io.harness.ccm.budget.EnvironmentType;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.communication.CESlackWebhookService;
import io.harness.ccm.communication.entities.CESlackWebhook;
import io.harness.ccm.currency.Currency;
import io.harness.notifications.NotificationResourceClient;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.graphql.datafetcher.budget.BudgetTimescaleQueryHelper;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.Lists;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BudgetAlertsServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String BUDGET_ID = "BUDGET_ID";
  private static final String APPLICATION_ID_1 = "APPLICATION_ID_1";
  private static final String APPLICATION_ID_2 = "APPLICATION_ID_2";
  private static final String[] USER_GROUP_IDS = {"USER_GROUP_ID"};
  private static final String MEMBER_ID = "MEMBER_ID";
  private static final String BASE_URL = "BASE_URL";
  private static final String WEBHOOK_URL = "WEBHOOK_URL";
  private static final double ALERT_THRESHOLD_PERCENTAGE = 0.5;

  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private NotificationResourceClient notificationResourceClient;
  @Mock private BudgetTimescaleQueryHelper budgetTimescaleQueryHelper;
  @Mock private CESlackWebhookService ceSlackWebhookService;
  @Mock private BatchMainConfig mainConfiguration;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Mock private AccountShardService accountShardService;
  @Mock private CloudBillingHelper cloudBillingHelper;
  @Mock private BudgetDao budgetDao;
  @Mock private BudgetGroupDao budgetGroupDao;
  @Mock private CurrencyPreferenceHelper currencyPreferenceHelper;
  @Mock private Statement statement;
  @Mock private ResultSet resultSet;
  @InjectMocks private BudgetAlertsServiceImpl budgetAlertsService;

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(notificationResourceClient.sendNotification(any(), any())).thenReturn(null);
    when(accountShardService.getCeEnabledAccountIds()).thenReturn(List.of(ACCOUNT_ID));
    AlertThreshold alertThreshold =
        AlertThreshold.builder().percentage(ALERT_THRESHOLD_PERCENTAGE).basedOn(AlertThresholdBase.ACTUAL_COST).build();
    Budget budget = Budget.builder()
                        .uuid(BUDGET_ID)
                        .accountId(ACCOUNT_ID)
                        .name("test_budget")
                        .scope(ApplicationBudgetScope.builder()
                                   .applicationIds(new String[] {APPLICATION_ID_1, APPLICATION_ID_2})
                                   .environmentType(EnvironmentType.ALL)
                                   .build())
                        .type(SPECIFIED_AMOUNT)
                        .budgetAmount(1000.0)
                        .actualCost(600.0)
                        .forecastCost(800.0)
                        .alertThresholds(new AlertThreshold[] {alertThreshold})
                        .userGroupIds(USER_GROUP_IDS)
                        .build();

    CESlackWebhook ceSlackWebhook =
        CESlackWebhook.builder().accountId(ACCOUNT_ID).sendCostReport(true).webhookUrl(WEBHOOK_URL).build();
    UserGroup userGroup = UserGroup.builder().accountId(ACCOUNT_ID).memberIds(List.of(MEMBER_ID)).build();
    User user = User.Builder.anUser().email("user@harness.io").build();

    when(mainConfiguration.getBaseUrl()).thenReturn(BASE_URL);
    when(mainConfiguration.getBillingDataPipelineConfig())
        .thenReturn(BillingDataPipelineConfig.builder().gcpProjectId("projectId").build());
    when(budgetDao.list(ACCOUNT_ID)).thenReturn(Collections.singletonList(budget));
    when(budgetGroupDao.list(ACCOUNT_ID, Integer.MAX_VALUE, 0)).thenReturn(Lists.newArrayList(new BudgetGroup[0]));
    when(cloudToHarnessMappingService.getUserGroup(ACCOUNT_ID, USER_GROUP_IDS[0], true)).thenReturn(userGroup);
    when(cloudToHarnessMappingService.getUser(MEMBER_ID)).thenReturn(user);
    when(ceSlackWebhookService.getByAccountId(budget.getAccountId())).thenReturn(ceSlackWebhook);
    when(cloudBillingHelper.getCloudProviderTableName(anyString(), anyString(), anyString()))
        .thenReturn("cloudProviderTable");
    when(currencyPreferenceHelper.getDestinationCurrency(anyString())).thenReturn(Currency.USD);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldSendBudgetAlerts() {
    budgetAlertsService.sendBudgetAndBudgetGroupAlerts();
    verify(notificationResourceClient, times(1)).sendNotification(any(), any());
  }
}
