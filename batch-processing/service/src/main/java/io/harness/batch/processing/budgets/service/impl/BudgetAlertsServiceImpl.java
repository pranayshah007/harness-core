/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.budgets.service.impl;

import static io.harness.ccm.budget.AlertThresholdBase.ACTUAL_COST;
import static io.harness.ccm.budget.AlertThresholdBase.FORECASTED_COST;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.joda.time.Months.monthsBetween;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.batch.processing.tasklet.util.CurrencyPreferenceHelper;
import io.harness.ccm.BudgetCommon;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.entities.BudgetAlertsData;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.budgetGroup.dao.BudgetGroupDao;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.currency.Currency;
import io.harness.notification.Team;
import io.harness.notification.dtos.NotificationChannelDTO;
import io.harness.notification.dtos.NotificationChannelDTO.NotificationChannelDTOBuilder;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.notifications.NotificationResourceClient;
import io.harness.rest.RestResponse;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.graphql.datafetcher.budget.BudgetTimescaleQueryHelper;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import retrofit2.Response;

@Service
@Singleton
@Slf4j
public class BudgetAlertsServiceImpl {
  @Autowired private TimeScaleDBService timeScaleDBService;
  @Autowired private NotificationResourceClient notificationResourceClient;
  @Autowired private BudgetTimescaleQueryHelper budgetTimescaleQueryHelper;
  @Autowired private BudgetDao budgetDao;
  @Autowired private BudgetGroupDao budgetGroupDao;
  @Autowired private BatchMainConfig mainConfiguration;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private AccountShardService accountShardService;
  @Autowired private CloudBillingHelper cloudBillingHelper;
  @Autowired private CurrencyPreferenceHelper currencyPreferenceHelper;

  private static final String BUDGET_MAIL_ERROR = "Budget alert email couldn't be sent";
  private static final String NG_PATH_CONST = "ng/";
  private static final String BUDGET_DETAILS_URL_FORMAT = "/account/%s/continuous-efficiency/budget/%s";
  private static final String BUDGET_DETAILS_URL_FORMAT_NG = "/account/%s/ce/budget/%s/%s";
  private static final String PERSPECTIVE_DETAILS_URL_FORMAT =
      "/account/%s/continuous-efficiency/perspective-explorer/%s/%s";
  private static final String PERSPECTIVE_DETAILS_URL_FORMAT_NG = "/account/%s/ce/perspectives/%s/name/%s";
  private static final String ACTUAL_COST_BUDGET = "cost";
  private static final String ACTUAL_SPEND = "Actual Spend";
  private static final String FORECASTED_SPEND = "Forecasted Spend";
  private static final String SUBJECT_ACTUAL_COST_BUDGET = "Spent so far";
  private static final String FORECASTED_COST_BUDGET = "forecasted cost";
  private static final String SUBJECT_FORECASTED_COST_BUDGET = "Forecasted cost";
  private static final String BUDGET = "budget";
  private static final String BUDGET_CAMEL_CASE = "Budget";
  private static final String BUDGET_GROUP = "budget group";
  private static final String BUDGET_GROUP_CAMEL_CASE = "Budget Group";
  private static final String DAY = "day";
  private static final String WEEK = "week";
  private static final String MONTH = "month";
  private static final String QUARTER = "quarter";
  private static final String YEAR = "year";

  public void sendBudgetAndBudgetGroupAlerts() {
    List<String> accountIds = accountShardService.getCeEnabledAccountIds();
    accountIds.forEach(accountId -> {
      List<Budget> budgets = budgetDao.list(accountId);
      // [TODO]: Cache currency symbol for each account
      Currency currency = currencyPreferenceHelper.getDestinationCurrency(accountId);
      budgets.forEach(budget -> {
        updateCGBudget(budget);
        try {
          checkAndSendAlerts(buildBudgetCommon(budget, null), currency);
        } catch (Exception e) {
          log.error("Can't send alert for budget : {}, accountId: {}, Exception: ", budget.getUuid(),
              budget.getAccountId(), e);
        }
      });
      List<BudgetGroup> budgetGroups = budgetGroupDao.list(accountId, Integer.MAX_VALUE, 0);
      budgetGroups.forEach(budgetGroup -> {
        try {
          checkAndSendAlerts(buildBudgetCommon(null, budgetGroup), currency);
        } catch (Exception e) {
          log.error("Can't send alert for budget group : {}, accountId: {}, Exception: ", budgetGroup.getUuid(),
              budgetGroup.getAccountId(), e);
        }
      });
    });
  }

