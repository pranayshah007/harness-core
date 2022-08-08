/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.external.comm.handlers;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.concurrent.HTimeLimiter;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.helpers.ext.external.comm.CollaborationHandler;
import software.wings.helpers.ext.external.comm.CollaborationProviderRequest;
import software.wings.helpers.ext.external.comm.CollaborationProviderResponse;
import software.wings.helpers.ext.external.comm.EmailRequest;
import software.wings.helpers.ext.mail.Mailer;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.EmailUtils;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;

@Singleton
@Slf4j
public class EmailHandler implements CollaborationHandler {
  @Inject private Mailer mailer;
  @Inject private EmailUtils emailHelperUtil;
  @Inject private KryoSerializer kryoSerializer;

  @Inject @Transient private transient EncryptionService encryptionService;
  private static final TimeLimiter timeLimiter = HTimeLimiter.create();

  @Override
  public CollaborationProviderResponse handle(CollaborationProviderRequest request) {
    EmailRequest emailRequest = (EmailRequest) request;
    try {
      mailer.send(emailRequest.getSmtpConfig(), emailRequest.getEncryptionDetails(), emailRequest.getEmailData());
      return CollaborationProviderResponse.builder()
          .accountId(emailRequest.getEmailData().getAccountId())
          .status(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      String errorString = emailHelperUtil.getErrorString(emailRequest.getEmailData());
      log.error(errorString, e);
      return CollaborationProviderResponse.builder()
          .status(CommandExecutionStatus.FAILURE)
          .errorMessage(errorString)
          .accountId(emailRequest.getEmailData().getAccountId())
          .build();
    }
  }

  @Override
  public boolean validateDelegateConnection(SmtpConfig smtpConfig, List<EncryptedDataDetail> encryptionDetails) {
    return validateDelegateConnectionInternal(smtpConfig, encryptionDetails);
  }

  @Override
  public boolean validateDelegateConnection(CollaborationProviderRequest request) {
    EmailRequest emailRequest = (EmailRequest) request;
    SmtpConfig smtpConfig = emailRequest.getSmtpConfig();
    List<EncryptedDataDetail> encryptionDetails = emailRequest.getEncryptionDetails();
    return validateDelegateConnectionInternal(smtpConfig, encryptionDetails);
  }

  private boolean validateDelegateConnectionInternal(
      SmtpConfig smtpConfig, List<EncryptedDataDetail> encryptionDetails) {
    try {
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(10), () -> {
        boolean result = false;
        try {
          Properties props = new Properties();
          if (isNotEmpty(smtpConfig.getPassword())) {
            props.setProperty("mail.smtp.auth", "true");
          }
          SmtpConfig config = kryoSerializer.clone(smtpConfig);
          encryptionService.decrypt(config, encryptionDetails, false);
          if (config.isUseSSL()) {
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.starttls.enable", "true");
          }
          if (config.isStartTLS()) {
            props.put("mail.smtp.starttls.enable", "true");
          }
          Session session = Session.getInstance(props, null);
          Transport transport = session.getTransport("smtp");
          transport.connect(config.getHost(), config.getPort(), config.getUsername(),
              isNotEmpty(config.getPassword()) ? new String(config.getPassword()) : null);
          transport.close();
          result = true;

          log.info("Validated email delegate communication to {}.", transport.toString());
        } catch (AuthenticationFailedException e) {
          log.warn("SMTP: Authentication Failed", e);

        } catch (MessagingException e) {
          log.warn("SMTP: Messaging Exception Occurred", e);
        } catch (Exception e) {
          log.warn("SMTP: Unknown Exception", e);
        }
        return result;
      });
    } catch (Exception e) {
      log.warn("Failed to validate email delegate communication", e);
    }
    return false;
  }
}
