/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.senders;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.notification.constant.NotificationClientConstants.HARNESS_NAME;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.notification.SmtpConfig;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class MailSenderImpl {
  public NotificationProcessingResponse send(
      List<String> emailIds, String subject, String body, String notificationId, SmtpConfig smtpConfig) {
    try {
      if (Objects.isNull(stripToNull(body))) {
        log.error("No email body available. Aborting notification request {}", notificationId);
        return NotificationProcessingResponse.trivialResponseWithNoRetries;
      }

      Email email = new HtmlEmail();
      email.setHostName(smtpConfig.getHost());
      email.setSmtpPort(smtpConfig.getPort());

      if (!isEmpty(smtpConfig.getPassword())) {
        email.setAuthenticator(
            new DefaultAuthenticator(smtpConfig.getUsername(), new String(smtpConfig.getPassword())));
      }
      email.setSSLOnConnect(smtpConfig.isUseSSL());
      if (smtpConfig.isUseSSL()) {
        email.setSslSmtpPort(Integer.toString(smtpConfig.getPort()));
      }

      try {
        email.setReplyTo(ImmutableList.of(new InternetAddress(smtpConfig.getFromAddress())));
      } catch (AddressException | EmailException e) {
        log.error(ExceptionUtils.getMessage(e), e);
      }
      email.setFrom(smtpConfig.getFromAddress(), HARNESS_NAME);

      for (String emailId : emailIds) {
        email.addTo(emailId);
      }

      email.setSubject(subject);
      ((HtmlEmail) email).setHtmlMsg(body);
      email.send();
    } catch (EmailException e) {
      log.error("Failed to send email. Check SMTP configuration. notificationId: {}\n{}", notificationId,
          ExceptionUtils.getMessage(e));
      return NotificationProcessingResponse.nonSent(emailIds.size());
    }
    return NotificationProcessingResponse.allSent(emailIds.size());
  }
}
