/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;

import software.wings.alerts.AlertCategory;
import software.wings.beans.alert.AlertNotificationRule;
import software.wings.beans.alert.AlertNotificationRule.AlertNotificationRulekeys;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AlertNotificationRuleService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
public class AlertNotificationRuleServiceImpl implements AlertNotificationRuleService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserGroupService userGroupService;

  @Override
  public AlertNotificationRule create(AlertNotificationRule rule) {
    validateCreate(rule);
    return wingsPersistence.saveAndGet(AlertNotificationRule.class, rule);
  }

  private void validateCreate(AlertNotificationRule rule) {
    if (rule.isDefault() && isDefaultAlertNotificationRuleCreated(rule.getAccountId())) {
      throw new InvalidRequestException("Default alert notification rule already exists");
    }
  }

  private boolean isDefaultAlertNotificationRuleCreated(String accountId) {
    return getDefaultAlertNotificationRule(accountId).isPresent();
  }

  private Optional<AlertNotificationRule> getDefaultAlertNotificationRule(String accountId) {
    List<AlertNotificationRule> rules = wingsPersistence.createQuery(AlertNotificationRule.class)
                                            .filter(AlertNotificationRulekeys.accountId, accountId)
                                            .filter(AlertNotificationRule.ALERT_CATEGORY, AlertCategory.All)
                                            .asList();
    return rules.isEmpty() ? Optional.empty() : Optional.of(rules.get(0));
  }

  @Override
  public AlertNotificationRule update(AlertNotificationRule rule) {
    validateUpdate(rule);
    return wingsPersistence.saveAndGet(AlertNotificationRule.class, rule);
  }

  @Override
  public AlertNotificationRule createDefaultRule(String accountId) {
    UserGroup userGroup = userGroupService.getDefaultUserGroup(accountId);

    Set<String> userGroupsToNotify = new HashSet<>();
    if (null != userGroup) {
      userGroupsToNotify.add(userGroup.getUuid());
    } else {
      log.error(
          "No default User group found. accountId={}. Default alert notification rule will not have any notifiers.",
          accountId);
    }
    AlertNotificationRule defaultRule =
        new AlertNotificationRule(accountId, AlertCategory.All, null, userGroupsToNotify);
    defaultRule = create(defaultRule);

    return defaultRule;
  }

  private void validateUpdate(AlertNotificationRule rule) {
    Optional<AlertNotificationRule> existingRule = getById(rule.getAccountId(), rule.getUuid());
    if (!existingRule.isPresent()) {
      throw new InvalidRequestException(
          "Can not update alert notification rule. No such alert notification rule exists");
    }
    if (!existingRule.get().isDefault() && rule.isDefault()
        && isDefaultAlertNotificationRuleCreated(rule.getAccountId())) {
      throw new InvalidRequestException(
          "Can not update alert notification rule. Default alert notification rule already exists");
    }
  }

  private Optional<AlertNotificationRule> getById(String accountId, String ruleId) {
    List<AlertNotificationRule> rules = wingsPersistence.createQuery(AlertNotificationRule.class)
                                            .filter(AlertNotificationRulekeys.accountId, accountId)
                                            .filter(AlertNotificationRule.ID_KEY2, ruleId)
                                            .asList();
    return rules.isEmpty() ? Optional.empty() : Optional.of(rules.get(0));
  }

  @Override
  @Nonnull
  public List<AlertNotificationRule> getAll(String accountId) {
    Query<AlertNotificationRule> query = wingsPersistence.createQuery(AlertNotificationRule.class)
                                             .filter(AlertNotificationRulekeys.accountId, accountId);
    return CollectionUtils.emptyIfNull(query.asList());
  }

  @Override
  public void deleteById(String ruleId, String accountId) {
    Optional<AlertNotificationRule> defaultAlertNotificationRule = getDefaultAlertNotificationRule(accountId);
    if (defaultAlertNotificationRule.isPresent() && defaultAlertNotificationRule.get().getUuid().equals(ruleId)) {
      throw new InvalidRequestException("Default alert notification rule can not be deleted");
    }
    wingsPersistence.delete(wingsPersistence.createQuery(AlertNotificationRule.class)
                                .filter(AlertNotificationRulekeys.accountId, accountId)
                                .filter(AlertNotificationRule.ID_KEY2, ruleId));
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(wingsPersistence.createQuery(AlertNotificationRule.class)
                                .filter(AlertNotificationRulekeys.accountId, accountId));
  }
}
