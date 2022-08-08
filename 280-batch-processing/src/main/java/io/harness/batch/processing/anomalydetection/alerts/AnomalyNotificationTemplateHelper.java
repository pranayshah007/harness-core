/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.alerts;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnomalyNotificationTemplateHelper {
  public static final String anomalyDetailsSlackTemplateBlock = "{"
      + "\"type\": \"section\","
      + "\"text\": {"
      + "\"type\": \"mrkdwn\","
      + "\"text\": \"%s\""
      + "}\n"
      + "}";

  public static final String anomalyDetailsEmailTemplateBlock =
      "<div style=\"color: #77787b; line-height: 30px; font-weight: normal;\">\n"
      + "%s"
      + "</div>";
}
