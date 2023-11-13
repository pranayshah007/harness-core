/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notifications;

import io.harness.notification.Team;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.templates.PredefinedTemplate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class NotificationTemplateRegistrar implements Runnable {
  @Inject NotificationClient notificationClient;

  @Override
  public void run() {
        List<PredefinedTemplate> templates =
                Arrays.asList(PredefinedTemplate.DELEGATE_DOWN_EMAIL, PredefinedTemplate.DELEGATE_EXPIRED_EMAIL,
                        PredefinedTemplate.DELEGATE_ABOUT_EXPIRE_EMAIL,
                        PredefinedTemplate.DELEGATE_DOWN_SLACK, PredefinedTemplate.DELEGATE_EXPIRED_SLACK,
                        PredefinedTemplate.DELEGATE_ABOUT_EXPIRE_SLACK,
                        PredefinedTemplate.DELEGATE_DOWN_MSTEAMS, PredefinedTemplate.DELEGATE_EXPIRED_MSTEAMS,
                        PredefinedTemplate.DELEGATE_ABOUT_EXPIRE_MSTEAMS,
                        PredefinedTemplate.DELEGATE_DOWN_PAGERDUTY, PredefinedTemplate.DELEGATE_EXPIRED_PAGERDUTY,
                        PredefinedTemplate.DELEGATE_ABOUT_EXPIRE_PAGERDUTY,
                        PredefinedTemplate.DELEGATE_DOWN_WEBHOOK, PredefinedTemplate.DELEGATE_EXPIRED_WEBHOOK,
                        PredefinedTemplate.DELEGATE_ABOUT_EXPIRE_WEBHOOK);
        for (PredefinedTemplate template : templates) {
      try {
        log.info("Registering {} with NotificationService", template);
        notificationClient.saveNotificationTemplate(Team.OTHER, template, true);
      } catch (Exception ex) {
        log.error("Unable to save {} to NotificationService - skipping register notification templates.", template, ex);
      }
        }
  }
}
