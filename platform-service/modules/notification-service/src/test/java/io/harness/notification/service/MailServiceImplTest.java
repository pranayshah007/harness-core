/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ANKUSH;
import static io.harness.rule.OwnerRule.RICHA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.notification.NotificationRequest;
import io.harness.notification.SmtpConfig;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.SmtpConfigResponse;
import io.harness.notification.remote.dto.EmailDTO;
import io.harness.notification.remote.dto.EmailSettingDTO;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.senders.MailSenderImpl;
import io.harness.notification.service.MailServiceImpl.EmailTemplate;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.serializer.YamlUtils;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.userng.remote.UserNGClient;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class MailServiceImplTest extends CategoryTest {
  @Mock private NotificationSettingsService notificationSettingsService;
  @Mock private NotificationTemplateService notificationTemplateService;
  @Mock private YamlUtils yamlUtils;
  @Mock private SmtpConfig smtpConfigDefault;
  @Mock private MailSenderImpl mailSender;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private UserNGClient userNGClient;
  private MockedStatic<NGRestUtils> restUtilsMockedStatic;
  private MailServiceImpl mailService;
  private String accountId = "accountId";
  private String mailTemplateName = "email_test";
  private String emailAdress = "email@harness.io";
  private String id = "id";
  private EmailTemplate emailTemplate = new EmailTemplate();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    mailService = new MailServiceImpl(notificationSettingsService, notificationTemplateService, yamlUtils,
        smtpConfigDefault, mailSender, delegateGrpcClientWrapper, userNGClient);
    emailTemplate.setBody("this is test mail");
    emailTemplate.setSubject("test notification");
    restUtilsMockedStatic = mockStatic(NGRestUtils.class);
    when(NGRestUtils.getResponse(any())).thenReturn(true);
  }

  @After
  public void cleanup() {
    restUtilsMockedStatic.close();
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_EmptyRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().build();
    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_OnlyIdInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder().setId(id).build();
    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_NoTemplateIdInRequest() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setEmail(
                NotificationRequest.Email.newBuilder().addAllEmailIds(Collections.singletonList(emailAdress)).build())
            .build();
    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_NoRecipientInRequest() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setEmail(NotificationRequest.Email.newBuilder()
                                                                .setTemplateId(mailTemplateName)
                                                                .addAllEmailIds(Collections.EMPTY_LIST)
                                                                .build())
                                                  .build();
    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_ValidCase() {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setId(id)
                                                  .setAccountId(accountId)
                                                  .setEmail(NotificationRequest.Email.newBuilder()
                                                                .setTemplateId(mailTemplateName)
                                                                .addAllEmailIds(Collections.singletonList(emailAdress))
                                                                .build())
                                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true, false)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of("This is a test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(notificationSettingsService.getSmtpConfig(eq(accountId))).thenReturn(Optional.of(smtpConfigDefault));
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    when(notificationSettingsService.getSmtpConfigResponse(eq(accountId))).thenReturn(new SmtpConfigResponse());

    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);
    assertTrue(notificationProcessingResponse.equals(NotificationProcessingResponse.trivialResponseWithNoRetries));
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationSettingsService.getSmtpConfigResponse(eq(accountId)))
        .thenReturn(new SmtpConfigResponse(SmtpConfig.builder().build(), Collections.EMPTY_LIST));
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("");
    notificationProcessingResponse = mailService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendNotification_ValidCaseMailUserGroup() {
    NotificationRequest notificationRequest =
        NotificationRequest.newBuilder()
            .setId(id)
            .setAccountId(accountId)
            .setEmail(NotificationRequest.Email.newBuilder()
                          .setTemplateId(mailTemplateName)
                          .addAllEmailIds(Collections.singletonList(emailAdress))
                          .addUserGroup(NotificationRequest.UserGroup.newBuilder()
                                            .setIdentifier("identifier")
                                            .setProjectIdentifier("projectIdentifier")
                                            .setOrgIdentifier("orgIdentifier")
                                            .build())
                          .build())
            .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any()))
        .thenReturn(Optional.empty(), Optional.of("This is a test notification"));
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(false);
    when(notificationSettingsService.getSmtpConfig(eq(accountId))).thenReturn(Optional.of(smtpConfigDefault));
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    when(notificationSettingsService.getSmtpConfigResponse(eq(accountId))).thenReturn(new SmtpConfigResponse());

    NotificationProcessingResponse notificationProcessingResponse = mailService.send(notificationRequest);
    assertEquals(notificationProcessingResponse, NotificationProcessingResponse.trivialResponseWithNoRetries);
    notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(notificationTemplateService.getTemplateAsString(eq(mailTemplateName), any()))
        .thenReturn(Optional.of("this is test notification"));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NotificationTaskResponse.builder().processingResponse(notificationExpectedResponse).build());
    when(notificationSettingsService.getSendNotificationViaDelegate(eq(accountId))).thenReturn(true);
    notificationProcessingResponse = mailService.send(notificationRequest);
    assertEquals(notificationExpectedResponse, notificationProcessingResponse);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendTestNotification_CheckAllGaurds() {
    final NotificationSettingDTO notificationSettingDTO1 = EmailSettingDTO.builder().build();
    assertThatThrownBy(() -> mailService.sendTestNotification(notificationSettingDTO1))
        .isInstanceOf(NotificationException.class);

    final NotificationSettingDTO notificationSettingDTO2 = EmailSettingDTO.builder().accountId(accountId).build();
    assertThatThrownBy(() -> mailService.sendTestNotification(notificationSettingDTO2))
        .isInstanceOf(NotificationException.class);

    final NotificationSettingDTO notificationSettingDTO3 =
        EmailSettingDTO.builder().recipient("email@harness.io").build();
    assertThatThrownBy(() -> mailService.sendTestNotification(notificationSettingDTO3))
        .isInstanceOf(NotificationException.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void sendTestNotification_ValidRequest() {
    final NotificationSettingDTO notificationSettingDTO4 =
        EmailSettingDTO.builder().accountId(accountId).recipient("email@harness.io").build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of("This is a test notification"));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    boolean response = mailService.sendTestNotification(notificationSettingDTO4);
    assertTrue(response);
  }

  @SneakyThrows
  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void sendEmail_ValidRequest() {
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .accountId(accountId)
                                  .toRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .ccRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of("This is a test notification"));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    NotificationTaskResponse response = mailService.sendEmail(emailDTO);
    assertTrue(response.getProcessingResponse().getResult().iterator().next());
  }

  @SneakyThrows
  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void sendEmail_no_emails() {
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .toRecipients(Collections.emptySet())
                                  .ccRecipients(Collections.emptySet())
                                  .accountId(accountId)
                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of("This is a test notification"));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    assertThatThrownBy(() -> mailService.sendEmail(emailDTO)).isInstanceOf(NotificationException.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void sendEmail_invalid_emails() {
    Set<String> emails = new HashSet<>();
    emails.add("email_harness.io");
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .accountId(accountId)
                                  .toRecipients(emails)
                                  .ccRecipients(emails)
                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of("This is a test notification"));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    assertThatThrownBy(() -> mailService.sendEmail(emailDTO)).isInstanceOf(NotificationException.class);
  }
  @SneakyThrows
  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void sendEmail_no_account_id() {
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .toRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .ccRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(true)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of("This is a test notification"));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    assertThatThrownBy(() -> mailService.sendEmail(emailDTO)).isInstanceOf(NotificationException.class);
  }

  @SneakyThrows
  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void sendEmail_response_wrong() {
    final EmailDTO emailDTO = EmailDTO.builder()
                                  .notificationId("notificationId")
                                  .toRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .ccRecipients(new HashSet<>(Collections.singletonList("email@harness.io")))
                                  .build();
    NotificationProcessingResponse notificationExpectedResponse =
        NotificationProcessingResponse.builder().result(Arrays.asList(false)).shouldRetry(false).build();
    when(mailSender.send(any(), any(), any(), any(), any(), any())).thenReturn(notificationExpectedResponse);
    when(notificationTemplateService.getTemplateAsString(any(), any()))
        .thenReturn(Optional.of("This is a test notification"));
    when(yamlUtils.read(any(), (TypeReference<EmailTemplate>) any())).thenReturn(emailTemplate);
    assertThatThrownBy(() -> mailService.sendEmail(emailDTO)).isInstanceOf(NotificationException.class);
  }
}
