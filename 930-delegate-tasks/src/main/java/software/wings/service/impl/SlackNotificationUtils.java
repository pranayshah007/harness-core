/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import lombok.experimental.UtilityClass;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

@UtilityClass
public class SlackNotificationUtils {
  private final MediaType APPLICATION_JSON = MediaType.parse("application/json; charset=utf-8");

  public Request createHttpRequest(String message, String slackWebHook) {
    RequestBody body = RequestBody.create(APPLICATION_JSON, message);

    return new Request.Builder()
        .url(slackWebHook)
        .post(body)
        .addHeader("Content-Type", "application/json")
        .addHeader("Accept", "*/*")
        .addHeader("Cache-Control", "no-cache")
        .addHeader("Host", "hooks.slack.com")
        .addHeader("accept-encoding", "gzip, deflate")
        .addHeader("content-length", "798")
        .addHeader("Connection", "keep-alive")
        .addHeader("cache-control", "no-cache")
        .build();
  }
}
