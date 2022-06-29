/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.notifications;

import static software.wings.common.NotificationConstants.BLUE_COLOR;
import static software.wings.common.NotificationMessageResolver.getDecoratedNotificationMessage;

import static java.lang.String.format;
import static java.lang.String.join;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;

import io.harness.data.structure.EmptyPredicate;

import software.wings.beans.Notification;
import software.wings.common.NotificationConstants;
import software.wings.service.intfc.MicrosoftTeamsNotificationService;

import com.google.api.client.util.Charsets;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jetbrains.annotations.Nullable;

/**
 * Created by mehulkasliwal on 2020-04-17.
 */
@Singleton
@Slf4j
public class MicrosoftTeamsMessageDispatcher {
  private static final String ARTIFACTS = "ARTIFACTS";
  private static final String ASTERISK = "\\*";
  private static final String ASTERISK_REPLACEMENT = "**";
  private static final String COMMA = ",";
  private static final String ERROR = "_error";
  private static final String JSON = ".json";
  private static final String KEY_VERB = "VERB";
  private static final String KEY_ERRORS = "ERRORS";
  private static final String MICROSOFT_TEAMS_FOLDER = "/microsoftteams/";
  private static final String NA = "N / A";
  private static final String NAME = "_NAME";
  private static final String NEW_LINE = "\\n";
  private static final String NEW_LINE_REPLACEMENT = "\n\n";
  private static final String PIPELINE = "PIPELINE";
  private static final String[] schemes = {"https", "http", "rtsp", "ftp"};
  private static final List<String> TEMPLATE_KEYS_TO_BE_PROCESSED =
      ImmutableList.of("APPLICATION", ARTIFACTS, "ENVIRONMENT", PIPELINE, "SERVICE", "TRIGGER");
  private static final String THEME_COLOR = "THEME_COLOR";
  private static final String UNDERSCORE_REPLACEMENT = "\\\\\\\\\\_";
  private static final String UNDERSCORE = "_";
  private static final String URL = "_URL";

  @Inject private MicrosoftTeamsNotificationService microsoftTeamsNotificationService;

  public void dispatch(List<Notification> notifications, String microsoftTeamsWebhookUrl) {
    if (EmptyPredicate.isEmpty(notifications)) {
      return;
    }
    List<String> messages = new ArrayList<>();

    for (Notification notification : notifications) {
      if (null != notification) {
        String template = getTemplate(notification);
        if (StringUtils.isNotEmpty(template)) {
          Map<String, String> templateVariables =
              processTemplateVariables(notification.getNotificationTemplateVariables());
          messages.add(getDecoratedNotificationMessage(template, templateVariables));
        }
      }
    }
    for (String message : messages) {
      int responseCode = microsoftTeamsNotificationService.sendMessage(message, microsoftTeamsWebhookUrl);
      if (responseCode >= 200 && responseCode < 300) {
        log.info("Successfully sent message to Microsoft Teams");
      }
    }
  }

  @Nullable
  @VisibleForTesting
  String getTemplate(Notification notification) {
    String templateFileName = getTemplateFileName(notification);
    String template = getTemplateFile(templateFileName);
    if (StringUtils.isEmpty(template)) {
      log.info("No template found in file {}", templateFileName);
    }
    return template;
  }

  @VisibleForTesting
  String getTemplateFileName(Notification notification) {
    String templateId = notification.getNotificationTemplateId();
    String errors = notification.getNotificationTemplateVariables().getOrDefault(KEY_ERRORS, EMPTY);
    StringBuilder templateFile = new StringBuilder();
    templateFile.append(MICROSOFT_TEAMS_FOLDER).append(templateId.toLowerCase());
    if (StringUtils.isNotEmpty(errors)) {
      templateFile.append(ERROR);
    }
    templateFile.append(JSON);
    return templateFile.toString();
  }

  private String getTemplateFile(String templateFileName) {
    URL url = getClass().getResource(templateFileName);
    String template = EMPTY;
    try {
      template = Resources.toString(url, Charsets.UTF_8);
    } catch (Exception e) {
      log.info("Exception occurred at getTemplate() for file {}", templateFileName, e);
    }
    return template;
  }

  @VisibleForTesting
  Map<String, String> processTemplateVariables(Map<String, String> templateVariables) {
    Map<String, String> clonedTemplateVariables = new HashMap<>(templateVariables);
    clonedTemplateVariables.forEach((key, value) -> {
      String newValue = handleSpecialCharacters(key, value);
      if (newValue.isEmpty() && key.endsWith(NAME)) {
        newValue = NA;
      }
      clonedTemplateVariables.put(key, newValue);
    });
    formatTemplateUrlAndName(clonedTemplateVariables);
    String notificationStatus = templateVariables.getOrDefault(KEY_VERB, EMPTY);
    clonedTemplateVariables.put(THEME_COLOR, NotificationConstants.getThemeColor(notificationStatus, BLUE_COLOR));
    return clonedTemplateVariables;
  }

  /**
   * Microsoft Teams Message card handles underscore "_" character in message differently. We need to replace "_" with
   * "\\_" to render it properly. \n should also be converted to \n\n for proper rendering of new line
   * @param key
   * @param value
   * @return updated value
   */
  @VisibleForTesting
  String handleSpecialCharacters(String key, String value) {
    if (key.contains(URL)) {
      return value;
    }
    value = value.replaceAll(ASTERISK, ASTERISK_REPLACEMENT).replaceAll(NEW_LINE, NEW_LINE_REPLACEMENT);
    String[] parts = value.split(SPACE);
    for (int index = 0; index < parts.length; index++) {
      String formattedValue = parts[index].replaceAll(UNDERSCORE, UNDERSCORE_REPLACEMENT);
      if (checkIfStringIsValidUrl(parts[index])) {
        parts[index] = format("[%s](%s)", formattedValue, parts[index]);
      } else {
        parts[index] = formattedValue;
      }
    }
    return join(SPACE, parts);
  }

  @VisibleForTesting
  boolean checkIfStringIsValidUrl(String value) {
    UrlValidator urlValidator = new UrlValidator(schemes);
    return urlValidator.isValid(value);
  }

  private void formatTemplateUrlAndName(Map<String, String> templateVariables) {
    for (String key : TEMPLATE_KEYS_TO_BE_PROCESSED) {
      if (templateVariables.containsKey(key + NAME) && templateVariables.containsKey(key + URL)) {
        if (ARTIFACTS.equals(key)) {
          templateVariables.put(key, templateVariables.get(key + NAME));
        } else {
          String[] names = templateVariables.get(key + NAME).split(COMMA);
          String[] urls = templateVariables.get(key + URL).split(COMMA);
          String updatedValue = getUpdatedValue(names, urls);
          if (PIPELINE.equals(key)) {
            updatedValue = (!NA.equals(updatedValue)) ? format("in pipeline %s", updatedValue) : EMPTY;
          }
          templateVariables.put(key, updatedValue);
        }
      }
    }
  }

  @VisibleForTesting
  String getUpdatedValue(String[] names, String[] urls) {
    List<String> updatedValue = new ArrayList<>();
    if (names.length != urls.length) {
      log.info("Name and URL array has length mismatch. Names={} Urls={}", names, urls);
    } else {
      for (int index = 0; index < names.length; index++) {
        if (StringUtils.isNotEmpty(urls[index])) {
          updatedValue.add(format("[%s](%s)", names[index], urls[index]));
        } else {
          updatedValue.add(names[index]);
        }
      }
    }
    return join(", ", updatedValue);
  }
}
