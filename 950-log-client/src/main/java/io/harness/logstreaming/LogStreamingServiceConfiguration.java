/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import io.harness.secret.ConfigSecret;
import io.harness.threading.ThreadPoolConfig;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(makeFinal = false)
public class LogStreamingServiceConfiguration {
  private String baseUrl;
  @ConfigSecret private String serviceToken;
  private ThreadPoolConfig threadPoolConfig;
}
