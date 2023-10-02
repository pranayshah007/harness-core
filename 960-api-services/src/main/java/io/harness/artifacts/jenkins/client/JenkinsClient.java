/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.jenkins.client;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.network.Http;

import software.wings.helpers.ext.jenkins.CustomJenkinsHttpClient;

import java.net.URI;
import java.net.URISyntaxException;
import javax.net.ssl.HostnameVerifier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
@Slf4j
public class JenkinsClient {
  private CustomJenkinsHttpClient jenkinsHttpClient;
  public static final String TOKEN_FIELD = "Bearer Token(HTTP Header)";

  public CustomJenkinsHttpClient getJenkinsHttpClient(JenkinsInternalConfig jenkinsInternalConfig)
      throws URISyntaxException {
    if (TOKEN_FIELD.equals(jenkinsInternalConfig.getAuthMechanism())) {
      return new CustomJenkinsHttpClient(new URI(jenkinsInternalConfig.getJenkinsUrl()),
          new String(jenkinsInternalConfig.getToken()), getUnSafeBuilder());
    } else {
      return new CustomJenkinsHttpClient(new URI(jenkinsInternalConfig.getJenkinsUrl()),
          jenkinsInternalConfig.getUsername(), new String(jenkinsInternalConfig.getPassword()), getUnSafeBuilder());
    }
  }

  public JenkinsCustomServer getJenkinsServer(JenkinsInternalConfig jenkinsInternalConfig) throws URISyntaxException {
    return new JenkinsCustomServer(getJenkinsHttpClient(jenkinsInternalConfig));
  }

  public static HttpClientBuilder getUnSafeBuilder() {
    RequestConfig.Builder requestBuilder = RequestConfig.custom();
    requestBuilder.setConnectTimeout(150 * 1000);
    requestBuilder.setConnectionRequestTimeout(150 * 1000);

    HttpClientBuilder builder = HttpClientBuilder.create();
    builder.setDefaultRequestConfig(requestBuilder.build());
    try {
      // Set ssl context
      builder.setSSLContext(Http.getSslContext());
      // Create all-trusting host name verifier
      HostnameVerifier allHostsValid = (s, sslSession) -> true;
      builder.setSSLHostnameVerifier(allHostsValid);
    } catch (Exception ex) {
      log.warn("Installing trust managers");
    }
    return builder;
  }
}
