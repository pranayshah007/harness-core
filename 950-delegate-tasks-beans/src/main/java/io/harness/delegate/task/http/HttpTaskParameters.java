/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.http;

import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.HttpCapabilityDetailsLevel.QUERY;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.beans.HttpCertificate;
import io.harness.beans.KeyValuePair;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HttpTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  String method;
  @Expression(ALLOW_SECRETS) String url;
  @Expression(ALLOW_SECRETS) String header;
  @Expression(ALLOW_SECRETS) String body;
  @Expression(ALLOW_SECRETS) List<KeyValuePair> headers;
  int socketTimeoutMillis;
  boolean useProxy;
  boolean isCertValidationRequired;
  boolean useHeaderForCapabilityCheck;
  HttpCertificate certificate;
  List<EncryptedDataDetail> encryptedDataDetails;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (useHeaderForCapabilityCheck) {
      return Collections.singletonList(
          HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              url, headers, QUERY, maskingEvaluator));
    } else {
      return Collections.singletonList(
          HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
              url, QUERY, maskingEvaluator));
    }
  }
}
