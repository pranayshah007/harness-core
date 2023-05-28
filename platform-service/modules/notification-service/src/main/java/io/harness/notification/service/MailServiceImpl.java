/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.exception.WingsException.USER;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static freemarker.template.Configuration.VERSION_2_3_23;
import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.MailTaskParams;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationRequest;
import io.harness.notification.SmtpConfig;
import io.harness.notification.Team;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.SmtpConfigResponse;
import io.harness.notification.remote.dto.EmailDTO;
import io.harness.notification.remote.dto.EmailSettingDTO;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.senders.MailSenderImpl;
import io.harness.notification.service.api.ChannelService;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.serializer.YamlUtils;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.userng.remote.UserNGClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import freemarker.core.InvalidReferenceException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class MailServiceImpl implements ChannelService {
  public static final String DEFAULT_SUBJECT_BODY = "Test Notification from Harness";
  private final Configuration cfg = new Configuration(VERSION_2_3_23);
  private final NotificationSettingsService notificationSettingsService;
  private final NotificationTemplateService notificationTemplateService;
  private final YamlUtils yamlUtils;
  private final SmtpConfig smtpConfigDefault;
  private final MailSenderImpl mailSender;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private UserNGClient userNGClient;

  @Override
  public NotificationProcessingResponse send(NotificationRequest notificationRequest) {
    if (Objects.isNull(notificationRequest) || !notificationRequest.hasEmail()) {
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    String notificationId = notificationRequest.getId();
    NotificationRequest.Email mailDetails = notificationRequest.getEmail();
    Map<String, String> templateData = mailDetails.getTemplateDataMap();
    String templateId = mailDetails.getTemplateId();

    if (Objects.isNull(stripToNull(templateId))) {
      log.info("template Id is null for notification request {}", notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    List<String> emailIds = resolveRecipients(notificationRequest);
    if (isEmpty(emailIds)) {
      log.info("No recipients found in notification request {}", notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    try {
      String subject;
      String body;
      Optional<EmailTemplate> emailTemplateOpt = getTemplate(templateId, notificationRequest.getTeam());
      if (!emailTemplateOpt.isPresent()) {
        log.error(
            "Failed to send email for notification request {} possibly due to no valid template with name {} found",
            notificationId, templateId);
        return NotificationProcessingResponse.trivialResponseWithNoRetries;
      }
      EmailTemplate emailTemplate = emailTemplateOpt.get();

      subject = processTemplate(templateId + "-subject", emailTemplate.getSubject(), templateData);
      body = processTemplate(templateId + "-body", emailTemplate.getBody(), templateData);
      return send(emailIds, subject, body, notificationId, notificationRequest.getAccountId());
    } catch (Exception e) {
      log.error("Failed to send email. Check template details for notificationId: {}\n{}", notificationId,
          ExceptionUtils.getMessage(e));
      return NotificationProcessingResponse.trivialResponseWithRetries;
    }
  }

  @Override
  public boolean sendTestNotification(NotificationSettingDTO notificationSettingDTO) {
    EmailSettingDTO emailSettingDTO = (EmailSettingDTO) notificationSettingDTO;
    String email = emailSettingDTO.getRecipient();
    if (Objects.isNull(stripToNull(email)) || !EmailValidator.getInstance().isValid(email)
        || Objects.isNull(emailSettingDTO.getAccountId())) {
      throw new NotificationException(
          String.format("Invalid email encountered while processing Test Connection request %s.",
              notificationSettingDTO.getNotificationId()),
          DEFAULT_ERROR_CODE, USER);
    }
    NotificationTaskResponse response = sendInSync(Collections.singletonList(email), Collections.emptyList(),
        DEFAULT_SUBJECT_BODY, DEFAULT_SUBJECT_BODY, email, notificationSettingDTO.getAccountId());
    if (response.getProcessingResponse() == null || response.getProcessingResponse().getResult().isEmpty()
        || NotificationProcessingResponse.isNotificationRequestFailed(response.getProcessingResponse())) {
      throw new NotificationException("Failed to send email. Check SMTP configuration.", DEFAULT_ERROR_CODE, USER);
    }
    return true;
  }

  private boolean isSmtpConfigProvided(String accountId) {
    return Objects.nonNull(notificationSettingsService.getSmtpConfigResponse(accountId));
  }

  public NotificationTaskResponse sendEmail(EmailDTO emailDTO) {
    // Enable only if SMTP configuration is provided by the user; as we don't want to spam via Harness email
    if (emailDTO.isSendToNonHarnessRecipients() && !isSmtpConfigProvided(emailDTO.getAccountId())) {
      emailDTO.setSendToNonHarnessRecipients(false);
    }

    List<String> emails = new ArrayList<>(emailDTO.getToRecipients());
    List<String> ccEmails = new ArrayList<>(emailDTO.getCcRecipients());
    String accountId = emailDTO.getAccountId();
    if (Objects.isNull(accountId)) {
      throw new NotificationException(
          String.format("No account id encountered for %s.", emailDTO.getNotificationId()), DEFAULT_ERROR_CODE, USER);
    }
    validateEmptyEmails(emailDTO, emails, ccEmails, "");
    String errorMessage = validateEmails(emails, ccEmails, accountId, emailDTO.isSendToNonHarnessRecipients());
    validateEmptyEmails(emailDTO, emails, ccEmails, errorMessage);
    NotificationTaskResponse response = sendInSync(
        emails, ccEmails, emailDTO.getSubject(), emailDTO.getBody(), emailDTO.getNotificationId(), accountId);
    if (response.getProcessingResponse() == null || response.getProcessingResponse().getResult().isEmpty()
        || NotificationProcessingResponse.isNotificationRequestFailed(response.getProcessingResponse())) {
      throw new NotificationException("Failed to send email. Check SMTP configuration.", DEFAULT_ERROR_CODE, USER);
    }
    if (StringUtils.isNotEmpty(errorMessage)) {
      response.setErrorMessage(errorMessage);
    }
    return response;
  }

  private void validateEmptyEmails(EmailDTO emailDTO, List<String> emails, List<String> ccEmails, String errorMessage) {
    if (emails.isEmpty() && ccEmails.isEmpty()) {
      String emptyEmailMessage = String.format("No email id encountered");
      if (StringUtils.isNotEmpty(errorMessage)) {
        emptyEmailMessage = errorMessage + " " + emptyEmailMessage;
      }
      throw new NotificationException(emptyEmailMessage, DEFAULT_ERROR_CODE, USER);
    }
  }

  private String validateEmails(
      List<String> emails, List<String> ccEmails, String accountId, boolean sendToNonHarnessRecipients) {
    String errorMessage = "";
    Set<String> invalidEmails = getInvalidEmails(emails);
    invalidEmails.addAll(getInvalidEmails(ccEmails));
    if (!invalidEmails.isEmpty()) {
      emails.removeAll(invalidEmails);
      ccEmails.removeAll(invalidEmails);
      errorMessage =
          errorMessage.concat(String.format("Emails %s are invalid.", StringUtils.join(invalidEmails, ", ")));
    }
    Set<String> notPresentEmails = getAbsentEmails(emails, accountId, sendToNonHarnessRecipients);
    notPresentEmails.addAll(getAbsentEmails(ccEmails, accountId, sendToNonHarnessRecipients));
    if (!notPresentEmails.isEmpty()) {
      emails.removeAll(notPresentEmails);
      ccEmails.removeAll(notPresentEmails);
      if (StringUtils.isNotEmpty(errorMessage)) {
        errorMessage = errorMessage.concat(" ");
      }
      errorMessage = errorMessage.concat(String.format(
          "Emails %s are not present in account, Emails can be sent only to email addresses of Harness users in the account.",
          StringUtils.join(notPresentEmails, ", ")));
    }
    return errorMessage;
  }

  private Set<String> getAbsentEmails(List<String> emails, String accountId, boolean sendToNonHarnessRecipients) {
    return emails.stream()
        .filter(email -> !sendToNonHarnessRecipients && !getResponse(userNGClient.isEmailIdInAccount(email, accountId)))
        .collect(Collectors.toSet());
  }

  private Set<String> getInvalidEmails(List<String> emails) {
    return emails.stream().filter(email -> !EmailValidator.getInstance().isValid(email)).collect(Collectors.toSet());
  }

  private NotificationProcessingResponse send(
      List<String> emailIds, String subject, String body, String notificationId, String accountId) {
    NotificationProcessingResponse notificationProcessingResponse;
    SmtpConfigResponse smtpConfigResponse = notificationSettingsService.getSmtpConfigResponse(accountId);
    if (Objects.nonNull(smtpConfigResponse) && Objects.nonNull(smtpConfigResponse.getSmtpConfig())) {
      Set<String> taskSelectors = getDelegateSelectors(smtpConfigResponse.getSmtpConfig());
      DelegateTaskRequest delegateTaskRequest =
          DelegateTaskRequest.builder()
              .accountId(accountId)
              .taskType("NOTIFY_MAIL")
              .taskParameters(MailTaskParams.builder()
                                  .notificationId(notificationId)
                                  .subject(subject)
                                  .body(body)
                                  .ccEmailIds(Collections.emptyList())
                                  .emailIds(emailIds)
                                  .smtpConfig(smtpConfigResponse.getSmtpConfig())
                                  .encryptionDetails(smtpConfigResponse.getEncryptionDetails())
                                  .build())
              .taskSelectors(taskSelectors)
              .executionTimeout(Duration.ofMinutes(1L))
              .build();
      String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
      log.info("Async delegate task created with taskID {}.", taskId);
      notificationProcessingResponse = NotificationProcessingResponse.allSent(emailIds.size());
    } else {
      notificationProcessingResponse =
          mailSender.send(emailIds, Collections.emptyList(), subject, body, notificationId, smtpConfigDefault);
    }
    log.info(NotificationProcessingResponse.isNotificationRequestFailed(notificationProcessingResponse)
            ? "Failed to send notification for request {}."
            : "Notification request {} sent.",
        notificationId);
    return notificationProcessingResponse;
  }

  private NotificationTaskResponse sendInSync(List<String> emailIds, List<String> ccEmailIds, String subject,
      String body, String notificationId, String accountId) {
    NotificationTaskResponse notificationTaskResponse;
    NotificationProcessingResponse notificationProcessingResponse;
    SmtpConfigResponse smtpConfigResponse = notificationSettingsService.getSmtpConfigResponse(accountId);
    if (Objects.nonNull(smtpConfigResponse) && Objects.nonNull(smtpConfigResponse.getSmtpConfig())) {
      final Map<String, String> ngTaskSetupAbstractionsWithOwner =
          getNGTaskSetupAbstractionsWithOwner(accountId, null, null);
      Set<String> taskSelectors = getDelegateSelectors(smtpConfigResponse.getSmtpConfig());
      DelegateTaskRequest delegateTaskRequest =
          DelegateTaskRequest.builder()
              .accountId(accountId)
              .taskType("NOTIFY_MAIL")
              .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
              .taskParameters(MailTaskParams.builder()
                                  .notificationId(notificationId)
                                  .subject(subject)
                                  .body(body)
                                  .emailIds(emailIds)
                                  .ccEmailIds(ccEmailIds)
                                  .smtpConfig(smtpConfigResponse.getSmtpConfig())
                                  .encryptionDetails(smtpConfigResponse.getEncryptionDetails())
                                  .build())
              .taskSelectors(taskSelectors)
              .executionTimeout(Duration.ofMinutes(1L))
              .build();
      DelegateResponseData responseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
      if (responseData instanceof ErrorNotifyResponseData) {
        throw new NotificationException("Failed to send email. Check SMTP configuration.", DEFAULT_ERROR_CODE, USER);
      } else {
        notificationTaskResponse = (NotificationTaskResponse) responseData;
      }
    } else {
      notificationProcessingResponse =
          mailSender.send(emailIds, ccEmailIds, subject, body, notificationId, smtpConfigDefault);
      notificationTaskResponse =
          NotificationTaskResponse.builder().processingResponse(notificationProcessingResponse).build();
    }
    log.info(
        NotificationProcessingResponse.isNotificationRequestFailed(notificationTaskResponse.getProcessingResponse())
            ? "Failed to send notification for request {}."
            : "Notification request {} sent.",
        notificationId);
    return notificationTaskResponse;
  }

  private String processTemplate(String templateName, String templateStr, Map<String, String> templateData) {
    if (Objects.isNull(stripToNull(templateStr))) {
      return null;
    }
    Template template = null;
    try {
      template = new Template(templateName, templateStr, cfg);
    } catch (IOException e) {
      log.error("Can't parse the stored file as .ftl template file", e);
    }

    if (Objects.isNull(template)) {
      return null;
    }

    Map<String, String> dataMap = new HashMap<>(templateData);
    boolean exceptionCaught;
    StringWriter strWriter;
    do {
      strWriter = new StringWriter();
      exceptionCaught = false;
      try {
        template.process(dataMap, strWriter);
      } catch (InvalidReferenceException e) {
        exceptionCaught = true;
        dataMap.put(e.getBlamedExpressionString(), "");
      } catch (IOException | TemplateException e) {
        log.error("Failed to process template. Check template {}", templateName);
      }
    } while (exceptionCaught);

    return strWriter.toString();
  }

  private Optional<EmailTemplate> getTemplate(String templateId, Team team) {
    Optional<String> templateStrOptional = notificationTemplateService.getTemplateAsString(templateId, team);
    Optional<EmailTemplate> emailTemplateOpt = Optional.empty();
    if (templateStrOptional.isPresent()) {
      try {
        emailTemplateOpt =
            Optional.of(yamlUtils.read(templateStrOptional.get(), new TypeReference<EmailTemplate>() {}));
      } catch (IOException e) {
        log.error("failed to parse template {} into yaml. Please check the format", templateId);
      }
    }
    return emailTemplateOpt;
  }

  private List<String> resolveRecipients(NotificationRequest notificationRequest) {
    NotificationRequest.Email emailDetails = notificationRequest.getEmail();
    List<String> recipients = new ArrayList<>(emailDetails.getEmailIdsList());
    if (isNotEmpty(emailDetails.getUserGroupList())) {
      List<String> resolvedRecipients = notificationSettingsService.getNotificationRequestForUserGroups(
          emailDetails.getUserGroupList(), NotificationChannelType.EMAIL, notificationRequest.getAccountId(), 0L);
      recipients.addAll(resolvedRecipients);
    }
    return recipients.stream().distinct().collect(Collectors.toList());
  }

  private Set<String> getDelegateSelectors(SmtpConfig smtpConfig) {
    return isEmpty(smtpConfig.getDelegateSelectors()) ? new HashSet<>() : smtpConfig.getDelegateSelectors();
  }

  @Getter
  @Setter
  public static class EmailTemplate {
    private String subject;
    private String body;
  }
}
