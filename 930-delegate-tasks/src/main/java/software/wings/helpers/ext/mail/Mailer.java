/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.mail;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static freemarker.template.Configuration.VERSION_2_3_23;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.SimpleEmail;

/**
 * Created by peeyushaggarwal on 5/20/16.
 */
@Slf4j
public class Mailer {
  private final Configuration cfg = new Configuration(VERSION_2_3_23);
  private static final String HARNESS_NAME = "Harness";

  @Inject private EncryptionService encryptionService;
  /**
   * Instantiates a new mailer.
   */
  public Mailer() {
    cfg.setTemplateLoader(new ClassTemplateLoader(getClass(), "/mailtemplates"));
  }

  /**
   * Send.
   *
   * @param smtpConfig the smtp config
   * @param emailData  the email data
   * @throws EmailException    the email exception
   * @throws IOException       Signals that an I/O exception has occurred.
   * @throws TemplateException the template exception
   */
  public void send(SmtpConfig smtpConfig, List<EncryptedDataDetail> encryptedDataDetails, EmailData emailData) {
    try {
      encryptionService.decrypt(smtpConfig, encryptedDataDetails, false);
      Email email = emailData.isHasHtml() ? new HtmlEmail() : new SimpleEmail();
      email.setHostName(smtpConfig.getHost());
      email.setSmtpPort(smtpConfig.getPort());
      if (isNotEmpty(smtpConfig.getPassword())) {
        email.setAuthenticator(
            new DefaultAuthenticator(smtpConfig.getUsername(), new String(smtpConfig.getPassword())));
      }
      email.setSSLOnConnect(smtpConfig.isUseSSL());
      email.setStartTLSEnabled(smtpConfig.isStartTLS());
      if (smtpConfig.isUseSSL()) {
        email.setSslSmtpPort(Integer.toString(smtpConfig.getPort()));
      }

      try {
        email.setReplyTo(ImmutableList.of(new InternetAddress(smtpConfig.getFromAddress())));
      } catch (AddressException e) {
        log.error(ExceptionUtils.getMessage(e), e);
      }
      email.setFrom(smtpConfig.getFromAddress(), HARNESS_NAME);

      for (String to : emailData.getTo()) {
        email.addTo(to);
      }

      if (emailData.getCc() != null) {
        for (String cc : emailData.getCc()) {
          email.addCc(cc);
        }
      }

      if (emailData.getBcc() != null) {
        for (String bcc : emailData.getBcc()) {
          email.addBcc(bcc);
        }
      }

      String subject = emailData.getSubject();
      String body = emailData.getBody();
      if (isNotBlank(emailData.getTemplateName())) {
        Template subjectTemplate = cfg.getTemplate(emailData.getTemplateName() + "-subject.ftl");
        Template bodyTemplate = cfg.getTemplate(emailData.getTemplateName() + "-body.ftl");

        StringWriter subjectWriter = new StringWriter();
        subjectTemplate.process(emailData.getTemplateModel(), subjectWriter);

        subject = subjectWriter.toString();

        StringWriter bodyWriter = new StringWriter();
        bodyTemplate.process(emailData.getTemplateModel(), bodyWriter);

        body = bodyWriter.toString();
      }

      email.setSubject(subject);
      if (emailData.isHasHtml()) {
        ((HtmlEmail) email).setHtmlMsg(body);
      } else {
        email.setMsg(body);
      }

      email.send();
      log.info("Successfully sent an email with subject '{}' to user {} through mail server {}:{}", email.getSubject(),
          emailData.getTo(), email.getHostName(), email.getSmtpPort());
    } catch (EmailException | IOException e) {
      log.warn("Failed to send email. Reason: " + ExceptionUtils.getMessage(e));
      throw new WingsException(ErrorCode.EMAIL_FAILED, e);
    } catch (TemplateException e) {
      log.warn("Failed to parse email template . Reason: " + ExceptionUtils.getMessage(e));
      throw new WingsException(ErrorCode.EMAIL_FAILED, e);
    }
  }
}
