/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.notifications;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.common.NotificationConstants.ABORTED_COLOR;
import static software.wings.common.NotificationConstants.COMPLETED_COLOR;
import static software.wings.common.NotificationConstants.FAILED_COLOR;
import static software.wings.common.NotificationConstants.PAUSED_COLOR;
import static software.wings.common.NotificationConstants.RESUMED_COLOR;
import static software.wings.common.NotificationMessageResolver.getDecoratedNotificationMessage;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.Notification;
import software.wings.common.NotificationMessageResolver;
import software.wings.common.NotificationMessageResolver.ChannelTemplate.EmailTemplate;
import software.wings.persistence.mail.EmailData;
import software.wings.service.intfc.EmailNotificationService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class EmailDispatcher {
  private static final String LINK_COLOR = "#1A89BF";

  @Inject private NotificationMessageResolver notificationMessageResolver;
  @Inject private EmailNotificationService emailNotificationService;

  public void dispatch(List<Notification> notifications, List<String> toAddress) {
    if (isEmpty(toAddress)) {
      return;
    }

    List<String> validToAddresses = new ArrayList<>();
    for (String emailAddress : toAddress) {
      if (isNotBlank(emailAddress)) {
        validToAddresses.add(emailAddress);
      }
    }

    if (isEmpty(validToAddresses)) {
      log.info("No valid email addresses in: {}", toAddress);
      return;
    }

    List<String> emailBodyList = new ArrayList<>();
    List<String> emailSubjectList = new ArrayList<>();
    notifications.forEach(notification -> {
      EmailTemplate emailTemplate =
          notificationMessageResolver.getEmailTemplate(notification.getNotificationTemplateId());
      if (emailTemplate == null) {
        log.error("No email template found for templateId {}", notification.getNotificationTemplateId());
        return;
      }
      emailBodyList.add(
          getDecoratedNotificationMessage(emailTemplate.getBody(), notification.getNotificationTemplateVariables()));
      emailSubjectList.add(
          getDecoratedNotificationMessage(emailTemplate.getSubject(), notification.getNotificationTemplateVariables()));
    });

    if (emailBodyList.isEmpty() || emailSubjectList.isEmpty()) {
      log.info("Email body/subject is empty. destination-emails: {}", toAddress);
      return;
    }

    String body = processEmailHtml(String.join("<br>", emailBodyList));
    String subject = emailSubjectList.get(emailSubjectList.size() - 1);
    Optional<String> accountIdOptional = notifications.stream()
                                             .filter(Objects::nonNull)
                                             .map(Notification::getAccountId)
                                             .filter(StringUtils::isNotBlank)
                                             .findFirst();

    EmailData emailData = EmailData.builder().to(validToAddresses).subject(subject).body(body).build();
    if (accountIdOptional.isPresent()) {
      emailData.setAccountId(accountIdOptional.get());
    }
    emailData.setRetries(2);
    emailData.setCc(Collections.emptyList());
    log.info("Trying to send email to: {}", validToAddresses);
    emailNotificationService.sendAsync(emailData);
  }

  public static String processEmailHtml(String text) {
    return text.replaceAll("<<<", "<b><<a>>href=\"")
        .replaceAll("\\|-\\|", "\" target=\"_blank\">")
        .replaceAll(">>>", "</a></b>")
        .replaceAll("<<a>>", "<a style=\"text-decoration:none; color:" + LINK_COLOR + ";\" ")
        .replaceAll("<<top-div>>", "<div style=\"margin-top:12px; margin-left:14px\">")
        .replaceAll("<<bottom-div>>",
            "<div style=\"margin:15px; padding-left:7px; "
                + "border-left-width:3px; border-radius:3px; border-left-style:solid; font-size:small; border-left-color:")
        .replaceAll("<<completed-color>>", COMPLETED_COLOR + ";\">")
        .replaceAll("<<failed-color>>", FAILED_COLOR + ";\">")
        .replaceAll("<<paused-color>>", PAUSED_COLOR + ";\">")
        .replaceAll("<<resumed-color>>", RESUMED_COLOR + ";\">")
        .replaceAll("<<aborted-color>>", ABORTED_COLOR + ";\">")
        .replaceAll("<<rejected-color>>", FAILED_COLOR + ";\">")
        .replaceAll("<<expired-color>>", FAILED_COLOR + ";\">")
        .replaceAll("\\*Application:\\*", "<b>Application:</b>")
        .replaceAll("\\*Services:\\*", "<b>Services:</b>")
        .replaceAll("\\*Artifacts:\\*", "<b>Artifacts:</b>")
        .replaceAll("\\*Environment:\\*", "<b>Environment:</b>")
        .replaceAll("\\*Infrastructure Definitions:\\*", "<b>Infrastructure Definitions:</b>")
        .replaceAll("\\*TriggeredBy:\\*", "<b>TriggeredBy:</b>")
        .replaceAll("<<img-path>>", "<div><span><img src=\"https://s3.amazonaws.com/wings-assets/slackicons/")
        .replaceAll("<<img-suffix>>",
            ".png\" height=\"13\" width=\"13\" style=\"padding-right:5px; padding-top:5px;\"></span>"
                + "<span style=\"color:gray; display:inline-block; vertical-align:top; margin-top:4px;\">");
  }
}
