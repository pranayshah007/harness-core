/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.budgets.service.impl;

import static io.harness.ccm.budget.AlertThresholdBase.ACTUAL_COST;
import static io.harness.ccm.budget.AlertThresholdBase.FORECASTED_COST;
import static io.harness.ccm.commons.constants.Constants.HARNESS_NAME;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;
import static org.apache.commons.text.StrSubstitutor.replace;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.mail.CEMailNotificationService;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.batch.processing.slackNotification.CESlackNotificationService;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.entities.BudgetAlertsData;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.communication.CESlackWebhookService;
import io.harness.ccm.communication.entities.CESlackWebhook;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.beans.notification.SlackNotificationConfiguration;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.graphql.datafetcher.budget.BudgetTimescaleQueryHelper;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class BudgetAlertsServiceImpl {
  @Autowired private TimeScaleDBService timeScaleDBService;
  @Autowired private CEMailNotificationService emailNotificationService;
  @Autowired private CESlackNotificationService slackNotificationService;
  @Autowired private BudgetTimescaleQueryHelper budgetTimescaleQueryHelper;
  @Autowired private BudgetDao budgetDao;
  @Autowired private CESlackWebhookService ceSlackWebhookService;
  @Autowired private BatchMainConfig mainConfiguration;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private AccountShardService accountShardService;
  @Autowired private CloudBillingHelper cloudBillingHelper;

  private static final String BUDGET_MAIL_ERROR = "Budget alert email couldn't be sent";
  private static final String NG_PATH_CONST = "ng/";
  private static final String BUDGET_DETAILS_URL_FORMAT = "/account/%s/continuous-efficiency/budget/%s";
  private static final String BUDGET_DETAILS_URL_FORMAT_NG = "/account/%s/ce/budget/%s/%s";
  private static final String ACTUAL_COST_BUDGET = "cost";
  private static final String FORECASTED_COST_BUDGET = "forecasted cost";

  public void sendBudgetAlerts() {
    List<String> accountIds = accountShardService.getCeEnabledAccountIds();
    accountIds.forEach(accountId -> {
      List<Budget> budgets = budgetDao.list(accountId);
      budgets.forEach(budget -> {
        updateCGBudget(budget);
        try {
          checkAndSendAlerts(budget);
        } catch (Exception e) {
          log.error("Can't send alert for budget : {}, Exception: ", budget.getUuid(), e);
        }
      });
    });
  }

  private void checkAndSendAlerts(Budget budget) {
    checkNotNull(budget.getAlertThresholds());
    checkNotNull(budget.getAccountId());

    List<String> emailAddresses =
        Lists.newArrayList(Optional.ofNullable(budget.getEmailAddresses()).orElse(new String[0]));

    List<String> userGroupIds = Arrays.asList(Optional.ofNullable(budget.getUserGroupIds()).orElse(new String[0]));
    emailAddresses.addAll(getEmailsForUserGroup(budget.getAccountId(), userGroupIds));
    CESlackWebhook slackWebhook = ceSlackWebhookService.getByAccountId(budget.getAccountId());

    // For sending alerts based on actual cost
    AlertThreshold[] alertsBasedOnActualCost =
        BudgetUtils.getSortedAlertThresholds(ACTUAL_COST, budget.getAlertThresholds());
    double actualCost = budget.getActualCost();
    checkAlertThresholdsAndSendAlerts(budget, alertsBasedOnActualCost, slackWebhook, emailAddresses, actualCost);

    // For sending alerts based on forecast cost
    AlertThreshold[] alertsBasedOnForecastCost =
        BudgetUtils.getSortedAlertThresholds(FORECASTED_COST, budget.getAlertThresholds());
    double forecastCost = budget.getForecastCost();
    checkAlertThresholdsAndSendAlerts(budget, alertsBasedOnForecastCost, slackWebhook, emailAddresses, forecastCost);
  }

  private void checkAlertThresholdsAndSendAlerts(Budget budget, AlertThreshold[] alertThresholds,
      CESlackWebhook slackWebhook, List<String> emailAddresses, double cost) {
    for (AlertThreshold alertThreshold : alertThresholds) {
      List<String> userGroupIds =
          Arrays.asList(Optional.ofNullable(alertThreshold.getUserGroupIds()).orElse(new String[0]));
      emailAddresses.addAll(getEmailsForUserGroup(budget.getAccountId(), userGroupIds));
      if (alertThreshold.getEmailAddresses() != null && alertThreshold.getEmailAddresses().length > 0) {
        emailAddresses.addAll(Arrays.asList(alertThreshold.getEmailAddresses()));
      }

      if (slackWebhook == null && isEmpty(emailAddresses)) {
        log.warn("The budget with id={} has no associated communication channels.", budget.getUuid());
        return;
      }

      BudgetAlertsData data = BudgetAlertsData.builder()
                                  .accountId(budget.getAccountId())
                                  .actualCost(cost)
                                  .budgetedCost(budget.getBudgetAmount())
                                  .budgetId(budget.getUuid())
                                  .alertThreshold(alertThreshold.getPercentage())
                                  .alertBasedOn(alertThreshold.getBasedOn())
                                  .time(System.currentTimeMillis())
                                  .build();

      if (BudgetUtils.isAlertSentInCurrentPeriod(
              budgetTimescaleQueryHelper.getLastAlertTimestamp(data, budget.getAccountId()), budget.getStartTime())) {
        break;
      }
      String costType = ACTUAL_COST_BUDGET;
      try {
        if (alertThreshold.getBasedOn() == FORECASTED_COST) {
          costType = FORECASTED_COST_BUDGET;
        }
        log.info("{} has been spent under the budget with id={} ", cost, budget.getUuid());
      } catch (Exception e) {
        log.error(e.getMessage());
        break;
      }

      if (exceedsThreshold(cost, getThresholdAmount(budget, alertThreshold))) {
        try {
          sendBudgetAlertViaSlack(budget, alertThreshold, slackWebhook);
        } catch (Exception e) {
          log.error("Notification via slack not send : ", e);
        }
        sendBudgetAlertMail(budget.getAccountId(), emailAddresses, budget.getUuid(), budget.getName(), alertThreshold,
            cost, costType, budget.isNgBudget());
        // insert in timescale table
        budgetTimescaleQueryHelper.insertAlertEntryInTable(data, budget.getAccountId());
        break;
      }
    }
  }

  private void sendBudgetAlertViaSlack(Budget budget, AlertThreshold alertThreshold, CESlackWebhook slackWebhook) {
    if ((slackWebhook == null || !budget.isNotifyOnSlack()) && alertThreshold.getSlackWebhooks() == null) {
      return;
    }
    List<String> slackWebhooks =
        Arrays.asList(Optional.ofNullable(alertThreshold.getSlackWebhooks()).orElse(new String[0]));
    if (slackWebhook != null && budget.isNotifyOnSlack()) {
      slackWebhooks.add(slackWebhook.getWebhookUrl());
    }
    slackWebhooks.forEach(webhook -> {
      SlackNotificationConfiguration slackConfig = new SlackNotificationSetting("#ccm-test", webhook);
      String slackMessageTemplate =
          "The cost associated with *${BUDGET_NAME}* has reached a limit of ${THRESHOLD_PERCENTAGE}%.";
      Map<String, String> params =
          ImmutableMap.<String, String>builder()
              .put("THRESHOLD_PERCENTAGE", String.format("%.1f", alertThreshold.getPercentage()))
              .put("BUDGET_NAME", budget.getName())
              .build();
      String slackMessage = replace(slackMessageTemplate, params);
      slackNotificationService.sendMessage(
          slackConfig, stripToEmpty(slackConfig.getName()), HARNESS_NAME, slackMessage, budget.getAccountId());
    });
  }

  private List<String> getEmailsForUserGroup(String accountId, List<String> userGroupIds) {
    List<String> emailAddresses = new ArrayList<>();
    for (String userGroupId : userGroupIds) {
      UserGroup userGroup = cloudToHarnessMappingService.getUserGroup(accountId, userGroupId, true);
      if (userGroup != null && userGroup.getMemberIds() != null) {
        emailAddresses.addAll(userGroup.getMemberIds()
                                  .stream()
                                  .map(memberId -> {
                                    User user = cloudToHarnessMappingService.getUser(memberId);
                                    return user.getEmail();
                                  })
                                  .collect(Collectors.toList()));
      }
    }
    return emailAddresses;
  }

  private void sendBudgetAlertMail(String accountId, List<String> emailAddresses, String budgetId, String budgetName,
      AlertThreshold alertThreshold, double currentCost, String costType, boolean isNgBudget) {
    List<String> uniqueEmailAddresses = new ArrayList<>(new HashSet<>(emailAddresses));

    try {
      String budgetUrl = buildAbsoluteUrl(accountId, budgetId, budgetName, isNgBudget);

      Map<String, String> templateModel = new HashMap<>();
      templateModel.put("url", budgetUrl);
      templateModel.put("BUDGET_NAME", budgetName);
      templateModel.put("THRESHOLD_PERCENTAGE", String.format("%.1f", alertThreshold.getPercentage()));
      templateModel.put("CURRENT_COST", String.format("%.2f", currentCost));
      templateModel.put("COST_TYPE", costType);

      uniqueEmailAddresses.forEach(emailAddress -> {
        templateModel.put("name", emailAddress.substring(0, emailAddress.lastIndexOf('@')));
        EmailData emailData = EmailData.builder()
                                  .to(singletonList(emailAddress))
                                  .templateName("ce_budget_alert")
                                  .templateModel(ImmutableMap.copyOf(templateModel))
                                  .accountId(accountId)
                                  .build();
        emailData.setCc(emptyList());
        emailData.setRetries(0);
        emailNotificationService.send(emailData);
      });
    } catch (URISyntaxException e) {
      log.error(BUDGET_MAIL_ERROR, e);
    }
  }

  private String buildAbsoluteUrl(String accountId, String budgetId, String budgetName, boolean isNgBudget)
      throws URISyntaxException {
    String baseUrl = mainConfiguration.getBaseUrl();
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    if (isNgBudget) {
      uriBuilder.setPath(NG_PATH_CONST);
      uriBuilder.setFragment(format(BUDGET_DETAILS_URL_FORMAT_NG, accountId, budgetId, budgetName));
    } else {
      uriBuilder.setFragment(format(BUDGET_DETAILS_URL_FORMAT, accountId, budgetId));
    }
    return uriBuilder.toString();
  }

  private boolean exceedsThreshold(double currentAmount, double thresholdAmount) {
    return currentAmount >= thresholdAmount;
  }

  private double getThresholdAmount(Budget budget, AlertThreshold alertThreshold) {
    switch (alertThreshold.getBasedOn()) {
      case ACTUAL_COST:
      case FORECASTED_COST:
        return budget.getBudgetAmount() * alertThreshold.getPercentage() / 100;
      default:
        return 0;
    }
  }

  private void updateCGBudget(Budget budget) {
    try {
      log.info("Updating CG budget {}", budget.toString());
      if (budget.getPeriod() == null) {
        budget.setPeriod(BudgetPeriod.MONTHLY);
        budget.setStartTime(BudgetUtils.getStartOfMonth(false));
        budget.setEndTime(BudgetUtils.getEndTimeForBudget(budget.getStartTime(), budget.getPeriod()));
        budget.setGrowthRate(0D);
        budget.setNgBudget(false);
        budgetDao.update(budget.getUuid(), budget);
      }
    } catch (Exception e) {
      log.error("Can't update CG budget : {}, Exception: ", budget.getUuid(), e);
    }
  }
}
