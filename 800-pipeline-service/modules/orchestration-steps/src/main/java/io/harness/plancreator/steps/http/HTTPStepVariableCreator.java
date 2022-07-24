/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.http;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.http.HttpOutcome;
import io.harness.yaml.core.variables.NGVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class HTTPStepVariableCreator extends GenericStepVariableCreator<HttpStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.HTTP);
  }

  @Override
  protected void addVariablesInComplexObject(Map<String, YamlProperties> yamlPropertiesMap,
      Map<String, YamlOutputProperties> yamlOutputPropertiesMap, YamlNode yamlNode) {
    List<String> complexFields = new ArrayList<>();
    complexFields.add(YAMLFieldNameConstants.OUTPUT_VARIABLES);
    complexFields.add(YAMLFieldNameConstants.HEADERS);

    List<YamlField> fields = yamlNode.fields();
    fields.forEach(field -> {
      if (!field.getName().equals(YAMLFieldNameConstants.UUID) && !complexFields.contains(field.getName())) {
        addFieldToPropertiesMapUnderStep(field, yamlPropertiesMap);
      }
    });

    YamlField outputVariablesField = yamlNode.getField(YAMLFieldNameConstants.OUTPUT_VARIABLES);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(outputVariablesField)) {
      addVariablesForOutputVariables(outputVariablesField, yamlOutputPropertiesMap);
    }
    YamlField headersField = yamlNode.getField(YAMLFieldNameConstants.HEADERS);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(headersField)) {
      addHeaderVariables(headersField, yamlPropertiesMap);
    }
  }

  private void addHeaderVariables(YamlField headersField, Map<String, YamlProperties> yamlPropertiesMap) {
    List<YamlNode> headerNodes = headersField.getNode().asArray();
    headerNodes.forEach(headerNode -> {
      YamlField keyField = headerNode.getField(YAMLFieldNameConstants.KEY);
      if (keyField != null) {
        addFieldToPropertiesMapUnderStep(keyField, yamlPropertiesMap);
      } else {
        throw new InvalidRequestException("Key in header field cannot be null or empty");
      }
    });
  }

  @Override
  public Class<HttpStepNode> getFieldClass() {
    return HttpStepNode.class;
  }

  @Override
  public YamlExtraProperties getStepExtraProperties(String fqnPrefix, String localNamePrefix, HttpStepNode config) {
    YamlExtraProperties stepExtraProperties = super.getStepExtraProperties(fqnPrefix, localNamePrefix, config);

    Map<String, String> outputVariablesMap = new HashMap<>();
    for (NGVariable outputVariable : config.getHttpStepInfo().getOutputVariables()) {
      outputVariablesMap.put(outputVariable.getName(), "variable");
    }
    HttpOutcome httpOutcome = HttpOutcome.builder().outputVariables(outputVariablesMap).build();

    List<String> outputExpressions = VariableCreatorHelper.getExpressionsInObject(httpOutcome, "output");
    List<YamlProperties> outputProperties = new LinkedList<>();
    for (String outputExpression : outputExpressions) {
      outputProperties.add(YamlProperties.newBuilder()
                               .setFqn(fqnPrefix + "." + outputExpression)
                               .setLocalName(localNamePrefix + "." + outputExpression)
                               .setVisible(true)
                               .build());
    }

    return YamlExtraProperties.newBuilder()
        .addAllProperties(stepExtraProperties.getPropertiesList())
        .addAllOutputProperties(outputProperties)
        .build();
  }
}
