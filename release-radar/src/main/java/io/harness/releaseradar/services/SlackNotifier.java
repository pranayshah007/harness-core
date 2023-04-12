/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.services;

import io.harness.data.structure.EmptyPredicate;
import io.harness.releaseradar.beans.EventNotifyData;

import com.google.inject.Singleton;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Singleton
public class SlackNotifier {
  public boolean notify(String webhookUrl, EventNotifyData data) throws IOException {
    try {
      URL url = new URL(webhookUrl);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

      String payload = "{\"text\": \""
          + "*Release Notification*"
          + "\", \"attachments\": [{\"text\": \"" + eventDataToString(data) + "\"}]}";

      conn.setDoOutput(true);
      conn.getOutputStream().write(payload.getBytes());

      if (conn.getResponseCode() != 200) {
        throw new RuntimeException("HTTP error code : " + conn.getResponseCode());
      }

      conn.disconnect();
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private static String eventDataToString(EventNotifyData eventData) {
    StringBuilder sb = new StringBuilder();
    if (eventData.getEnvironment() != null) {
      sb.append("*Environment:* ").append(eventData.getEnvironment()).append("\n");
    }
    if (eventData.getEventType() != null) {
      sb.append("*Event Type:* ").append(eventData.getEventType()).append("\n");
    }

    if (eventData.getBuildVersion() != null) {
      sb.append("*Build Version:* ").append(eventData.getBuildVersion()).append("\n");
    }

    if (EmptyPredicate.isNotEmpty(eventData.getRelease())) {
      sb.append("*Release:* ").append(eventData.getRelease()).append("\n");
    }

    if (EmptyPredicate.isNotEmpty(eventData.getServiceName())) {
      sb.append("*Service Name:* ").append(eventData.getServiceName()).append("\n");
    }
    // Add any other fields as needed
    return sb.toString();
  }
}
