/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.budgets.service.impl;

import static io.harness.ccm.budget.BudgetType.SPECIFIED_AMOUNT;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.BillingDataPipelineConfig;
import io.harness.batch.processing.mail.CEMailNotificationService;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.batch.processing.slackNotification.CESlackNotificationService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.AlertThresholdBase;
import io.harness.ccm.budget.ApplicationBudgetScope;
import io.harness.ccm.budget.EnvironmentType;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.communication.CESlackWebhookService;
import io.harness.ccm.communication.entities.CESlackWebhook;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.graphql.datafetcher.budget.BudgetTimescaleQueryHelper;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BudgetAlertsServiceImplTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private CEMailNotificationService emailNotificationService;
  @Mock private CESlackNotificationService slackNotificationService;
  @Mock private BudgetTimescaleQueryHelper budgetTimescaleQueryHelper;
  @Mock private CESlackWebhookService ceSlackWebhookService;
  @Mock private BatchMainConfig mainConfiguration;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Mock private AccountShardService accountShardService;
  @Mock private CloudBillingHelper cloudBillingHelper;
  @Mock private BudgetDao budgetDao;
  @InjectMocks private BudgetAlertsServiceImpl budgetAlertsService;

  @Mock Statement statement;
  @Mock ResultSet resultSet;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private String BUDGET_ID = "BUDGET_ID";
  private String APPLICATION_ID_1 = "APPLICATION_ID_1";
  private String APPLICATION_ID_2 = "APPLICATION_ID_2";
  private AlertThreshold alertThreshold;
  private String[] userGroupIds = {"USER_GROUP_ID"};
  private String MEMBER_ID = "MEMBER_ID";
  private String BASE_URL = "BASE_URL";
  private String WEBHOOK_URL = "WEBHOOK_URL";
  private UserGroup userGroup;
  private User user;
  private Budget budget;
  private CESlackWebhook ceSlackWebhook;

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(emailNotificationService.send(any())).thenReturn(true);
    when(accountShardService.getCeEnabledAccountIds()).thenReturn(Arrays.asList(ACCOUNT_ID));
    alertThreshold = AlertThreshold.builder().percentage(0.5).basedOn(AlertThresholdBase.ACTUAL_COST).build();
    budget = Budget.builder()
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
                 .userGroupIds(userGroupIds)
                 .build();

    ceSlackWebhook =
        CESlackWebhook.builder().accountId(ACCOUNT_ID).sendCostReport(true).webhookUrl(WEBHOOK_URL).build();
    userGroup = UserGroup.builder().accountId(ACCOUNT_ID).memberIds(Arrays.asList(MEMBER_ID)).build();
    user = User.Builder.anUser().email("user@harness.io").build();

    when(mainConfiguration.getBaseUrl()).thenReturn(BASE_URL);
    when(mainConfiguration.getBillingDataPipelineConfig())
        .thenReturn(BillingDataPipelineConfig.builder().gcpProjectId("projectId").build());
    when(budgetDao.list(ACCOUNT_ID)).thenReturn(Collections.singletonList(budget));
    when(cloudToHarnessMappingService.getUserGroup(ACCOUNT_ID, userGroupIds[0], true)).thenReturn(userGroup);
    when(cloudToHarnessMappingService.getUser(MEMBER_ID)).thenReturn(user);
    when(ceSlackWebhookService.getByAccountId(budget.getAccountId())).thenReturn(ceSlackWebhook);
    when(cloudBillingHelper.getCloudProviderTableName(anyString(), anyString(), anyString()))
        .thenReturn("cloudProviderTable");
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldSendBudgetAlerts() {
    budgetAlertsService.sendBudgetAlerts();
    verify(emailNotificationService).send(any());
  }
}
