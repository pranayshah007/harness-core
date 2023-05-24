/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.rancher;

import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class RancherHelperServiceImpl implements RancherHelperService {
  @Override
  public ConnectorValidationResult testRancherConnection(String rancherUrl, String bearerToken) {
    // Implement connection test here using http client (with optional proxy)
    return ConnectorValidationResult.builder().status(ConnectivityStatus.FAILURE).build();
  }
}
