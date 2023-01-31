/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notifications;

import static io.harness.beans.FeatureName.SPG_ENABLE_NOTIFICATION_RULES;

import io.harness.ff.FeatureFlagService;
import io.harness.notifications.conditions.CVFilterMatcher;
import io.harness.notifications.conditions.ManualInterventionFilterMatcher;

import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertFilter;
import software.wings.beans.alert.AlertNotificationRule;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AlertNotificationRuleCheckerImpl implements AlertNotificationRuleChecker {
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public boolean doesAlertSatisfyRule(Alert alert, AlertNotificationRule rule) {
    AlertFilter filter = rule.getAlertFilter();
    if (!featureFlagService.isEnabled(SPG_ENABLE_NOTIFICATION_RULES, alert.getAccountId())) {
      if (rule.getAlertCategory() != alert.getCategory()) {
        log.debug("Alert category and rule category don't match. Alert does not satisfy rule. Alert: {}, Rule: {}",
            alert, rule);
        return false;
      }

      if (null == rule.getAlertFilter()) {
        log.debug(
            "[Missing Alert Filter] Alert matched the rule because rule category and alert category are same and no additional filters are present. Rule: {}, Alert: {}",
            alert, rule);
        return rule.getAlertCategory() == alert.getCategory();
      }
    }
    return alertSatisfiesFilter(alert, filter);
  }

  private boolean alertSatisfiesFilter(Alert alert, AlertFilter filter) {
    FilterMatcher matcher = null;

    // Matcher should be based on type on alert. Basic Filter Matcher will just compare alert type in alert with
    // alertType in rule. But for certain alerts like AlertType.SSOSyncFailedAlert , we might need custom checker which
    // will check that `ssoId` in rule is same as `ssoId` in alert.
    switch (alert.getType()) {
      case ManualInterventionNeeded:
        matcher = new ManualInterventionFilterMatcher(filter, alert);
        break;
      case CONTINUOUS_VERIFICATION_ALERT:
        matcher = new CVFilterMatcher(filter, alert);
        break;
      default:
        matcher = new BasicFilterMatcher(filter, alert);
    }

    return matcher.matchesCondition();
  }
}
