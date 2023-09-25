/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.entities.NotificationChannel.NotificationChannelKeys;
import io.harness.notification.entities.NotificationRule;
import io.harness.notification.entities.NotificationRule.NotificationRuleKeys;
import io.harness.notification.repositories.NotificationChannelRepository;
import io.harness.notification.repositories.NotificationRuleRepository;
import io.harness.notification.service.api.NotificationManagementService;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class NotificationManagementServiceImpl implements NotificationManagementService {
  private final NotificationRuleRepository notificationRuleRepository;
  private final NotificationChannelRepository notificationChannelRepository;

  @Override
  public NotificationRule create(NotificationRule notificationRule) {
    return notificationRuleRepository.save(notificationRule);
  }

  @Override
  public NotificationRule update(NotificationRule notificationRule) {
    return notificationRuleRepository.save(notificationRule);
  }

  @Override
  public NotificationRule get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String notificationRuleNameIdentifier) {
    Criteria criteria = createNotificationRuleFetchCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, notificationRuleNameIdentifier);
    return notificationRuleRepository.findOne(criteria);
  }

  @Override
  public List<NotificationRule> list(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return null;
  }

  @Override
  public boolean delete(NotificationChannel notificationChannel) {
    notificationChannelRepository.delete(notificationChannel);
    return true;
  }

  @Override
  public NotificationChannel create(NotificationChannel notificationChannel) {
    NotificationChannel channel = notificationChannelRepository.save(notificationChannel);
    return channel;
  }

  @Override
  public NotificationChannel update(NotificationChannel notificationChannel) {
    return notificationChannelRepository.save(notificationChannel);
  }

  @Override
  public NotificationChannel getNotificationChannel(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria criteria =
        createNotificationChannelFetchCriteria(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return notificationChannelRepository.findOne(criteria);
  }

  @Override
  public boolean deleteNotificationRule(NotificationRule notificationRule) {
    notificationRuleRepository.delete(notificationRule);
    return true;
  }

  @Override
  public List<NotificationChannel> getNotificationChannelList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return null;
  }

  private Criteria createNotificationChannelFetchCriteria(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria criteria = createScopeCriteria(accountIdentifier, orgIdentifier, projectIdentifier);
    criteria.and(NotificationChannelKeys.name).is(identifier);
    return criteria;
  }

  private Criteria createScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(NotificationChannelKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(NotificationChannelKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(NotificationChannelKeys.projectIdentifier).is(projectIdentifier);
    return criteria;
  }

  private Criteria createNotificationRuleFetchCriteria(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria criteria = createNotificationRuleScopeCriteria(accountIdentifier, orgIdentifier, projectIdentifier);
    criteria.and(NotificationRuleKeys.notificationRuleIdentifier).is(identifier);
    return criteria;
  }

  private Criteria createNotificationRuleScopeCriteria(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(NotificationRuleKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(NotificationRuleKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(NotificationRuleKeys.projectIdentifier).is(projectIdentifier);
    return criteria;
  }
}
