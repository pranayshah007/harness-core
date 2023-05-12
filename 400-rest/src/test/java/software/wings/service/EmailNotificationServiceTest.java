/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.ng.core.account.DefaultExperience.CG;
import static io.harness.rule.OwnerRule.RUSHABH;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static com.google.common.collect.Lists.newArrayList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.queue.QueuePublisher;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.EmailSendingFailedAlert;
import software.wings.helpers.ext.mail.Mailer;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.persistence.mail.EmailData;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.EmailHelperUtils;
import software.wings.utils.EmailUtils;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Verifier;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by peeyushaggarwal on 5/25/16.
 */
public class EmailNotificationServiceTest extends WingsBaseTest {
  private static final EmailData nonSystemEmailTemplateData = EmailData.builder()
                                                                  .to(newArrayList("to"))
                                                                  .cc(newArrayList("cc"))
                                                                  .templateName("templateName")
                                                                  .templateModel("templateModel")
                                                                  .accountId(ACCOUNT_ID)
                                                                  .build();
  private static final EmailData nonSystemEmailBodyData = EmailData.builder()
                                                              .to(newArrayList("to"))
                                                              .cc(newArrayList("cc"))
                                                              .body("body")
                                                              .subject("subject")
                                                              .accountId(ACCOUNT_ID)
                                                              .build();

  private static final EmailData systemEmailTemplateData = EmailData.builder()
                                                               .to(newArrayList("to"))
                                                               .cc(newArrayList("cc"))
                                                               .templateName("templateName")
                                                               .templateModel("templateModel")
                                                               .system(true)
                                                               .accountId(ACCOUNT_ID)
                                                               .build();

  private static final EmailData testEmailTemplateData = EmailData.builder()
                                                             .to(newArrayList("to"))
                                                             .cc(newArrayList("cc"))
                                                             .templateName("templateName")
                                                             .templateModel("templateModel")
                                                             .system(true)
                                                             .build();

  private static final EmailData customerEmailBodyData = EmailData.builder()
                                                             .to(newArrayList("to"))
                                                             .cc(newArrayList("cc"))
                                                             .body("body")
                                                             .subject("subject")
                                                             .accountId(ACCOUNT_ID)
                                                             .system(false)
                                                             .build();

  private static final SmtpConfig smtpConfig = SmtpConfig.builder()
                                                   .host("testHost")
                                                   .username("testUser")
                                                   .password("testPassword".toCharArray())
                                                   .accountId("testAccount")
                                                   .build();

  private static final Account dummyAccount = anAccount()
                                                  .withCompanyName("Harness")
                                                  .withAccountName("Account Name 1")
                                                  .withAccountKey("ACCOUNT_KEY")
                                                  .withLicenseInfo(getLicenseInfo())
                                                  .withDefaultExperience(CG)
                                                  .withWhitelistedDomains(new HashSet<>())
                                                  .build();

  @Mock private Mailer mailer;

  @Mock private SettingsService settingsService;

  @Mock private QueuePublisher<EmailData> queue;

  @Mock private SecretManager secretManager;

  @Mock private DelegateService delegateService;

  @Mock private MainConfiguration mainConfiguration;

  @Mock private AlertService alertService;

  @Mock private AccountService accountService;

  @Inject EmailUtils emailUtils;

  @InjectMocks @Inject private EmailHelperUtils emailHelperUtils;
  /**
   * The Verify.
   */
  @Rule
  public Verifier verify = new Verifier() {
    @Override
    protected void verify() throws Throwable {
      verifyNoMoreInteractions(queue, mailer, delegateService);
    }
  };
  @InjectMocks @Inject private EmailNotificationService emailDataNotificationService;

