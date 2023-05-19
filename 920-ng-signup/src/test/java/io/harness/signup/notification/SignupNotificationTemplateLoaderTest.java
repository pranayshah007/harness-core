/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.signup.notification;

import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.remote.NotificationHTTPClient;
import io.harness.notification.remote.dto.TemplateDTO;
import io.harness.rule.Owner;
import io.harness.signup.SignupNotificationConfiguration;
import io.harness.templates.google.DownloadResult;
import io.harness.templates.google.GoogleCloudFileService;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class SignupNotificationTemplateLoaderTest extends CategoryTest {
  @Mock GoogleCloudFileService googleCloudFileService;
  @Mock NotificationHTTPClient notificationHTTPClient;
  @Mock SignupNotificationConfiguration notificationConfiguration;
  @InjectMocks SignupNotificationTemplateLoader signupNotificationTemplateLoader;

  private EmailInfo emailInfo;
  private static final String TEMPLATE_ID = "1";
  private static final String GCS_FILENAME = "2";
  private static final String BUCKET_NAME = "bucket";

  @Before
  public void setup() {
    initMocks(this);
    emailInfo = EmailInfo.builder().templateId(TEMPLATE_ID).gcsFileName(GCS_FILENAME).build();
    Map<EmailType, EmailInfo> templates =
        ImmutableMap.<EmailType, EmailInfo>builder().put(EmailType.VERIFY, emailInfo).build();
    when(notificationConfiguration.getTemplates()).thenReturn(templates);
    when(notificationConfiguration.getBucketName()).thenReturn(BUCKET_NAME);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testLoad() throws IOException {
    DownloadResult downloadResult = DownloadResult.builder().content(new byte[1]).build();
    when(googleCloudFileService.downloadFile(GCS_FILENAME, BUCKET_NAME)).thenReturn(downloadResult);
    Call<ResponseDTO<TemplateDTO>> request = mock(Call.class);
    when(notificationHTTPClient.saveNotificationTemplate(any(), any(), any(), any())).thenReturn(request);
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(mock(TemplateDTO.class))));

    Boolean result = signupNotificationTemplateLoader.load(EmailType.VERIFY);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testLoadFailedFromGcs() {
    DownloadResult downloadResult = DownloadResult.builder().content(null).build();
    when(googleCloudFileService.downloadFile(GCS_FILENAME, BUCKET_NAME)).thenReturn(downloadResult);

    Boolean result = signupNotificationTemplateLoader.load(EmailType.VERIFY);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testLoadFailedDuringSavingTemplate() {
    DownloadResult downloadResult = DownloadResult.builder().content(new byte[1]).build();
    when(googleCloudFileService.downloadFile(GCS_FILENAME, BUCKET_NAME)).thenReturn(downloadResult);
    when(notificationHTTPClient.saveNotificationTemplate(any(), any(), any(), any())).thenThrow(new RuntimeException());

    Boolean result = signupNotificationTemplateLoader.load(EmailType.VERIFY);
    assertThat(result).isFalse();
  }
}
