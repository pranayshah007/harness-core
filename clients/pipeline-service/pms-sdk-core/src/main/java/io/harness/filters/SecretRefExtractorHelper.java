/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filters;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class SecretRefExtractorHelper {
  public Map<String, ParameterField<SecretRefData>> extractSecretRefsFromVariables(YamlField variablesField) {
    try {
      List<YamlNode> variables = variablesField.getNode().asArray();
      Map<String, ParameterField<SecretRefData>> fqnToSecretRefs = new HashMap<>();
      for (YamlNode variable : variables) {
        YamlField uuidNode = variable.getField(YAMLFieldNameConstants.UUID);
        if (uuidNode != null) {
          String fqn = YamlUtils.getFullyQualifiedName(uuidNode.getNode());
          if (variable.getType().equals(NGVariableType.SECRET.getYamlProperty())) {
            SecretNGVariable secretNGVariable = YamlUtils.read(variable.toString(), SecretNGVariable.class);
            fqnToSecretRefs.put(fqn, secretNGVariable.getValue());
          }
        }
      }
      return fqnToSecretRefs;
    } catch (Exception ex) {
      throw new InvalidRequestException("Error fetching variables", ex.getCause());
    }
  }
}
