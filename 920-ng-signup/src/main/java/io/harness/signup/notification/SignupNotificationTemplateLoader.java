/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.signup.notification;

import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.notification.Team;
import io.harness.notification.remote.NotificationHTTPClient;
import io.harness.signup.SignupNotificationConfiguration;
import io.harness.templates.google.DownloadResult;
import io.harness.templates.google.GoogleCloudFileService;

import com.google.common.cache.CacheLoader;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

@Slf4j
public class SignupNotificationTemplateLoader extends CacheLoader<EmailType, Boolean> {
  private final GoogleCloudFileService googleCloudFileService;
  private final NotificationHTTPClient notificationHTTPClient;
  private final SignupNotificationConfiguration notificationConfiguration;

  @Inject
  SignupNotificationTemplateLoader(GoogleCloudFileService googleCloudFileService,
      NotificationHTTPClient notificationHTTPClient, SignupNotificationConfiguration notificationConfiguration) {
    this.googleCloudFileService = googleCloudFileService;
    this.notificationHTTPClient = notificationHTTPClient;
    this.notificationConfiguration = notificationConfiguration;

    try {
      googleCloudFileService.initialize(notificationConfiguration.getProjectId());
    } catch (IllegalArgumentException e) {
      log.warn("Failed to initialize GCS for signup notification template", e);
    }
  }

  @Override
  public Boolean load(EmailType emailType) {
    try {
      EmailInfo emailInfo = notificationConfiguration.getTemplates().get(emailType);
      DownloadResult downloadResult =
          googleCloudFileService.downloadFile(emailInfo.getGcsFileName(), notificationConfiguration.getBucketName());
      if (downloadResult.getContent() != null) {
        // save template to notification service
        final MultipartBody.Part formData = MultipartBody.Part.createFormData(
            "file", null, RequestBody.create(MultipartBody.FORM, downloadResult.getContent()));
        getResponse(
            notificationHTTPClient.saveNotificationTemplate(formData, Team.GTM, emailInfo.getTemplateId(), true));
        return true;
      } else {
        log.error("File {} doesn't exists in bucket {}", emailInfo.getGcsFileName(),
            notificationConfiguration.getBucketName());
      }
    } catch (Exception e) {
      log.error("Failed to download/save notification template for {}", emailType.name(), e);
    }
    return false;
  }
}
