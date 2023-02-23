/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.network.Http.getProxyHostName;
import static io.harness.network.Http.getProxyPassword;
import static io.harness.network.Http.getProxyPort;
import static io.harness.network.Http.getProxyUserName;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

@OwnedBy(HarnessTeam.DEL)
public class ProxyUtils {
  public static void initProxyConfig() {
    String proxyUserEnv = System.getenv("PROXY_USER");
    if (isNotBlank(proxyUserEnv)) {
      System.setProperty("http.proxyUser", proxyUserEnv);
      System.setProperty("https.proxyUser", proxyUserEnv);
    }

    String proxyPasswordEnv = System.getenv("PROXY_PASSWORD");
    if (isNotBlank(proxyPasswordEnv)) {
      System.setProperty("http.proxyPassword", proxyPasswordEnv);
      System.setProperty("https.proxyPassword", proxyPasswordEnv);
    }

    String proxyHost = getProxyHostName();
    String proxyPort = getProxyPort();
    String proxyUser = getProxyUserName();
    String proxyPassword = getProxyPassword();

    validateProxyProperties(proxyHost, proxyPort, proxyUser, proxyPassword);

    if (isProxyAuthUsed()) {
      /**
       * If url to be proxied is `https` protocol
       * and one of these system properties is present using System#getProperty():
       * [https.proxyUser, https.proxyPassword, https.proxyHost, https.proxyPort]
       * then URL#openConnection() fails with error: Unable to tunnel through proxy. Proxy returns “HTTP/1.1 407”
       *
       * This scenario is possible if one of these env vars PROXY_USER or PROXY_PASSWORD is not empty
       * or if System.getProperty("proxyScheme") is NOT "http", refer io.harness.network.Http#getHttpProxyHost() logic.
       *
       * In order a https request to be proxied correctly Proxy should be provided as argument URL#openConnection(Proxy
       * proxy) A custom Authenticator should be used and disable these properties:
       * jdk.http.auth.tunneling.disabledSchemes, jdk.http.auth.proxying.disabledSchemes
       * refer https://www.oracle.com/java/technologies/javase/8u111-relnotes.html
       */

      Authenticator.setDefault(new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          if (getRequestorType().equals(RequestorType.PROXY)) {
            return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
          }
          return super.getPasswordAuthentication();
        }
      });

      System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
      System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
    }
  }

  static boolean isProxyAuthUsed() {
    return isNotEmpty(getProxyUserName());
  }

  static void validateProxyProperties(String proxyHost, String proxyPort, String proxyUser, String proxyPassword) {
    if (isNotEmpty(proxyHost) && isEmpty(proxyPort)) {
      throw new IllegalArgumentException("proxyHost provided, but proxyPort is empty");
    }
    if (isNotEmpty(proxyPort) && isEmpty(proxyHost)) {
      throw new IllegalArgumentException("proxyPort provided, but proxyHost is empty");
    }

    if (isNotEmpty(proxyPort)) {
      try {
        Integer.parseInt(proxyPort);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(format("Invalid proxyPort provided %s", proxyPort));
      }
    }

    if (isNotEmpty(proxyUser) && isEmpty(proxyPassword)) {
      throw new IllegalArgumentException("proxyUser provided, but proxyPassword is empty");
    }
    if (isNotEmpty(proxyPassword) && isEmpty(proxyUser)) {
      throw new IllegalArgumentException("proxyPassword provided, but proxyUser is empty");
    }

    if (isNotEmpty(proxyUser) && isEmpty(proxyHost)) {
      throw new IllegalArgumentException("proxyUser provided, but proxyHost is empty");
    }
  }
}
