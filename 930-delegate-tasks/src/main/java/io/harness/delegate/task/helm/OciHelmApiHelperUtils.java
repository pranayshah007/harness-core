/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.OciHelmDockerApiException;
import io.harness.network.Http;

import com.google.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@Singleton
@OwnedBy(CDP)
public class OciHelmApiHelperUtils {
  protected String normalizeFieldData(final String fieldData) {
    if (EmptyPredicate.isEmpty(fieldData)) {
      return fieldData;
    }

    StringBuffer result = new StringBuffer(fieldData);
    while (result.charAt(0) == '/') {
      result.replace(0, 1, "");
    }
    while (result.charAt(result.length() - 1) == '/') {
      result.replace(result.length() - 1, result.length(), "");
    }

    return result.toString();
  }

  protected String normalizeUrl(String url) throws URISyntaxException {
    URI uriObject = new URI(url);
    if (EmptyPredicate.isEmpty(uriObject.getHost())) {
      if (EmptyPredicate.isEmpty(uriObject.getPath())) {
        throw new OciHelmDockerApiException("Hostname provided in URL field of OCI Helm connector is invalid");
      }
      return format("https://%s", uriObject.getPath());
    }
    if (isNotEmpty(uriObject.getScheme()) && uriObject.getScheme().startsWith("http")) {
      return format("%s://%s%s", uriObject.getScheme(), uriObject.getHost(),
          uriObject.getPort() != -1 ? format(":%d", uriObject.getPort()) : "");
    }
    return format(
        "https://%s%s", uriObject.getHost(), uriObject.getPort() != -1 ? format(":%d", uriObject.getPort()) : "");
  }

  public OciHelmDockerApiRestClient getRestClient(String baseUrl) throws URISyntaxException {
    return getRetrofit(JacksonConverterFactory.create(), baseUrl).create(OciHelmDockerApiRestClient.class);
  }

  private Retrofit getRetrofit(Converter.Factory converterFactory, String baseUrl) throws URISyntaxException {
    return new Retrofit.Builder()
        .baseUrl(normalizeUrl(baseUrl))
        .addConverterFactory(converterFactory)
        .client(Http.getOkHttpClient(baseUrl, false))
        .build();
  }
}
