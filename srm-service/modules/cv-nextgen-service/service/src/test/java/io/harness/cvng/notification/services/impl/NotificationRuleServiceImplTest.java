/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.impl;

import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NotificationRuleServiceImplTest extends CvNextGenTestBase {
  @Inject private NotificationRuleServiceImpl notificationRuleService;
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreateNotificationRules() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    assertThat(notificationRuleResponse.getNotificationRule()).isEqualTo(notificationRuleDTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdateNotificationRules() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    notificationRuleDTO.setName("UpdateName");
    NotificationRuleResponse updatedNotificationRuleResponse = notificationRuleService.update(
        builderFactory.getContext().getProjectParams(), notificationRuleDTO.getIdentifier(), notificationRuleDTO);
    assertThat(updatedNotificationRuleResponse.getNotificationRule()).isEqualTo(notificationRuleDTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testDeleteNotificationRules() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    Boolean deleted = notificationRuleService.delete(
        builderFactory.getContext().getProjectParams(), notificationRuleDTO.getIdentifier());
    assertThat(deleted).isTrue();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetNotificationRules() {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    NotificationRuleResponse notificationRuleResponse = notificationRuleService.get(
        builderFactory.getContext().getProjectParams(), notificationRuleDTO.getIdentifier());
    assertThat(notificationRuleResponse.getNotificationRule()).isEqualTo(notificationRuleDTO);
  }
}
