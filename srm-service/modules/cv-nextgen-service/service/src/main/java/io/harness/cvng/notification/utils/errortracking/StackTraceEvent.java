/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking;

import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.EVENT_VERSION_LABEL;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
public class StackTraceEvent {
  private String version;
  private String stackTrace;
  private String arcScreenUrl;

  public String toSlackString() {
    StringBuilder slack = new StringBuilder(EVENT_VERSION_LABEL + "*" + version + "*\n");
    slack.append("```").append(stackTrace.replace(",", "\n")).append("```");
    return slack.toString();
  }

  public String toEmailString() {
    StringBuilder email = new StringBuilder("<div style=\"margin-bottom: 16px\">");
    email.append("<span>" + EVENT_VERSION_LABEL + "<span style=\"font-weight: bold;\">" + version + "</span></span>");
    email.append("<div style =\"margin-top: 4px; background-color: #383946; border-radius: 3px;\">");
    email.append("<p style=\"color:white; padding: 15px; padding-top: 18px; padding-bottom:18px;\">");
    email.append(stackTrace.replace(",", "<br/>"));
    email.append("</p>").append("</div>").append("</div>");
    return email.toString();
  }
}