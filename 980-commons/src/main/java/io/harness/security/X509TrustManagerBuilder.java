/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.TrustManagerBuilderException;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Responsible for building a X509TrustManager.
 *
 * Explicit key algorithm SunX509 is used to ensure we actually create a X509TrustManager.
 * This is similar to the default algorithm that's returned if nothing else configured.
 *
 * Default values are found here:
 *    https://github.com/openjdk/jdk11u/blob/master/src/java.base/share/classes/javax/net/ssl/TrustManagerFactory.java
 *    https://github.com/openjdk/jdk11u/blob/master/src/java.base/share/classes/java/security/KeyStore.java
 *
 * Registered classes for the algorithms can be found here:
 *    https://github.com/openjdk/jdk11u/blob/master/src/java.base/share/classes/sun/security/ssl/SunJSSE.java
 */
@OwnedBy(PL)
public class X509TrustManagerBuilder {
  public static final String TRUST_MANAGER_ALGORITHM_SUNX509 = "SunX509";

  private KeyStore keyStore;
  private boolean trustAllCerts;

  public X509TrustManagerBuilder trustDefaultTrustStore() throws TrustManagerBuilderException {
    // throw explicitly to avoid silent misconfigurations.
    if (trustAllCerts) {
      throw new TrustManagerBuilderException(
          "No other trust source can be provided since all certs are trusted already.");
    }
    this.ensureKeyStoreIsInitialized();

    try {
      // Load all trusted issuers from default java trust store
      TrustManagerFactory defaultTrustManagerFactory = TrustManagerFactory.getInstance(TRUST_MANAGER_ALGORITHM_SUNX509);
      defaultTrustManagerFactory.init((KeyStore) null);
      for (TrustManager trustManager : defaultTrustManagerFactory.getTrustManagers()) {
        if (trustManager instanceof X509TrustManager) {
          for (X509Certificate acceptedIssuer : ((X509TrustManager) trustManager).getAcceptedIssuers()) {
            keyStore.setCertificateEntry(acceptedIssuer.getSubjectDN().getName(), acceptedIssuer);
          }
        }
      }
    } catch (Exception ex) {
      throw new TrustManagerBuilderException(
          String.format("Failed to build trust key store from default Java trust store: %s", ex.getMessage()), ex);
    }

    return this;
  }

  public X509TrustManagerBuilder trustAllCertificates() throws TrustManagerBuilderException {
    // throw explicitly to avoid silent misconfigurations.
    if (this.keyStore != null) {
      throw new TrustManagerBuilderException(
          "Unable to configure to trust all certificates as it conflicts with the existing configuration.");
    }

    this.trustAllCerts = true;
    return this;
  }

  public X509TrustManager build() throws TrustManagerBuilderException {
    if (this.trustAllCerts) {
      return new AllTrustingX509TrustManager();
    }

    // We assume that there has to always be something to trust - throw explicitly to avoid silent misconfiguration
    if (this.keyStore == null) {
      throw new TrustManagerBuilderException("No trusting entity was configured, unable to build manager.");
    }

    try {
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TRUST_MANAGER_ALGORITHM_SUNX509);
      trustManagerFactory.init(this.keyStore);

      // We only expect one TrustManager from the SunX509 factory
      return (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
    } catch (Exception ex) {
      throw new TrustManagerBuilderException(String.format("Failed to build trust manager: %s", ex.getMessage()), ex);
    }
  }

  private void ensureKeyStoreIsInitialized() throws TrustManagerBuilderException {
    if (this.keyStore != null) {
      return;
    }

    try {
      this.keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      this.keyStore.load(null, null);
    } catch (Exception ex) {
      throw new TrustManagerBuilderException(
          String.format("Failed to create empty key store: %s", ex.getMessage()), ex);
    }
  }
}
