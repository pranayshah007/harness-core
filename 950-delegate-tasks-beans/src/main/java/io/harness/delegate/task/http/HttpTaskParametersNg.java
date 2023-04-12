/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.http;

import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.HttpCapabilityDetailsLevel.QUERY;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.beans.HttpCertificateNG;
import io.harness.beans.KeyValuePair;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.http.HttpHeaderConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HttpTaskParametersNg implements TaskParameters, ExecutionCapabilityDemander {
  String method;
  @Expression(ALLOW_SECRETS) String url;
  @Expression(ALLOW_SECRETS) List<HttpHeaderConfig> requestHeader;
  @Expression(ALLOW_SECRETS) String body;
  int socketTimeoutMillis;
  boolean useProxy;
  boolean isCertValidationRequired;
  boolean shouldAvoidHeadersInCapability;

  // New type for supporting NG secret resolution
  @Expression(ALLOW_SECRETS) HttpCertificateNG certificateNG;
  List<EncryptedDataDetail> encryptedDataDetails;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (shouldAvoidHeadersInCapability) {
      return Collections.singletonList(
          HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              url, QUERY, maskingEvaluator));
    }
    List<KeyValuePair> headers = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(requestHeader)) {
      for (HttpHeaderConfig headerConfig : requestHeader) {
        headers.add(KeyValuePair.builder().key(headerConfig.getKey()).value(headerConfig.getValue()).build());
      }
    }
    return Collections.singletonList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        url, headers, QUERY, maskingEvaluator));
  }
}