  private BudgetCommon buildBudgetCommon(Budget budget, BudgetGroup group) {
    if (budget != null) {
      return BudgetCommon.builder()
          .alertThresholds(budget.getAlertThresholds())
          .forecastCost(budget.getForecastCost())
          .name(budget.getName())
          .lastMonthCost(budget.getLastMonthCost())
          .budgetAmount(budget.getBudgetAmount())
          .period(budget.getPeriod())
          .startTime(budget.getStartTime())
          .accountId(budget.getAccountId())
          .uuid(budget.getUuid())
          .budgetGroup(false)
          .budgetMonthlyBreakdown(budget.getBudgetMonthlyBreakdown())
          .actualCost(budget.getActualCost())
          .emailAddresses(budget.getEmailAddresses())
          .userGroupIds(budget.getUserGroupIds())
          .isNgBudget(budget.isNgBudget())
          .notifyOnSlack(budget.isNotifyOnSlack())
          .perspectiveName(BudgetUtils.getPerspectiveNameForBudget(budget))
          .perspectiveId(BudgetUtils.getPerspectiveIdForBudget(budget))
          .build();
    }
    return BudgetCommon.builder()
        .alertThresholds(group.getAlertThresholds())
        .forecastCost(group.getForecastCost())
        .name(group.getName())
        .lastMonthCost(group.getLastMonthCost())
        .budgetAmount(group.getBudgetGroupAmount())
        .period(group.getPeriod())
        .startTime(group.getStartTime())
        .accountId(group.getAccountId())
        .uuid(group.getUuid())
        .budgetGroup(true)
        .budgetMonthlyBreakdown(group.getBudgetGroupMonthlyBreakdown())
        .actualCost(group.getActualCost())
        .emailAddresses(null)
        .userGroupIds(null)
        .isNgBudget(true)
        .notifyOnSlack(true)
        .build();
  }

  private void checkAndSendAlerts(BudgetCommon budgetCommon, Currency currency) {
    checkNotNull(budgetCommon.getAlertThresholds());
    checkNotNull(budgetCommon.getAccountId());

    List<String> emailAddresses =
        Lists.newArrayList(Optional.ofNullable(budgetCommon.getEmailAddresses()).orElse(new String[0]));

    List<String> userGroupIds =
        Arrays.asList(Optional.ofNullable(budgetCommon.getUserGroupIds()).orElse(new String[0]));
    emailAddresses.addAll(getEmailsForUserGroup(budgetCommon.getAccountId(), userGroupIds));

    // For sending alerts based on actual cost
    AlertThreshold[] alertsBasedOnActualCost =
        BudgetUtils.getSortedAlertThresholds(ACTUAL_COST, budgetCommon.getAlertThresholds());
    double actualCost = budgetCommon.getActualCost();
    if (budgetCommon.getBudgetMonthlyBreakdown() != null
        && budgetCommon.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
      int month = monthDifferenceStartAndCurrentTime(budgetCommon.getStartTime());
      if (month != -1) {
        actualCost = budgetCommon.getBudgetMonthlyBreakdown().getActualMonthlyCost()[month];
      }
    }
    checkAlertThresholdsAndSendAlerts(budgetCommon, alertsBasedOnActualCost, emailAddresses, actualCost, currency);

    // For sending alerts based on forecast cost
    AlertThreshold[] alertsBasedOnForecastCost =
        BudgetUtils.getSortedAlertThresholds(FORECASTED_COST, budgetCommon.getAlertThresholds());
    double forecastCost = budgetCommon.getForecastCost();
    if (budgetCommon.getBudgetMonthlyBreakdown() != null
        && budgetCommon.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
      int month = monthDifferenceStartAndCurrentTime(budgetCommon.getStartTime());
      if (month != -1) {
        forecastCost = budgetCommon.getBudgetMonthlyBreakdown().getForecastMonthlyCost()[month];
      }
    }
    checkAlertThresholdsAndSendAlerts(budgetCommon, alertsBasedOnForecastCost, emailAddresses, forecastCost, currency);
  }

