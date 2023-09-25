/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.harness.logging.common.AccessTokenBean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StackdriverLoggerFactory {
  private static Logging logging;

  public static Logging get(final AccessTokenBean accessTokenBean) {
    if (logging != null) {
      Date nineMinutesFromNow = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(9));
      GoogleCredentials credentials = (GoogleCredentials) logging.getOptions().getCredentials();
      Date expirationTime = credentials.getAccessToken().getExpirationTime();
      if (expirationTime.before(nineMinutesFromNow)) {
        log.info("Logging token expires {}. Refreshing.", expirationTime);
        try {
          logging.close();
        } catch (Exception e) {
          log.error("Error closing logging", e);
        }
        logging = null;
      } else {
        return logging;
      }
    }
    LoggingOptions.Builder loggingOptionsBuilder =
        LoggingOptions.newBuilder()
            .setProjectId(accessTokenBean.getProjectId())
            .setCredentials(GoogleCredentials.create(
                new AccessToken(accessTokenBean.getTokenValue(), new Date(accessTokenBean.getExpirationTimeMillis()))));

    logging = loggingOptionsBuilder.build().getService();
    return logging;
  }
}
