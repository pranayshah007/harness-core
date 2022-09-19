/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utilities;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.utils.PmsConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class ResourceConstraintUtility {
  public JsonNode getResourceConstraintJsonNode(String resourceUnit, String whenCondition) {
    String yamlField = "---\n"
        + "name: \"Resource Constraint\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"ResourceConstraint\"\n"
        + "spec:\n"
        + "  name: \"" + PmsConstants.QUEUING_RC_NAME + "\"\n"
        + "  resourceUnit: \"" + resourceUnit + "\"\n"
        + "  acquireMode: \"ENSURE\"\n"
        + "  permits: 1\n"
        + "  holdingScope: STAGE";
    if (isNotEmpty(whenCondition)) {
      String whenConditionYaml = "\nwhen:\n"
          + "    stageStatus: Success\n"
          + "    condition: " + whenCondition + "\n";
      yamlField = yamlField + whenConditionYaml;
    }

    YamlField resourceConstraintYamlField;
    try {
      resourceConstraintYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    } catch (IOException e) {
      log.error("Exception occurred while creating resource constraint", e);
      throw new InvalidRequestException("Exception while creating resource constraint");
    }
    return resourceConstraintYamlField.getNode().getCurrJsonNode();
  }

  // Todo: Move to cd-service i.e 125-cd-nextgen
  public boolean isSimultaneousDeploymentsAllowed(ParameterField<Boolean> allowSimultaneousDeploymentsField) {
    if (!ParameterField.isNull(allowSimultaneousDeploymentsField) && allowSimultaneousDeploymentsField.isExpression()) {
      throw new InvalidRequestException(
          "AllowedSimultaneous Deployment field is not a fixed value during execution of pipeline.");
    }
    boolean allowSimultaneousDeployments = false;
    if (!ParameterField.isNull(allowSimultaneousDeploymentsField)) {
      allowSimultaneousDeployments = allowSimultaneousDeploymentsField.getValue();
    }

    return allowSimultaneousDeployments;
  }
}