  private void checkAlertThresholdsAndSendAlerts(BudgetCommon budgetCommon, AlertThreshold[] alertThresholds,
      List<String> emailAddresses, double cost, Currency currency) {
    String budgetOrBudgetGroup = budgetCommon.isBudgetGroup() ? BUDGET_GROUP : BUDGET;
    String budgetOrBudgetGroupCamelCase = budgetCommon.isBudgetGroup() ? BUDGET_GROUP_CAMEL_CASE : BUDGET_CAMEL_CASE;
    for (AlertThreshold alertThreshold : alertThresholds) {
      List<String> userGroupIds =
          Arrays.asList(Optional.ofNullable(alertThreshold.getUserGroupIds()).orElse(new String[0]));
      emailAddresses.addAll(getEmailsForUserGroup(budgetCommon.getAccountId(), userGroupIds));
      if (alertThreshold.getEmailAddresses() != null && alertThreshold.getEmailAddresses().length > 0) {
        emailAddresses.addAll(Arrays.asList(alertThreshold.getEmailAddresses()));
      }

      List<String> slackWebhooks =
          Arrays.asList(Optional.ofNullable(alertThreshold.getSlackWebhooks()).orElse(new String[0]));
      slackWebhooks.addAll(getSlackWebhooksForUserGroup(budgetCommon.getAccountId(), userGroupIds));

      if (isEmpty(slackWebhooks) && isEmpty(emailAddresses)) {
        log.warn("The {} with id={} has no associated communication channels for threshold={}.", budgetOrBudgetGroup,
            budgetCommon.getUuid(), alertThreshold);
        continue;
      }

      BudgetAlertsData data = BudgetAlertsData.builder()
                                  .accountId(budgetCommon.getAccountId())
                                  .actualCost(cost)
                                  .budgetedCost(budgetCommon.getBudgetAmount())
                                  .budgetId(budgetCommon.getUuid())
                                  .alertThreshold(alertThreshold.getPercentage())
                                  .alertBasedOn(alertThreshold.getBasedOn())
                                  .time(System.currentTimeMillis())
                                  .build();

      if (BudgetUtils.isAlertSentInCurrentPeriod(budgetCommon,
              budgetTimescaleQueryHelper.getLastAlertTimestamp(data, budgetCommon.getAccountId()),
              budgetCommon.getStartTime())) {
        break;
      }
      String costType = ACTUAL_COST_BUDGET;
      String subjectCostType = SUBJECT_ACTUAL_COST_BUDGET;
      String costTypeSlackAlert = ACTUAL_SPEND;
      try {
        if (alertThreshold.getBasedOn() == FORECASTED_COST) {
          costType = FORECASTED_COST_BUDGET;
          subjectCostType = SUBJECT_FORECASTED_COST_BUDGET;
          costTypeSlackAlert = FORECASTED_SPEND;
        }
        log.info("{} has been spent under the {} with id={}, accountId={}", cost, budgetOrBudgetGroup,
            budgetCommon.getUuid(), budgetCommon.getAccountId());
      } catch (Exception e) {
        log.error(e.getMessage());
        break;
      }

      if (exceedsThreshold(cost, getThresholdAmount(budgetCommon, alertThreshold))) {
        try {
          sendBudgetAlertViaSlack(budgetCommon, alertThreshold, slackWebhooks, currency, costTypeSlackAlert);
          log.info("slack {} alert sent! for accountId: {}, budgetId: {}", budgetOrBudgetGroup,
              budgetCommon.getAccountId(), budgetCommon.getUuid());
        } catch (Exception e) {
          log.error("Notification via slack not sent for accountId: {}, budgetId: {} : ", budgetCommon.getAccountId(),
              budgetCommon.getUuid(), e);
        }
        sendBudgetAlertMail(budgetCommon.getAccountId(), emailAddresses, budgetCommon.getUuid(), budgetCommon.getName(),
            alertThreshold, cost, costType, budgetCommon.isNgBudget(), subjectCostType,
            getBudgetPeriodForEmailAlert(budgetCommon), currency, budgetOrBudgetGroup, budgetOrBudgetGroupCamelCase);
        // insert in timescale table
        budgetTimescaleQueryHelper.insertAlertEntryInTable(data, budgetCommon.getAccountId());
        break;
      }
    }
  }

