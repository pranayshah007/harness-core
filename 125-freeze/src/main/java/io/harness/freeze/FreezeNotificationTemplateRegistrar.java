/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze;

import static io.harness.annotations.dev.HarnessTeam.CDC;

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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_FREEZE})
@OwnedBy(CDC)
@Singleton
@Slf4j
public class FreezeNotificationTemplateRegistrar implements Runnable {
  @Inject NotificationClient notificationClient;

  @Override
  public void run() {
    try {
      int timout = 1;
      List<PredefinedTemplate> templates =
          new ArrayList<>(Arrays.asList(PredefinedTemplate.FREEZE_EMAIL_ALERT, PredefinedTemplate.FREEZE_SLACK_ALERT,
              PredefinedTemplate.FREEZE_MSTEAMS_ALERT, PredefinedTemplate.FREEZE_PD_ALERT,
              PredefinedTemplate.PIPELINE_REJECTED_PD_ALERT, PredefinedTemplate.PIPELINE_REJECTED_EMAIL_ALERT,
              PredefinedTemplate.PIPELINE_REJECTED_SLACK_ALERT, PredefinedTemplate.PIPELINE_REJECTED_MSTEAMS_ALERT,
              PredefinedTemplate.FREEZE_ENABLED_PD_ALERT, PredefinedTemplate.FREEZE_ENABLED_SLACK_ALERT,
              PredefinedTemplate.FREEZE_ENABLED_MSTEAMS_ALERT, PredefinedTemplate.FREEZE_ENABLED_EMAIL_ALERT));
      while (true) {
        List<PredefinedTemplate> unprocessedTemplate = new ArrayList<>();
        for (PredefinedTemplate template : templates) {
          log.info("Registering {} with NotificationService", template);
          try {
            notificationClient.saveNotificationTemplate(Team.PIPELINE, template, true);
          } catch (Exception ex) {
            log.error(String.format("Unable to register template with id: %s", template.getIdentifier()), ex);
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
