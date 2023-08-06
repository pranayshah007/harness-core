/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.notification.Team;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.templates.PredefinedTemplate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class NotificationTemplateRegistrar implements Runnable {
  @Inject NotificationClient notificationClient;

  @Override
  public void run() {
    try {
      int timout = 1;
      List<PredefinedTemplate> templates = new ArrayList<>(
          Arrays.asList(PredefinedTemplate.PIPELINE_PLAIN_SLACK, PredefinedTemplate.PIPELINE_PLAIN_WEBHOOK,
              PredefinedTemplate.PIPELINE_PLAIN_EMAIL, PredefinedTemplate.PIPELINE_PLAIN_PAGERDUTY,
              PredefinedTemplate.PIPELINE_PLAIN_MSTEAMS, PredefinedTemplate.STAGE_PLAIN_SLACK,
              PredefinedTemplate.STAGE_PLAIN_WEBHOOK, PredefinedTemplate.STAGE_PLAIN_EMAIL,
              PredefinedTemplate.STAGE_PLAIN_PAGERDUTY, PredefinedTemplate.STAGE_PLAIN_MSTEAMS,
              PredefinedTemplate.STEP_PLAIN_EMAIL, PredefinedTemplate.STEP_PLAIN_SLACK,
              PredefinedTemplate.STEP_PLAIN_WEBHOOK, PredefinedTemplate.STEP_PLAIN_MSTEAMS,
              PredefinedTemplate.STEP_PLAIN_PAGERDUTY, PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_SLACK,
              PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_EMAIL,
              PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_SLACK,
              PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_EMAIL,
              PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_MSTEAMS,
              PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_MSTEAMS,
              PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_SLACK,
              PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_EMAIL,
              PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_MSTEAMS,
              PredefinedTemplate.HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_SLACK,
              PredefinedTemplate.HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_EMAIL,
              PredefinedTemplate.HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_MSTEAMS));
      while (true) {
        List<PredefinedTemplate> unprocessedTemplate = new ArrayList<>();
        for (PredefinedTemplate template : templates) {
          log.info("Registering {} with NotificationService", template);
          try {
            notificationClient.saveNotificationTemplate(Team.PIPELINE, template, true);
          } catch (Exception ex) {
            log.error(String.format("Unable to register tempate with id: %s", template.getIdentifier()), ex);
            unprocessedTemplate.add(template);
          }
        }
        if (unprocessedTemplate.isEmpty()) {
          break;
        }

        Thread.sleep(timout);

        timout *= 10;
        templates = unprocessedTemplate;
      }
    } catch (InterruptedException e) {
      log.error("", e);
    }
  }
}
