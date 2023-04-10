/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.rancher;

import io.harness.beans.KeyValuePair;
import io.harness.connector.task.rancher.RancherConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.http.HttpService;
import io.harness.http.beans.HttpInternalConfig;
import io.harness.http.beans.HttpInternalResponse;

import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
public class RancherHelperServiceDelegateImpl implements RancherHelperServiceDelegate {
  @Inject private EncryptionService encryptionService;
  @Inject private HttpService httpService;

  @Override
  public void testRancherConnection(final io.harness.connector.task.rancher.RancherConfig rancherConfig)
      throws IOException {
    makeRancherApi("GET", "/v3/clusters", rancherConfig);
  }

  @NotNull
  private HttpInternalResponse makeRancherApi(final String httpMethod, final String url, RancherConfig rancherConfig)
      throws IOException {
    StringBuilder urlBuffer = new StringBuilder();
    urlBuffer.append(rancherConfig.getCredential().getRancherUrl()).append(url);

    List<KeyValuePair> headers = new ArrayList<>();
    headers.add(KeyValuePair.builder()
                    .key(HttpHeaders.AUTHORIZATION)
                    .value("Bearer " + rancherConfig.getCredential().getPassword().getRancherPassword())
                    .build());

    HttpInternalResponse httpResponse =
        httpService.executeUrl(HttpInternalConfig.builder()
                                   .method(httpMethod)
                                   .headers(headers)
                                   .socketTimeoutMillis(10000)
                                   .url(urlBuffer.toString())
                                   // TODO: Check if useProxy field need to be added in Rancher cloud provider
                                   .useProxy(false)
                                   //.isCertValidationRequired(rancherConfig.isCertValidationRequired())
                                   .build());

    if (Objects.isNull(httpResponse) || httpResponse.getHttpResponseCode() < 200
        || httpResponse.getHttpResponseCode() >= 300) {
      throw new InvalidRequestException("Rancher http call failed");
    }

    return httpResponse;
  }
}
