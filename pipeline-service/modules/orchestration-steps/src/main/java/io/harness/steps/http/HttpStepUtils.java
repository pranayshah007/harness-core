/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.http;

import static io.harness.beans.constants.JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.HttpCertificateNG;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class HttpStepUtils {
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  private static final String URL_ENCODED_CHAR_REGEX = ".*%[0-9a-fA-F]{2}.*";
  public Map<String, String> buildContextMapFromResponse(
      HttpStepResponse httpStepResponse, boolean resolveObjectsViaJSONSelect) {
    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("httpResponseBody", httpStepResponse.getHttpResponseBody());
    contextMap.put("httpResponseCode", String.valueOf(httpStepResponse.getHttpResponseCode()));
    if (resolveObjectsViaJSONSelect) {
      contextMap.put(RESOLVE_OBJECTS_VIA_JSON_SELECT, "true");
    }
    return contextMap;
  }

  public Map<String, String> evaluateOutputVariables(
      Map<String, Object> outputVariables, HttpStepResponse httpStepResponse, Ambiance ambiance) {
    Map<String, String> outputVariablesEvaluated = new LinkedHashMap<>();
    final boolean resolveObjectsViaJSONSelect = pmsFeatureFlagHelper.isEnabled(
        AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_RESOLVE_OBJECTS_VIA_JSON_SELECT);

    if (outputVariables != null) {
      Map<String, String> contextMap = buildContextMapFromResponse(httpStepResponse, resolveObjectsViaJSONSelect);
      outputVariables.keySet().forEach(name -> {
        Object expression = outputVariables.get(name);
        if (expression instanceof ParameterField) {
          ParameterField<?> expr = (ParameterField<?>) expression;
          if (expr.isExpression()) {
            // Adding Json Expression Support
            AmbianceUtils.enabledJsonSupportFeatureFlag(ambiance, contextMap);
            Object evaluatedValue = engineExpressionService.evaluateExpression(
                ambiance, expr.getExpressionValue(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED, contextMap);
            if (evaluatedValue != null) {
              outputVariablesEvaluated.put(name, evaluatedValue.toString());
            }
          } else if (expr.getValue() != null) {
            outputVariablesEvaluated.put(name, expr.getValue().toString());
          }
        }
      });
    }
    return outputVariablesEvaluated;
  }

  public String encodeURL(String rawUrl, NGLogCallback logCallback) {
    if (!isURLAlreadyEncoded(rawUrl)) {
      try {
        URL url = new URL(rawUrl);
        URI uri = new URI(url.getProtocol(), url.getUserInfo(), IDN.toASCII(url.getHost()), url.getPort(),
            url.getPath(), url.getQuery(), url.getRef());
        String encodedUrl = uri.toASCIIString();
        logCallback.saveExecutionLog(String.format("Encoded URL: %s", encodedUrl));
        return encodedUrl;
      } catch (MalformedURLException | URISyntaxException e) {
        logCallback.saveExecutionLog(String.format("Failed to encode URL: %s", e.getMessage()));
      }
    }
    return rawUrl;
  }

  private static boolean isURLAlreadyEncoded(String url) {
    return url.matches(URL_ENCODED_CHAR_REGEX);
  }

  public String fetchFinalValue(ParameterField<String> field) {
    return (String) field.fetchFinalValue();
  }

  public void closeLogStream(Ambiance ambiance) {
    try {
      Thread.sleep(500, 0);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Close Log Stream was interrupted", e);
    } finally {
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      logStreamingStepClient.closeAllOpenStreamsWithPrefix(StepUtils.generateLogKeys(ambiance, emptyList()).get(0));
    }
  }

  public NGLogCallback getNGLogCallback(LogStreamingStepClientFactory logStreamingStepClientFactory, Ambiance ambiance,
      String logFix, boolean openStream) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, logFix, openStream);
  }

  public static boolean validateAssertions(
      HttpStepResponse httpStepResponse, ParameterField<String> assertionParameterField) {
    if (ParameterField.isNull(assertionParameterField)) {
      return true;
    }

    HttpExpressionEvaluator evaluator = new HttpExpressionEvaluator(httpStepResponse);
    String assertion = (String) assertionParameterField.fetchFinalValue();
    if (assertion == null || isEmpty(assertion.trim())) {
      return true;
    }

    try {
      Object value = evaluator.evaluateExpression(assertion);
      if (!(value instanceof Boolean)) {
        throw new InvalidRequestException(String.format(
            "Expected boolean assertion, got %s value", value == null ? "null" : value.getClass().getSimpleName()));
      }
      return (boolean) value;
    } catch (Exception e) {
      throw new InvalidRequestException("Assertion provided is not a valid expression", e);
    }
  }

  @VisibleForTesting
  public Optional<HttpCertificateNG> createCertificate(ParameterField<String> cert, ParameterField<String> cert_key) {
    if (isEmpty(cert.getValue()) && isEmpty(cert_key.getValue())) {
      return Optional.empty();
    }

    if (isEmpty(cert.getValue()) && isNotEmpty(cert_key.getValue())) {
      throw new InvalidRequestException(
          "Only certificateKey is provided, we need both certificate and certificateKey or only certificate", USER);
    }

    return Optional.of(
        HttpCertificateNG.builder().certificate(cert.getValue()).certificateKey(cert_key.getValue()).build());
  }
}
