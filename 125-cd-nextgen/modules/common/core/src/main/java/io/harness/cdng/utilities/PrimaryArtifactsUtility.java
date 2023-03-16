/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.utilities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PrimaryArtifactsUtility {
  public JsonNode getArtifactsJsonNode() {
    String yamlField = "---\n"
        + "type: DockerRegistry\n"
        + "spec:\n";
    YamlField primaryYamlField;
    try {
      String yamlFieldWithUuid = YamlUtils.injectUuid(yamlField);
      primaryYamlField = YamlUtils.readTree(yamlFieldWithUuid);
    } catch (IOException e) {
      throw new InvalidRequestException("Exception while creating primary field");
    }
    return primaryYamlField.getNode().getCurrJsonNode();
  }

  public YamlField createPrimaryArtifactYamlFieldAndSetYamlUpdate(
      YamlField artifactField, YamlUpdates.Builder yamlUpdates) {
    YamlField primaryYamlField = artifactField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT);

    if (primaryYamlField != null) {
      return primaryYamlField;
    }

    primaryYamlField = createPrimaryYamlFieldUnderArtifacts(artifactField);
    PlanCreatorUtils.setYamlUpdate(primaryYamlField, yamlUpdates);
    return primaryYamlField;
  }

  private YamlField createPrimaryYamlFieldUnderArtifacts(YamlField stageOverride) {
    return new YamlField(YamlTypes.PRIMARY_ARTIFACT,
        new YamlNode(
            YamlTypes.PRIMARY_ARTIFACT, PrimaryArtifactsUtility.getArtifactsJsonNode(), stageOverride.getNode()));
  }
}