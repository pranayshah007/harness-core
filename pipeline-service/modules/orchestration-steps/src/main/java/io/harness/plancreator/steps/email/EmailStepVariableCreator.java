/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.email;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class EmailStepVariableCreator extends GenericStepVariableCreator<EmailStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.EMAIL);
  }

  @Override
  public Class<EmailStepNode> getFieldClass() {
    return EmailStepNode.class;
  }

  @Override
  protected void addVariablesInComplexObject(Map<String, YamlProperties> yamlPropertiesMap,
      Map<String, YamlOutputProperties> yamlOutputPropertiesMap, YamlNode yamlNode) {}
  private void addHeaderVariables(YamlField headersField, Map<String, YamlProperties> yamlPropertiesMap) {}
}
