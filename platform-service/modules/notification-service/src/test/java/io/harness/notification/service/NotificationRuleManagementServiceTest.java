/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.rule.OwnerRule.JENNY;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.entities.EmailChannel;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.entities.NotificationCondition;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.entities.NotificationEventConfig;
import io.harness.notification.entities.NotificationRule;
import io.harness.notification.entities.NotificationRule.NotificationRuleKeys;
import io.harness.notification.repositories.NotificationRuleRepository;
import io.harness.notification.service.api.NotificationRuleManagementService;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;

public class NotificationRuleManagementServiceTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "AccountId";
  private static final String ORG_IDENTIFIER = "OrgId";
  private static final String PROJECT_IDENTIFIER = "ProjectId";
  private static final String EMAIL_ID = "test@harness.com";

  NotificationRuleManagementService notificationRuleManagementService;
  @Mock NotificationRuleRepository notificationRuleRepository;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    notificationRuleManagementService = new NotificationRuleManagementServiceImpl(notificationRuleRepository);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testCreateNotificationRule() {
    NotificationRule notificationRule = createNotificationRule();
    notificationRuleManagementService.create(notificationRule);
    verify(notificationRuleRepository, times(1)).save(notificationRule);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDisableNotificationRule() {
    NotificationRule notificationRule = createNotificationRule();
    notificationRule.setStatus(NotificationRule.Status.DISABLED);
    notificationRuleManagementService.update(notificationRule);
    verify(notificationRuleRepository, times(1)).save(notificationRule);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetNotificationRuleByEntity() {
    NotificationRule notificationRule = createNotificationRule();
    Criteria criteria = new Criteria();
    criteria.and(NotificationRuleKeys.accountIdentifier).is(ACCOUNT_IDENTIFIER);
    criteria.and(NotificationRuleKeys.orgIdentifier).is(ORG_IDENTIFIER);
    criteria.and(NotificationRuleKeys.projectIdentifier).is(PROJECT_IDENTIFIER);
    criteria.and(NotificationRuleKeys.notificationEntity).is(NotificationEntity.DELEGATE.name());

    when(notificationRuleRepository.findOne(criteria)).thenReturn(notificationRule);
    NotificationRule nr = notificationRuleManagementService.get(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, NotificationEntity.DELEGATE);
    assertEquals(NotificationEntity.DELEGATE, nr.getNotificationEntity());
    assertEquals(nr.getNotificationChannelForEvent(NotificationEvent.DELEGATE_DOWN).size(), 1);
    assertEquals(nr.getNotificationEvents().size(), 2);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetNotificationRuleByIdentifier() {
    NotificationRule notificationRule = createNotificationRule();
    notificationRule.setIdentifier("nr1");
    Criteria criteria = new Criteria();
    criteria.and(NotificationRuleKeys.accountIdentifier).is(ACCOUNT_IDENTIFIER);
    criteria.and(NotificationRuleKeys.orgIdentifier).is(ORG_IDENTIFIER);
    criteria.and(NotificationRuleKeys.projectIdentifier).is(PROJECT_IDENTIFIER);
    criteria.and(NotificationRuleKeys.identifier).is("nr1");
    when(notificationRuleRepository.findOne(criteria)).thenReturn(notificationRule);
    NotificationRule nr =
        notificationRuleManagementService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "nr1");
    assertEquals(nr.getIdentifier(), "nr1");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAllNotificationRulesInAccount() {
    NotificationRule notificationRule1 = createNotificationRule();
    NotificationRule notificationRule2 = createNotificationRule();
    Criteria criteria = new Criteria();
    criteria.and(NotificationRuleKeys.accountIdentifier).is(ACCOUNT_IDENTIFIER);
    criteria.and(NotificationRuleKeys.orgIdentifier).is(ORG_IDENTIFIER);
    criteria.and(NotificationRuleKeys.projectIdentifier).is(PROJECT_IDENTIFIER);
    when(notificationRuleRepository.findAll(criteria)).thenReturn(List.of(notificationRule1, notificationRule2));
    List<NotificationRule> notificationRuleList =
        notificationRuleManagementService.list(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertEquals(notificationRuleList.size(), 2);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDeleteNotificationRule() {
    NotificationRule notificationRule = createNotificationRule();
    notificationRuleManagementService.delete(notificationRule);
    verify(notificationRuleRepository, times(1)).delete(notificationRule);
  }

  private NotificationRule createNotificationRule() {
    NotificationChannel notificationChannel = NotificationChannel.builder()
                                                  .identifier("nr1")
                                                  .status(NotificationChannel.Status.ENABLED)
                                                  .notificationChannelType(NotificationChannelType.EMAIL)
                                                  .channel(EmailChannel.builder().emailIds(List.of(EMAIL_ID)).build())
                                                  .build();
    NotificationEventConfig notificationEventConfig1 = NotificationEventConfig.builder()
                                                           .notificationEvent(NotificationEvent.DELEGATE_DOWN)
                                                           .notificationChannels(List.of(notificationChannel))
                                                           .build();
    NotificationEventConfig notificationEventConfig2 = NotificationEventConfig.builder()
                                                           .notificationEvent(NotificationEvent.DELEGATE_EXPIRED)
                                                           .notificationChannels(List.of(notificationChannel))
                                                           .build();

    NotificationCondition notificationCondition =
        NotificationCondition.builder()
            .conditionName("del-condition")
            .notificationEventConfigs(List.of(notificationEventConfig1, notificationEventConfig2))
            .build();
    return NotificationRule.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .identifier("NR1")
        .notificationEntity(NotificationEntity.DELEGATE)
        .notificationConditions(List.of(notificationCondition))
        .build();
  }
}
