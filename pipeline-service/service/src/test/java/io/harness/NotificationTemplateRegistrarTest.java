/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.notification.Team;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.remote.dto.TemplateDTO;
import io.harness.notification.templates.PredefinedTemplate;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class NotificationTemplateRegistrarTest extends PipelineServiceTestBase {
  @Mock NotificationClient notificationClient;
  @InjectMocks NotificationTemplateRegistrar notificationTemplateRegistrar;
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testNotificationTemplatesRegistration() {
    doReturn(TemplateDTO.builder().build())
        .when(notificationClient)
        .saveNotificationTemplate(any(), any(), anyBoolean());
    notificationTemplateRegistrar.run();
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.PIPELINE_PLAIN_SLACK, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.PIPELINE_PLAIN_EMAIL, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.PIPELINE_PLAIN_PAGERDUTY, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.PIPELINE_PLAIN_MSTEAMS, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.STAGE_PLAIN_SLACK, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.STAGE_PLAIN_EMAIL, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.STAGE_PLAIN_PAGERDUTY, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.STAGE_PLAIN_MSTEAMS, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.STEP_PLAIN_EMAIL, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.STEP_PLAIN_SLACK, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.STEP_PLAIN_MSTEAMS, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.STEP_PLAIN_PAGERDUTY, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_SLACK, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.PIPELINE, PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_EMAIL, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(
            Team.PIPELINE, PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_SLACK, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(
            Team.PIPELINE, PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_EMAIL, true);
  }
}
