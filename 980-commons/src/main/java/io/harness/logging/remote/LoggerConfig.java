/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.remote;

import lombok.Data;

@Data
public class LoggerConfig {
  private final String accountId;
  private final String loggingServiceUrl;
  private final String loggingTokenUrl;
  private final String delegateToken;
  private final String clientCertPath;
  private final String clientCertKeyPath;
  private final boolean trustAllCerts;
  private final String appName;
}