  private void sendBudgetAlertViaSlack(BudgetCommon budgetCommon, AlertThreshold alertThreshold,
      List<String> slackWebhooks, Currency currency, String costTypeSlackAlert) throws IOException, URISyntaxException {
    if ((isEmpty(slackWebhooks) || !budgetCommon.isNotifyOnSlack()) && alertThreshold.getSlackWebhooks() == null) {
      return;
    }
    NotificationChannelDTOBuilder slackChannelBuilder = budgetCommon.isBudgetGroup()
        ? getBudgetGroupNotificationChannelDTOBuilder(
            budgetCommon, alertThreshold, slackWebhooks, currency, costTypeSlackAlert)
        : getBudgetNotificationChannelDTOBuilder(
            budgetCommon, alertThreshold, slackWebhooks, currency, costTypeSlackAlert);
    Response<RestResponse<NotificationResult>> response =
        notificationResourceClient.sendNotification(budgetCommon.getAccountId(), slackChannelBuilder.build()).execute();
    if (!response.isSuccessful()) {
      log.error("Failed to send slack notification for accountId: {}, budgetId: {} error: {}",
          budgetCommon.getAccountId(), budgetCommon.getUuid(),
          (response.errorBody() != null) ? response.errorBody().string() : response.code());
    } else {
      String notificationId = response.body() != null ? response.body().getResource().getNotificationId() : null;
      log.info("Slack notification request sent. accountId: {}, budgetId: {}, notificationId: {}",
          budgetCommon.getAccountId(), budgetCommon.getUuid(), notificationId);
    }
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

  private List<String> getSlackWebhooksForUserGroup(String accountId, List<String> userGroupIds) {
    List<String> slackWebhooks = new ArrayList<>();
    for (String userGroupId : userGroupIds) {
      UserGroup userGroup = cloudToHarnessMappingService.getUserGroup(accountId, userGroupId, true);
      if (userGroup != null && userGroup.getNotificationSettings() != null) {
        SlackNotificationSetting slackNotificationSetting = userGroup.getNotificationSettings().getSlackConfig();
        if (!StringUtils.isEmpty(slackNotificationSetting.getOutgoingWebhookUrl())) {
          slackWebhooks.add(slackNotificationSetting.getOutgoingWebhookUrl());
        }
      }
    }
    return slackWebhooks;
  }

  private void sendBudgetAlertMail(String accountId, List<String> emailAddresses, String budgetId, String budgetName,
      AlertThreshold alertThreshold, double currentCost, String costType, boolean isNgBudget, String subjectCostType,
      String period, Currency currency, String budgetOrBudgetGroup, String budgetOrBudgetGroupCamelCase) {
    List<String> uniqueEmailAddresses = new ArrayList<>(new HashSet<>(emailAddresses));

    try {
      String budgetUrl = buildAbsoluteUrl(accountId, budgetId, budgetName, isNgBudget, true);

      Map<String, String> templateModel = new HashMap<>();
      templateModel.put("url", budgetUrl);
      templateModel.put("BUDGET_NAME", budgetName);
      templateModel.put("THRESHOLD_PERCENTAGE", format("%.1f", alertThreshold.getPercentage()));
      templateModel.put("CURRENT_COST", format("%s%s", currency.getUtf8HexSymbol(), format("%.2f", currentCost)));
      templateModel.put("COST_TYPE", costType);
      templateModel.put("SUBJECT_COST_TYPE", subjectCostType);
      templateModel.put("PERIOD", period);
      templateModel.put("BUDGET_OR_BUDGET_GROUP", budgetOrBudgetGroup);
      templateModel.put("BUDGET_OR_BUDGET_GROUP_CAMEL_CASE", budgetOrBudgetGroupCamelCase);

      uniqueEmailAddresses.forEach(emailAddress -> {
        templateModel.put("name", emailAddress.substring(0, emailAddress.lastIndexOf('@')));
        NotificationChannelDTOBuilder emailChannelBuilder = NotificationChannelDTO.builder()
                                                                .accountId(accountId)
                                                                .emailRecipients(singletonList(emailAddress))
                                                                .team(Team.OTHER)
                                                                .templateId("email_ccm_budget_alert")
                                                                .templateData(ImmutableMap.copyOf(templateModel))
                                                                .userGroups(Collections.emptyList());

        try {
          Response<RestResponse<NotificationResult>> response =
              notificationResourceClient.sendNotification(accountId, emailChannelBuilder.build()).execute();
          if (!response.isSuccessful()) {
            log.error("Failed to send email notification for accountId: {}, budgetId: {} error: {}", accountId,
                budgetId, (response.errorBody() != null) ? response.errorBody().string() : response.code());
          } else {
            log.info("email sent successfully for accountId: {}, budgetId: {}", accountId, budgetId);
          }
        } catch (IOException e) {
          log.error(BUDGET_MAIL_ERROR, e);
        }
      });
    } catch (URISyntaxException e) {
      log.error(BUDGET_MAIL_ERROR, e);
    }
  }

  private String buildAbsoluteUrl(String accountId, String id, String name, boolean isNg, boolean isBudgetUrl)
      throws URISyntaxException {
    String baseUrl = mainConfiguration.getBaseUrl();
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    // If name has / in it
    String encodedName = name;
    try {
      encodedName = URLEncoder.encode(name, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      log.info("Exception while encoding the name: {}", e);
    }
    if (isNg) {
      uriBuilder.setPath(NG_PATH_CONST);
      if (isBudgetUrl) {
        uriBuilder.setFragment(format(BUDGET_DETAILS_URL_FORMAT_NG, accountId, id, encodedName));
      } else {
        uriBuilder.setFragment(format(PERSPECTIVE_DETAILS_URL_FORMAT_NG, accountId, id, encodedName));
      }
    } else {
      if (isBudgetUrl) {
        uriBuilder.setFragment(format(BUDGET_DETAILS_URL_FORMAT, accountId, id));
      } else {
        uriBuilder.setFragment(format(PERSPECTIVE_DETAILS_URL_FORMAT, accountId, id, encodedName));
      }
    }
    return uriBuilder.toString();
  }

  private boolean exceedsThreshold(double currentAmount, double thresholdAmount) {
    return currentAmount >= thresholdAmount;
  }

  private double getThresholdAmount(BudgetCommon budget, AlertThreshold alertThreshold) {
    switch (alertThreshold.getBasedOn()) {
      case ACTUAL_COST:
      case FORECASTED_COST:
        if (budget.getBudgetMonthlyBreakdown() != null
            && budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
          int month = monthDifferenceStartAndCurrentTime(budget.getStartTime());
          if (month != -1) {
            return BudgetUtils.getYearlyMonthWiseValues(
                       budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount())[month]
                * alertThreshold.getPercentage() / BudgetUtils.HUNDRED;
          }
        }
        return budget.getBudgetAmount() * alertThreshold.getPercentage() / BudgetUtils.HUNDRED;
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

  private int monthDifferenceStartAndCurrentTime(long startTime) {
    long currentTime = BudgetUtils.getStartOfMonthGivenTime(max(startTime, BudgetUtils.getStartOfCurrentDay()));
    long startTimeUpdated = BudgetUtils.getStartOfMonthGivenTime(startTime);
    int monthDiff = monthsBetween(new DateTime(startTimeUpdated), new DateTime(currentTime)).getMonths();
    if (monthDiff > 11) {
      return -1;
    }
    return monthDiff;
  }

  // We don't have monthly here as one of the period in cases
  // We have placed it in default itself
  private String getBudgetPeriodForEmailAlert(BudgetCommon budget) {
    switch (budget.getPeriod()) {
      case DAILY:
        return DAY;
      case WEEKLY:
        return WEEK;
      case QUARTERLY:
        return QUARTER;
      case YEARLY:
        if (budget.getBudgetMonthlyBreakdown() != null
            && budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
          return MONTH;
        } else {
          return YEAR;
        }
      default:
        return MONTH;
    }
  }

  private NotificationChannelDTOBuilder getBudgetNotificationChannelDTOBuilder(BudgetCommon budgetCommon,
      AlertThreshold alertThreshold, List<String> slackWebhooks, Currency currency, String costTypeSlackAlert)
      throws URISyntaxException {
    String budgetUrl = buildAbsoluteUrl(
        budgetCommon.getAccountId(), budgetCommon.getUuid(), budgetCommon.getName(), budgetCommon.isNgBudget(), true);
    String perspectiveUrl = buildAbsoluteUrl(budgetCommon.getAccountId(), budgetCommon.getPerspectiveId(),
        budgetCommon.getPerspectiveName(), budgetCommon.isNgBudget(), false);
    String isOrHas = "is";
    String approachingOrExceeded = "approaching";
    if (alertThreshold.getPercentage() >= 100.0) {
      isOrHas = "has";
      approachingOrExceeded = "exceeded";
    }
    Map<String, String> templateData =
        ImmutableMap.<String, String>builder()
            .put("PERSPECTIVE_NAME", budgetCommon.getPerspectiveName())
            .put("IS_OR_HAS", isOrHas)
            .put("APPROACHING_OR_EXCEEDED", approachingOrExceeded)
            .put("PERIOD", budgetCommon.getPeriod().name().toLowerCase())
            .put("BUDGET_NAME", budgetCommon.getName())
            .put("BUDGET_AMOUNT", format("%s%s", currency.getSymbol(), format("%.2f", budgetCommon.getBudgetAmount())))
            .put("ACTUAL_AMOUNT", format("%s%s", currency.getSymbol(), format("%.2f", budgetCommon.getActualCost())))
            .put(
                "FORECASTED_COST", format("%s%s", currency.getSymbol(), format("%.2f", budgetCommon.getForecastCost())))
            .put("THRESHOLD_PERCENTAGE", format("%.1f", alertThreshold.getPercentage()))
            .put("ACTUAL_SPEND_OR_FORECASTED_SPEND", costTypeSlackAlert)
            .put("BUDGET_URL", budgetUrl)
            .put("PERSPECTIVE_URL", perspectiveUrl)
            .build();
    NotificationChannelDTOBuilder slackChannelBuilder = NotificationChannelDTO.builder()
                                                            .accountId(budgetCommon.getAccountId())
                                                            .templateData(templateData)
                                                            .webhookUrls(slackWebhooks)
                                                            .team(Team.OTHER)
                                                            .templateId("slack_ccm_budget_alert")
                                                            .userGroups(Collections.emptyList());
    return slackChannelBuilder;
  }

  private NotificationChannelDTOBuilder getBudgetGroupNotificationChannelDTOBuilder(BudgetCommon budgetCommon,
      AlertThreshold alertThreshold, List<String> slackWebhooks, Currency currency, String costTypeSlackAlert)
      throws URISyntaxException {
    String budgetGroupUrl = buildAbsoluteUrl(
        budgetCommon.getAccountId(), budgetCommon.getUuid(), budgetCommon.getName(), budgetCommon.isNgBudget(), true);
    String isOrHas = "is";
    String approachingOrExceeded = "approaching";
    if (alertThreshold.getPercentage() >= 100.0) {
      isOrHas = "has";
      approachingOrExceeded = "exceeded";
    }
    Map<String, String> templateData =
        ImmutableMap.<String, String>builder()
            .put("BUDGET_GROUP_NAME", budgetCommon.getName())
            .put("PERIOD", budgetCommon.getPeriod().name().toLowerCase())
            .put("IS_OR_HAS", isOrHas)
            .put("APPROACHING_OR_EXCEEDED", approachingOrExceeded)
            .put("BUDGET_GROUP_AMOUNT",
                format("%s%s", currency.getSymbol(), format("%.2f", budgetCommon.getBudgetAmount())))
            .put("ACTUAL_AMOUNT", format("%s%s", currency.getSymbol(), format("%.2f", budgetCommon.getActualCost())))
            .put(
                "FORECASTED_COST", format("%s%s", currency.getSymbol(), format("%.2f", budgetCommon.getForecastCost())))
            .put("THRESHOLD_PERCENTAGE", format("%.1f", alertThreshold.getPercentage()))
            .put("ACTUAL_SPEND_OR_FORECASTED_SPEND", costTypeSlackAlert)
            .put("BUDGET_GROUP_URL", budgetGroupUrl)
            .build();
    NotificationChannelDTOBuilder slackChannelBuilder = NotificationChannelDTO.builder()
                                                            .accountId(budgetCommon.getAccountId())
                                                            .templateData(templateData)
                                                            .webhookUrls(slackWebhooks)
                                                            .team(Team.OTHER)
                                                            .templateId("slack_ccm_budget_group_alert")
                                                            .userGroups(Collections.emptyList());
    return slackChannelBuilder;
  }
}
