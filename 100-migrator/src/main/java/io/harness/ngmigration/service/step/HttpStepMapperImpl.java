/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.data.structure.EmptyPredicate;
import io.harness.http.HttpHeaderConfig;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.http.HttpStepInfo;
import io.harness.plancreator.steps.http.HttpStepInfo.HttpStepInfoBuilder;
import io.harness.plancreator.steps.http.HttpStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.sm.states.HttpState;
import software.wings.yaml.workflow.StepYaml;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class HttpStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    return StepSpecTypeConstants.HTTP;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    HttpState state = new HttpState(stepYaml.getName());
    state.parseProperties(properties);
    HttpStepNode httpStepNode = new HttpStepNode();
    baseSetup(stepYaml, httpStepNode);
    HttpStepInfoBuilder httpStepInfoBuilder =
        HttpStepInfo.infoBuilder()
            .url(ParameterField.createValueField(state.getUrl()))
            .method(ParameterField.createValueField(state.getMethod()))
            .delegateSelectors(ParameterField.createValueField(Collections.emptyList()));

    if (StringUtils.isNotBlank(state.getBody())) {
      httpStepInfoBuilder.requestBody(ParameterField.createValueField(state.getBody()));
    }

    if (StringUtils.isNotBlank(state.getAssertion())) {
      httpStepInfoBuilder.assertion(ParameterField.createValueField(state.getAssertion()));
    }

    if (EmptyPredicate.isNotEmpty(state.getHeaders())) {
      httpStepInfoBuilder.headers(
          state.getHeaders()
              .stream()
              .map(header -> HttpHeaderConfig.builder().key(header.getKey()).value(header.getValue()).build())
              .collect(Collectors.toList()));
    }

    if (EmptyPredicate.isNotEmpty(state.getResponseProcessingExpressions())) {
      httpStepInfoBuilder.outputVariables(state.getResponseProcessingExpressions()
                                              .stream()
                                              .map(output
                                                  -> StringNGVariable.builder()
                                                         .type(NGVariableType.STRING)
                                                         .name(output.getName())
                                                         .value(ParameterField.createValueField(output.getValue()))
                                                         .build())
                                              .collect(Collectors.toList()));
    }

    httpStepNode.setHttpStepInfo(httpStepInfoBuilder.build());
    return httpStepNode;
  }
}
