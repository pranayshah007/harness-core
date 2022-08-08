/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import io.harness.notification.SeedDataConfiguration;
import io.harness.notification.entities.NotificationTemplate;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.notification.service.api.SeedDataPopulaterService;
import io.harness.notification.templates.PredefinedTemplate;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class SeedDataPopulaterServiceImpl implements SeedDataPopulaterService {
  private final NotificationTemplateService notificationTemplateService;

  private void populatePredefinedTemplates(SeedDataConfiguration seedDataConfiguration) {
    if (seedDataConfiguration.shouldOverrideAllPredefinedTemplates()) {
      notificationTemplateService.dropPredefinedTemplates();
    }
    for (PredefinedTemplate predefinedTemplate : PredefinedTemplate.values()) {
      String identifier = predefinedTemplate.getIdentifier();
      String path = predefinedTemplate.getPath();
      URL url = null;
      try {
        url = Resources.getResource(path);
      } catch (IllegalArgumentException ex) {
        log.warn("Resource not found, skipping to seed the template - " + ex);
        continue;
      }

      try {
        byte[] file = Resources.toByteArray(url);
        NotificationTemplate notificationTemplate =
            NotificationTemplate.builder().identifier(identifier).file(file).harnessManaged(true).build();
        log.info("Saved predefined template: {} to database", identifier);
        notificationTemplateService.save(notificationTemplate);
      } catch (IOException exception) {
        log.error("Error while converting file to byte array: {}", path);
      } catch (DuplicateKeyException duplicateKeyException) {
        log.info("Predefined Notification Template: {} already present in DB, skip saving in seed templates step",
            predefinedTemplate.getIdentifier());
      }
    }
  }

  @Override
  public void populateSeedData(SeedDataConfiguration seedDataConfiguration) {
    populatePredefinedTemplates(seedDataConfiguration);
  }
}
