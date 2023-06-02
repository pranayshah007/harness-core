/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.http;

import io.harness.http.beans.HttpInternalConfig;
import io.harness.http.beans.HttpInternalResponse;
import io.harness.logging.LogCallback;

import java.io.IOException;

public interface HttpService {
  HttpInternalResponse executeUrl(HttpInternalConfig internalConfig) throws IOException;

  HttpInternalResponse executeUrl(HttpInternalConfig internalConfig, LogCallback logCallback) throws IOException;
}
