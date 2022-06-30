/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.SslContextBuilderException;

import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * Build a new SSLContext from given X509KeyManager and X509TrustManager.
 */
@OwnedBy(PL)
public class X509SslContextBuilder {
  private static final String SSL_CONTEXT_PROTOCOL_TLS = "TLS";

  private X509TrustManager trustManager;
  private X509KeyManager keyManager;
  private SecureRandom secureRandom;

  public X509SslContextBuilder trustManager(X509TrustManager trustManager) {
    this.trustManager = trustManager;
    return this;
  }

  public X509SslContextBuilder keyManager(X509KeyManager keyManager) {
    this.keyManager = keyManager;
    return this;
  }

  public X509SslContextBuilder secureRandom(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
    return this;
  }

  public SSLContext build() throws SslContextBuilderException {
    TrustManager[] trustManagers = this.trustManager == null ? null : new TrustManager[] {this.trustManager};
    KeyManager[] keyManagers = this.keyManager == null ? null : new KeyManager[] {this.keyManager};

    try {
      SSLContext sslContext = SSLContext.getInstance(SSL_CONTEXT_PROTOCOL_TLS);
      sslContext.init(keyManagers, trustManagers, this.secureRandom);

      return sslContext;
    } catch (Exception ex) {
      throw new SslContextBuilderException(
          String.format("Failed to initialize a new SSLContext: %s", ex.getMessage()), ex);
    }
  }
}
