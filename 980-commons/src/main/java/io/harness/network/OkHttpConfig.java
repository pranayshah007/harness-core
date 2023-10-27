/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.network;

import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.Singular;
import okhttp3.Interceptor;

@Data
@Builder
public class OkHttpConfig {
  private final String clientCertificateFilePath;
  private final String clientCertificateKeyFilePath;
  @Default private final boolean trustAllCertificates = false;
  @Default private final int connectTimeoutSeconds = 10;
  @Default private final int readTimeoutSeconds = 10;
  @Singular private final List<Interceptor> interceptors;
}
