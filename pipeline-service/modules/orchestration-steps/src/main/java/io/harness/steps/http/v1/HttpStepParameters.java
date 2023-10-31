/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.http.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@EqualsAndHashCode(callSuper = true)
@RecasterAlias("io.harness.steps.http.v1.HttpStepParameters")
public class HttpStepParameters extends HttpBaseStepInfoV1 implements SpecParameters {
  ParameterField<Map<String, Object>> output_vars;
  ParameterField<Map<String, Object>> input_vars;
  Map<String, String> headers;
  ParameterField<String> cert;
  ParameterField<String> cert_key;
  ParameterField<List<TaskSelectorYaml>> delegate;

  @Builder(builderMethodName = "infoBuilder")
  public HttpStepParameters(ParameterField<String> url, ParameterField<String> method, ParameterField<String> body,
      ParameterField<String> assertion, Map<String, Object> output_vars, Map<String, String> headers,
      ParameterField<String> cert, ParameterField<String> cert_key, ParameterField<List<TaskSelectorYaml>> delegate,
      Map<String, Object> input_vars) {
    super(url, method, body, assertion);
    this.output_vars = ParameterField.createValueField(output_vars);
    this.input_vars = ParameterField.createValueField(input_vars);
    this.headers = headers;
    this.cert = cert;
    this.cert_key = cert_key;
    this.delegate = delegate;
  }

  public io.harness.steps.http.HttpStepParameters toHttpStepParametersV0() {
    return io.harness.steps.http.HttpStepParameters.infoBuilder()
        .outputVariables(output_vars != null ? output_vars.getValue() : null)
        .assertion(assertion)
        .certificate(cert)
        .certificateKey(cert_key)
        .delegateSelectors(delegate)
        .inputVariables(input_vars != null ? input_vars.getValue() : null)
        .headers(headers)
        .method(method)
        .url(url)
        .requestBody(body)
        .build();
  }

  @Override
  public String getVersion() {
    return HarnessYamlVersion.V1;
  }
}
