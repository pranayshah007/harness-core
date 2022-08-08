/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cf;

import io.harness.secret.ConfigSecret;

import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Singleton
public class CfClientConfig {
  @ConfigSecret private String apiKey;
  @Default private String configUrl = "https://config.feature-flags.uat.harness.io/api/1.0";
  @Default private String eventUrl = "https://event.feature-flags.uat.harness.io/api/1.0";
  private boolean analyticsEnabled;
  @Default private int connectionTimeout = 10000;
  @Default private int readTimeout = 10000;
  @Default private int retries = 6;
  @Default private int sleepInterval = 5000;
  @Default private int bufferSize = 10240;
}
