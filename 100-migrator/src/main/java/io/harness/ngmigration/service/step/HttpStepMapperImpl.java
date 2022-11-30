/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.beans.KeyValuePair;
import io.harness.data.structure.EmptyPredicate;
import io.harness.http.HttpHeaderConfig;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.http.HttpStepInfo;
import io.harness.plancreator.steps.http.HttpStepInfo.HttpStepInfoBuilder;
import io.harness.plancreator.steps.http.HttpStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.sm.State;
import software.wings.sm.states.HttpState;
import software.wings.yaml.workflow.StepYaml;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class HttpStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    return StepSpecTypeConstants.HTTP;
  }

  @Override
  public State getState(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    HttpState state = new HttpState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    HttpState state = (HttpState) getState(stepYaml);
    HttpStepNode httpStepNode = new HttpStepNode();
    baseSetup(stepYaml, httpStepNode);
    HttpStepInfoBuilder httpStepInfoBuilder =
        HttpStepInfo.infoBuilder()
            .url(ParameterField.createValueField(state.getUrl()))
            .method(ParameterField.createValueField(state.getMethod()))
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getTags()));

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

  @Override
  public boolean areSimilar(StepYaml stepYaml1, StepYaml stepYaml2) {
    // Check URL, Method, Body, Headers, Assertion condition
    HttpState state1 = (HttpState) getState(stepYaml1);
    HttpState state2 = (HttpState) getState(stepYaml2);
    if (!StringUtils.equals(state1.getUrl(), state2.getUrl())) {
      return false;
    }

    if (!StringUtils.equals(state1.getMethod(), state2.getMethod())) {
      return false;
    }

    if (!StringUtils.equals(state1.getBody(), state2.getBody())) {
      return false;
    }

    if (!StringUtils.equals(state1.getAssertion(), state2.getAssertion())) {
      return false;
    }

    List<KeyValuePair> headers1 =
        EmptyPredicate.isNotEmpty(state1.getHeaders()) ? state1.getHeaders() : Collections.emptyList();
    List<KeyValuePair> headers2 =
        EmptyPredicate.isNotEmpty(state2.getHeaders()) ? state2.getHeaders() : Collections.emptyList();
    if (headers1.size() != headers2.size()) {
      return false;
    }

    if (EmptyPredicate.isNotEmpty(headers1)) {
      Map<String, String> headerMap1 =
          headers1.stream().collect(Collectors.toMap(KeyValuePair::getKey, KeyValuePair::getValue));
      Map<String, String> headerMap2 =
          headers2.stream().collect(Collectors.toMap(KeyValuePair::getKey, KeyValuePair::getValue));
      if (!headerMap1.keySet().equals(headerMap2.keySet())) {
        return false;
      }

      return headerMap1.entrySet().stream().allMatch(
          entry -> StringUtils.equals(headerMap2.get(entry.getKey()), entry.getValue()));
    }

    return true;
  }
}