  /**
   * Setup mocks.
   */
  @Before
  public void setupMocks() {
    when(settingsService.getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.SMTP.name()))
        .thenReturn(newArrayList(aSettingAttribute().withName("name").withValue(smtpConfig).build()));
    when(accountService.get(any())).thenReturn(dummyAccount);
    SmtpConfig mock = mock(SmtpConfig.class);
    when(mock.valid()).thenReturn(true);
    when(mainConfiguration.getSmtpConfig()).thenReturn(mock);
  }

  /**
   * Should send email with template.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void shouldSendEmailWithTemplate() {
    emailDataNotificationService.send(nonSystemEmailTemplateData);
    verify(delegateService).queueTaskV2(any(DelegateTask.class));
    verify(settingsService).getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.SMTP.name());
    verifyNoMoreInteractions(alertService);
  }

  /**
   * Should send email with body.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void shouldSendEmailWithBody() {
    emailDataNotificationService.send(nonSystemEmailBodyData);
    verify(delegateService).queueTaskV2(any(DelegateTask.class));
    verify(settingsService).getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.SMTP.name());
    verifyNoMoreInteractions(mailer);
    verifyNoMoreInteractions(alertService);
  }

  /**
   * Should send email with template.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void shouldSendSystemEmailWithTemplate() {
    emailDataNotificationService.send(systemEmailTemplateData);
    verify(mailer).send(mainConfiguration.getSmtpConfig(), Collections.emptyList(), systemEmailTemplateData.toDTO());
    verifyNoMoreInteractions(delegateService);
    verify(alertService).closeAlertsOfType(ACCOUNT_ID, GLOBAL_APP_ID, AlertType.EMAIL_NOT_SENT_ALERT);
  }

  /**
   * Should send email with body.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void shouldSendCustomerEmailWithBody() {
    emailDataNotificationService.send(customerEmailBodyData);
    verifyNoMoreInteractions(mailer);
    verify(delegateService).queueTaskV2(any(DelegateTask.class));
    verify(settingsService).getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.SMTP.name());
    verifyNoMoreInteractions(alertService);
  }

  /**
   * Should send async email with template.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void shouldSendAsyncEmailWithTemplate() {
    emailDataNotificationService.sendAsync(nonSystemEmailTemplateData);
    verify(queue).send(nonSystemEmailTemplateData);
  }

  /**
   * Send async with body.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void sendAsyncWithBody() {
    emailDataNotificationService.sendAsync(nonSystemEmailBodyData);
    verify(queue).send(nonSystemEmailBodyData);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testEmailAlerts() {
    doThrow(new WingsException(ErrorCode.EMAIL_FAILED))
        .when(mailer)
        .send(any(SmtpConfig.class), any(List.class), any(software.wings.helpers.ext.mail.EmailData.class));
    emailDataNotificationService.send(testEmailTemplateData);
    String errorMessage = emailUtils.getErrorString(testEmailTemplateData.toDTO());
    verify(alertService)
        .openAlert(testEmailTemplateData.getAccountId(), GLOBAL_APP_ID, AlertType.EMAIL_NOT_SENT_ALERT,
            EmailSendingFailedAlert.builder().emailAlertData(errorMessage).build());
    verify(mailer).send(mainConfiguration.getSmtpConfig(), Collections.emptyList(), testEmailTemplateData.toDTO());
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testDefaultToMainConfiguration() {
    when(settingsService.getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.SMTP.name()))
        .thenReturn(newArrayList());
    emailDataNotificationService.send(nonSystemEmailBodyData);
    verify(mailer).send(mainConfiguration.getSmtpConfig(), Collections.emptyList(), nonSystemEmailBodyData.toDTO());
    verifyNoMoreInteractions(delegateService, queue);
    verify(alertService).closeAlertsOfType(ACCOUNT_ID, GLOBAL_APP_ID, AlertType.EMAIL_NOT_SENT_ALERT);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testInvalidSmtpConfiguration() {
    when(settingsService.getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.SMTP.name()))
        .thenReturn(newArrayList());
    when(mainConfiguration.getSmtpConfig()).thenReturn(null);
    emailDataNotificationService.send(nonSystemEmailBodyData);
    String errorMessage = emailUtils.getErrorString(nonSystemEmailBodyData.toDTO());
    verify(alertService)
        .openAlert(ACCOUNT_ID, GLOBAL_APP_ID, AlertType.EMAIL_NOT_SENT_ALERT,
            EmailSendingFailedAlert.builder().emailAlertData(errorMessage).build());
    verifyNoMoreInteractions(mailer, delegateService, queue);
  }
}
