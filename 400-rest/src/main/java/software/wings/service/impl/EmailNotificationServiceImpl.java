/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.microservice.NotifyEngineTarget.GENERAL;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.queue.QueuePublisher;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.app.MainConfiguration;
import software.wings.beans.TaskType;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.EmailSendingFailedAlert;
import software.wings.helpers.ext.external.comm.EmailRequest;
import software.wings.helpers.ext.mail.Mailer;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.persistence.mail.EmailData;
import software.wings.service.impl.email.EmailNotificationCallBack;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.EmailHelperUtils;
import software.wings.utils.EmailUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by peeyushaggarwal on 5/23/16.
 */
@Singleton
@Slf4j
public class EmailNotificationServiceImpl implements EmailNotificationService {
  @Inject private Mailer mailer;
  @Inject private QueuePublisher<EmailData> emailEventQueue;
  @Inject private MainConfiguration mainConfiguration;
  @Inject private SecretManager secretManager;
  @Inject private DelegateService delegateService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private EmailHelperUtils emailHelperUtils;
  @Inject private EmailUtils emailUtils;
  @Inject private AlertService alertService;

  private static final String CE_MAIL_FROM_ADDRESS = "ce-noreply@harness.io";
  private static final int TIMEOUT_DURATION = 10;
  private static final String SMTP_SETUP_ERROR =
      "Unable to send reset password mail as SMTP server is not configured. Please setup SMTP server or contact your admin";

  @Override
  public boolean send(EmailData emailData) {
    return sendCeMail(emailData, false);
  }

  private boolean sendMail(SmtpConfig config, software.wings.helpers.ext.mail.EmailData emailData, boolean isCeMail) {
    List<EncryptedDataDetail> encryptionDetails = config.equals(mainConfiguration.getSmtpConfig())
        ? Collections.emptyList()
        : secretManager.getEncryptionDetails(config, emailData.getAppId(), emailData.getWorkflowExecutionId());

    if (config.equals(mainConfiguration.getSmtpConfig())) {
      try {
        if (isCeMail) {
          config.setFromAddress(CE_MAIL_FROM_ADDRESS);
        }
        mailer.send(config, encryptionDetails, emailData);
        closeEmailNotSentAlert(emailData);
        return true;
      } catch (WingsException e) {
        String errorString = emailUtils.getErrorString(emailData);
        log.warn(errorString, e);
        return false;
      }
    } else {
      return sendEmailAsDelegateTask(config, encryptionDetails, emailData);
    }
  }

  private void sendEmailNotSentAlert(software.wings.helpers.ext.mail.EmailData emailData) {
    String errorString = emailUtils.getErrorString(emailData);
    alertService.openAlert(emailData.getAccountId(), GLOBAL_APP_ID, AlertType.EMAIL_NOT_SENT_ALERT,
        EmailSendingFailedAlert.builder().emailAlertData(errorString).build());
  }

  private void closeEmailNotSentAlert(software.wings.helpers.ext.mail.EmailData emailData) {
    alertService.closeAlertsOfType(emailData.getAccountId(), GLOBAL_APP_ID, AlertType.EMAIL_NOT_SENT_ALERT);
  }

  private boolean sendEmailAsDelegateTask(SmtpConfig config, List<EncryptedDataDetail> encryptionDetails,
      software.wings.helpers.ext.mail.EmailData emailData) {
    String waitId = generateUuid();
    try {
      EmailRequest request =
          EmailRequest.builder().emailData(emailData).encryptionDetails(encryptionDetails).smtpConfig(config).build();
      DelegateTask delegateTask = DelegateTask.builder()
                                      .accountId(emailData.getAccountId())
                                      .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
                                      .waitId(waitId)
                                      .data(TaskData.builder()
                                                .async(true)
                                                .taskType(TaskType.COLLABORATION_PROVIDER_TASK.name())
                                                .parameters(new Object[] {request})
                                                .timeout(TimeUnit.MINUTES.toMillis(TIMEOUT_DURATION))
                                                .build())
                                      .build();
      waitNotifyEngine.waitForAllOn(GENERAL, new EmailNotificationCallBack(), waitId);
      delegateService.queueTaskV2(delegateTask);
      return true;
    } catch (Exception e) {
      String errorString = emailUtils.getErrorString(emailData);
      log.warn(errorString, e);
      return false;
    }
  }

  @Override
  public void sendAsync(EmailData emailData) {
    emailEventQueue.send(emailData);
  }

  @Override
  public boolean sendCeMail(EmailData emailData, boolean isCeMail) {
    SmtpConfig defaultSMTPConfig;
    SmtpConfig fallBackSMTPConfig;

    if (emailData.isSystem()) {
      defaultSMTPConfig = mainConfiguration.getSmtpConfig();
      fallBackSMTPConfig = emailHelperUtils.getSmtpConfig(emailData.getAccountId(), false);
    } else {
      defaultSMTPConfig = emailHelperUtils.getSmtpConfig(emailData.getAccountId(), false);
      fallBackSMTPConfig = mainConfiguration.getSmtpConfig();
    }

    boolean isDefaultSMTPConfigValid = emailHelperUtils.isSmtpConfigValid(defaultSMTPConfig);
    boolean isFallBackSMTPConfigValid = emailHelperUtils.isSmtpConfigValid(fallBackSMTPConfig);

    boolean mailSentSuccessFully = false;
    software.wings.helpers.ext.mail.EmailData emailDataDto = emailData.toDTO();
    if (!isDefaultSMTPConfigValid && !isFallBackSMTPConfigValid) {
      sendEmailNotSentAlert(emailDataDto);
    } else if (isCeMail && !isDefaultSMTPConfigValid) {
      sendEmailNotSentAlert(emailDataDto);
      throw new InvalidRequestException(SMTP_SETUP_ERROR);
    } else if (isDefaultSMTPConfigValid) {
      mailSentSuccessFully = true;
      if (!sendMail(defaultSMTPConfig, emailDataDto, isCeMail)
          && (!isFallBackSMTPConfigValid || !sendMail(fallBackSMTPConfig, emailDataDto, isCeMail))) {
        sendEmailNotSentAlert(emailDataDto);
        mailSentSuccessFully = false;
      }
    } else if (isFallBackSMTPConfigValid) {
      mailSentSuccessFully = sendMail(fallBackSMTPConfig, emailDataDto, isCeMail);
    }

    return mailSentSuccessFully;
  }
}
