/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.notification.task;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.MailTaskParams;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.notification.SmtpConfig;
import io.harness.notification.senders.MailSenderImpl;

import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class MailSenderDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private MailSenderImpl mailSender;
  @Inject private EncryptionService encryptionService;
  public MailSenderDelegateTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    MailTaskParams mailTaskParams = (MailTaskParams) parameters;
    SmtpConfig notificationSmtpConfig = mailTaskParams.getSmtpConfig();
    software.wings.helpers.ext.mail.SmtpConfig restSmtpConfig =
        software.wings.helpers.ext.mail.SmtpConfig.builder()
            .host(notificationSmtpConfig.getHost())
            .port(notificationSmtpConfig.getPort())
            .fromAddress(notificationSmtpConfig.getFromAddress())
            .useSSL(notificationSmtpConfig.isUseSSL())
            .username(notificationSmtpConfig.getUsername())
            .password(notificationSmtpConfig.getPassword())
            .encryptedPassword(notificationSmtpConfig.getEncryptedPassword())
            .build();

    encryptionService.decrypt(restSmtpConfig, mailTaskParams.getEncryptionDetails(), false);
    notificationSmtpConfig.setPassword(restSmtpConfig.getPassword());
    try {
      NotificationProcessingResponse processingResponse =
          mailSender.send(mailTaskParams.getEmailIds(), mailTaskParams.getSubject(), mailTaskParams.getBody(),
              mailTaskParams.getNotificationId(), notificationSmtpConfig);
      return NotificationTaskResponse.builder().processingResponse(processingResponse).build();
    } catch (Exception e) {
      return NotificationTaskResponse.builder()
          .processingResponse(NotificationProcessingResponse.trivialResponseWithNoRetries)
          .errorMessage(e.getMessage())
          .build();
    }
  }
}
