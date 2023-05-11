/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.signup.notification;

import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.user.UserInfo;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.rule.Owner;
import io.harness.signup.SignupNotificationConfiguration;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SaasSignupNotificationHelperTest extends CategoryTest {
  @InjectMocks SaasSignupNotificationHelper signupNotificationHelper;
  @Mock private NotificationClient notificationClient;
  @Mock private SignupNotificationTemplateLoader catchLoader;
  @Mock private SignupNotificationConfiguration notificationConfiguration;

  private ArgumentCaptor<EmailChannel> emailChannelCaptor;
  private UserInfo userInfo;
  private static final String DEFAULT_TEMPLATE_ID = "default";
  private static final String EMAIL = "1@1";
  private static final String ID = "id";
  private static final String ACCOUNT_ID = "account";
  private static final String URL = "/test";
  private static final String VERIFY_TEMPLATE_ID = "verify_email";
  private static final String CONFIRM_TEMPLATE_ID = "signup_confirmation";
  private static final String GCS_FILE_NAME = "gname";

  @Before
  public void setup() throws IOException {
    initMocks(this);
    userInfo = UserInfo.builder().uuid(ID).defaultAccountId(ACCOUNT_ID).email(DEFAULT_TEMPLATE_ID).email(EMAIL).build();

    EmailInfo veriyEmailInfo = EmailInfo.builder().templateId(VERIFY_TEMPLATE_ID).gcsFileName(GCS_FILE_NAME).build();
    EmailInfo confirmEmailInfo = EmailInfo.builder().templateId(CONFIRM_TEMPLATE_ID).gcsFileName(GCS_FILE_NAME).build();
    Map<EmailType, EmailInfo> templates = ImmutableMap.<EmailType, EmailInfo>builder()
                                              .put(EmailType.VERIFY, veriyEmailInfo)
                                              .put(EmailType.CONFIRM, confirmEmailInfo)
                                              .build();
    when(notificationConfiguration.getTemplates()).thenReturn(templates);

    emailChannelCaptor = ArgumentCaptor.forClass(EmailChannel.class);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendVerifyNotification() {
    when(catchLoader.load(any())).thenReturn(true);
    signupNotificationHelper.sendSignupNotification(userInfo, EmailType.VERIFY, DEFAULT_TEMPLATE_ID, URL);

    verify(notificationClient, times(1)).sendNotificationAsync(emailChannelCaptor.capture());
    EmailChannel value = emailChannelCaptor.getValue();
    assertThat(value.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(value.getTemplateData().get("url")).isEqualTo(URL);
    assertThat(value.getTemplateId()).isEqualTo(VERIFY_TEMPLATE_ID);
    assertThat(value.getRecipients()).contains(EMAIL);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendConfirmNotification() {
    when(catchLoader.load(any())).thenReturn(true);

    signupNotificationHelper.sendSignupNotification(userInfo, EmailType.CONFIRM, DEFAULT_TEMPLATE_ID, URL);

    verify(notificationClient, times(1)).sendNotificationAsync(emailChannelCaptor.capture());
    EmailChannel value = emailChannelCaptor.getValue();
    assertThat(value.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(value.getTemplateData().get("url")).isEqualTo(URL);
    assertThat(value.getTemplateId()).isEqualTo(CONFIRM_TEMPLATE_ID);
    assertThat(value.getRecipients()).contains(EMAIL);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendVerifyNotificationFailover() {
    when(catchLoader.load(any())).thenReturn(false);

    signupNotificationHelper.sendSignupNotification(userInfo, EmailType.CONFIRM, DEFAULT_TEMPLATE_ID, URL);

    verify(notificationClient, times(1)).sendNotificationAsync(emailChannelCaptor.capture());
    EmailChannel value = emailChannelCaptor.getValue();
    assertThat(value.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(value.getTemplateData().get("url")).isEqualTo(URL);
    assertThat(value.getTemplateId()).isEqualTo(DEFAULT_TEMPLATE_ID);
    assertThat(value.getRecipients()).contains(EMAIL);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendConfirmNotificationFailover() {
    when(catchLoader.load(any())).thenReturn(false);

    signupNotificationHelper.sendSignupNotification(userInfo, EmailType.CONFIRM, DEFAULT_TEMPLATE_ID, URL);

    verify(notificationClient, times(1)).sendNotificationAsync(emailChannelCaptor.capture());
    EmailChannel value = emailChannelCaptor.getValue();
    assertThat(value.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(value.getTemplateData().get("url")).isEqualTo(URL);
    assertThat(value.getTemplateId()).isEqualTo(DEFAULT_TEMPLATE_ID);
    assertThat(value.getRecipients()).contains(EMAIL);
  }
}
