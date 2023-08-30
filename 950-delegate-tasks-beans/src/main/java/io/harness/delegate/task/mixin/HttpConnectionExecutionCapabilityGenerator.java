/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.mixin;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.KeyValuePair;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.task.utils.KmsUtils;
import io.harness.expression.DummySubstitutor;
import io.harness.expression.ExpressionEvaluator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@UtilityClass
@Slf4j
public class HttpConnectionExecutionCapabilityGenerator {
  public static HttpConnectionExecutionCapability buildHttpConnectionExecutionCapability(
      String urlString, ExpressionEvaluator maskingEvaluator) {
    return buildHttpConnectionExecutionCapability(urlString, HttpCapabilityDetailsLevel.PATH, maskingEvaluator);
  }

  public static HttpConnectionExecutionCapability buildHttpConnectionExecutionCapabilityForKms(
      String region, ExpressionEvaluator maskingEvaluator) {
    String kmsUrl = KmsUtils.generateKmsUrl(region);
    return buildHttpConnectionExecutionCapability(kmsUrl, maskingEvaluator);
  }

  public static HttpConnectionExecutionCapability buildHttpConnectionExecutionCapabilityWithIgnoreResponseCode(
      String urlString, ExpressionEvaluator maskingEvaluator, boolean ignoreResponseCode) {
    HttpConnectionExecutionCapability httpConnectionExecutionCapability =
        buildHttpConnectionExecutionCapability(urlString, HttpCapabilityDetailsLevel.PATH, maskingEvaluator);
    httpConnectionExecutionCapability.setIgnoreResponseCode(ignoreResponseCode);
    return httpConnectionExecutionCapability;
  }

  public static HttpConnectionExecutionCapability buildHttpConnectionExecutionCapabilityWithIgnoreResponseCode(
      String urlString, ExpressionEvaluator maskingEvaluator, boolean ignoreResponseCode,
      HttpCapabilityDetailsLevel level) {
    HttpConnectionExecutionCapability httpConnectionExecutionCapability =
        buildHttpConnectionExecutionCapability(urlString, level, maskingEvaluator);
    httpConnectionExecutionCapability.setIgnoreResponseCode(ignoreResponseCode);
    return httpConnectionExecutionCapability;
  }

  public static HttpConnectionExecutionCapability buildHttpConnectionExecutionCapabilityWithIgnoreResponseCode(
      String urlString, ExpressionEvaluator maskingEvaluator, boolean ignoreResponseCode, List<KeyValuePair> headers,
      HttpCapabilityDetailsLevel level) {
    HttpConnectionExecutionCapability httpConnectionExecutionCapability =
        buildHttpConnectionExecutionCapability(urlString, headers, level, maskingEvaluator);
    httpConnectionExecutionCapability.setIgnoreResponseCode(ignoreResponseCode);
    return httpConnectionExecutionCapability;
  }

  public static HttpConnectionExecutionCapability buildHttpConnectionExecutionCapability(
      String urlString, HttpCapabilityDetailsLevel level, ExpressionEvaluator maskingEvaluator) {
    try {
      URI uri = new URI(DummySubstitutor.substitute(urlString));

      if (isNotBlank(uri.getScheme()) && isNotBlank(uri.getHost())) {
        HttpConnectionExecutionCapability httpConnectionExecutionCapability =
            level.getHttpConnectionExecutionCapability(urlString);
        if (!httpConnectionExecutionCapability.fetchCapabilityBasis().contains(DummySubstitutor.DUMMY_UUID)) {
          return httpConnectionExecutionCapability;
        }
      }
    } catch (Exception e) {
      log.error("conversion to java.net.URI failed for url: {}", maskedUrlString(maskingEvaluator, urlString), e);
    }
    // This is falling back to existing approach, where we test for entire URL
    return HttpConnectionExecutionCapability.builder().url(maskedUrlString(maskingEvaluator, urlString)).build();
  }

  /***
   * Build HTTP Execution capability with headers. We will check for headers while doing capability check on the
   * delegate
   * @param urlString
   * @param headers
   * @param level
   * @param maskingEvaluator
   * @return
   */
  public static HttpConnectionExecutionCapability buildHttpConnectionExecutionCapability(String urlString,
      List<KeyValuePair> headers, HttpCapabilityDetailsLevel level, ExpressionEvaluator maskingEvaluator) {
    try {
      URI uri = new URI(DummySubstitutor.substitute(urlString));

      if (isNotBlank(uri.getScheme()) && isNotBlank(uri.getHost())) {
        HttpConnectionExecutionCapability httpConnectionExecutionCapability =
            level.getHttpConnectionExecutionCapabilityWithMaskedHeaders(urlString, headers, maskingEvaluator);
        if (!httpConnectionExecutionCapability.fetchCapabilityBasis().contains(DummySubstitutor.DUMMY_UUID)) {
          return httpConnectionExecutionCapability;
        }
      }
    } catch (Exception e) {
      log.error("conversion to java.net.URI failed for url: {}", maskedUrlString(maskingEvaluator, urlString), e);
    }
    // This is falling back to existing approach, where we test for entire URL
    return HttpConnectionExecutionCapability.builder()
        .url(maskedUrlString(maskingEvaluator, urlString))
        .headers(maskedHeaders(maskingEvaluator, headers))
        .build();
  }

  private static String maskedUrlString(ExpressionEvaluator maskingEvaluator, String urlString) {
    if (maskingEvaluator == null) {
      return urlString;
    }
    return maskingEvaluator.substitute(urlString, Collections.emptyMap());
  }

  private static List<KeyValuePair> maskedHeaders(ExpressionEvaluator maskingEvaluator, List<KeyValuePair> headers) {
    if (maskingEvaluator == null || headers == null) {
      return headers;
    }
    return headers.stream()
        .map(entry
            -> KeyValuePair.builder()
                   .key(maskingEvaluator.substitute(entry.getKey(), Collections.emptyMap()))
                   .value(maskingEvaluator.substitute(entry.getValue(), Collections.emptyMap()))
                   .build())
        .collect(Collectors.toList());
  }

  public enum HttpCapabilityDetailsLevel {
    DOMAIN(false, false),
    PATH(true, false),
    QUERY(true, true);
    private boolean usePath, useQuery;

    HttpCapabilityDetailsLevel(boolean usePath, boolean useQuery) {
      this.usePath = usePath;
      this.useQuery = useQuery;
    }

    private HttpConnectionExecutionCapability getHttpConnectionExecutionCapability(String urlString)
        throws URISyntaxException {
      URI uri = new URI(DummySubstitutor.substitute(urlString));
      return HttpConnectionExecutionCapability.builder()
          .scheme(uri.getScheme())
          .host(uri.getHost())
          .port(uri.getPort())
          .path(usePath ? getPath(uri) : null)
          .query(useQuery ? uri.getQuery() : null)
          .build();
    }

    private HttpConnectionExecutionCapability getHttpConnectionExecutionCapabilityWithMaskedHeaders(
        String urlString, List<KeyValuePair> headers, ExpressionEvaluator maskingEvaluator) throws URISyntaxException {
      URI uri = new URI(DummySubstitutor.substitute(urlString));
      return HttpConnectionExecutionCapability.builder()
          .headers(maskedHeaders(maskingEvaluator, headers))
          .scheme(uri.getScheme())
          .host(uri.getHost())
          .port(uri.getPort())
          .path(usePath ? getPath(uri) : null)
          .query(useQuery ? uri.getQuery() : null)
          .build();
    }

    private static String getPath(URI uri) {
      if (isBlank(uri.getPath())) {
        return null;
      }
      return uri.getPath().substring(1);
    }
  }
}
