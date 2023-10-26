/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class LogStreamingModule extends AbstractModule {
  private final String logStreamingServiceBaseUrl;
  private final String clientCertificateFilePath;
  private final String clientCertificateKeyFilePath;
  private final boolean trustAllCertificates;

  public LogStreamingModule(final String logStreamingServiceBaseUrl) {
    this.logStreamingServiceBaseUrl = logStreamingServiceBaseUrl;
    this.clientCertificateFilePath = null;
    this.clientCertificateKeyFilePath = null;
    this.trustAllCertificates = false;
  }

  public LogStreamingModule(final String logStreamingServiceBaseUrl, final String clientCertificateFilePath,
      final String clientCertificateKeyFilePath, final boolean trustAllCertificates) {
    this.logStreamingServiceBaseUrl = logStreamingServiceBaseUrl;
    this.clientCertificateFilePath = clientCertificateFilePath;
    this.clientCertificateKeyFilePath = clientCertificateKeyFilePath;
    this.trustAllCertificates = trustAllCertificates;
  }

  @Override
  @Singleton
  protected void configure() {
    bind(LogStreamingClient.class)
        .toProvider(new LogStreamingClientFactory(this.logStreamingServiceBaseUrl, this.clientCertificateFilePath,
            this.clientCertificateKeyFilePath, this.trustAllCertificates));
  }
}
