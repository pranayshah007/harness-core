/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.provision.service;

import io.harness.exception.InvalidRequestException;
import io.harness.http.HttpService;
import io.harness.http.beans.HttpInternalConfig;
import io.harness.http.beans.HttpInternalResponse;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.serializer.JsonUtils;

import software.wings.beans.HttpMethod;

import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;

public class ProvisionServiceImpl implements ProvisionService {
  @Inject @Named("provisionModuleConfig") ProvisionModuleConfig provisionModuleConfig;
  @Inject private HttpService httpService;
  private static final String ACCOUNT_ID = "account_id";
  private static final String NAMESPACE = "namespace";

  @Override
  public void triggerPipeline(String accountIdentifier, String namespace) throws IOException {
    makeTriggerApi(accountIdentifier, namespace);
  }

  private void makeTriggerApi(String accountIdentifier, String namespace) throws IOException {
    String url = provisionModuleConfig.getTriggerPipelineUrl();

    Map<String, String> body = new HashMap<>();
    body.put(ACCOUNT_ID, accountIdentifier);
    body.put(NAMESPACE, namespace);

    HttpInternalResponse httpResponse = httpService.executeUrl(HttpInternalConfig.builder()
                                                                   .method(HttpMethod.POST.name())
                                                                   .socketTimeoutMillis(10000)
                                                                   .body(JsonUtils.asJson(body))
                                                                   .url(url)
                                                                   .build());

    if (Objects.isNull(httpResponse) || httpResponse.getHttpResponseCode() < 200
        || httpResponse.getHttpResponseCode() >= 300) {
      throw new InvalidRequestException("Pipeline Trigger http call failed");
    }
  }
}
