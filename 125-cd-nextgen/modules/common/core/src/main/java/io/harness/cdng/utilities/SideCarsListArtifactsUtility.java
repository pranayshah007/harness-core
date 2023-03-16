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
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class SideCarsListArtifactsUtility {
  public JsonNode getSideCarsListJsonNode() {
    String yamlField = "---\n"
        + "- sidecar:\n"
        + "      spec:\n"
        + "      type: DockerRegistry\n"
        + "      identifier: \""
        + "dummyIdentifier"
        + "\"\n";
    YamlField sideCarsYamlField;
    try {
      String yamlFieldWithUuid = YamlUtils.injectUuid(yamlField);
      sideCarsYamlField = YamlUtils.readTree(yamlFieldWithUuid);
    } catch (IOException e) {
      throw new InvalidRequestException("Exception while creating primary field");
    }
    return sideCarsYamlField.getNode().getCurrJsonNode();
  }

  public YamlField fetchIndividualSideCarYamlField(
      YamlField sideCarsYamlField, String sideCarIdentifier, Map<String, YamlNode> sidecarIdentifierToYamlNodeMap) {
    if (sidecarIdentifierToYamlNodeMap.containsKey(sideCarIdentifier)) {
      return sidecarIdentifierToYamlNodeMap.get(sideCarIdentifier).getField(YamlTypes.SIDECAR_ARTIFACT_CONFIG);
    }

    return sideCarsYamlField.getNode().asArray().get(0).getField(YamlTypes.SIDECAR_ARTIFACT_CONFIG);
  }

  public YamlField createSideCarsArtifactYamlFieldAndSetYamlUpdate(
      YamlField artifactField, YamlUpdates.Builder yamlUpdates) {
    YamlField sideCarsYamlField = artifactField.getNode().getField(YamlTypes.SIDECARS_ARTIFACT_CONFIG);

    if (sideCarsYamlField != null && EmptyPredicate.isNotEmpty(sideCarsYamlField.getNode().asArray())) {
      return sideCarsYamlField;
    }

    sideCarsYamlField = createSideCarsYamlFieldUnderArtifacts(artifactField);
    PlanCreatorUtils.setYamlUpdate(sideCarsYamlField, yamlUpdates);
    return sideCarsYamlField;
  }

  private YamlField createSideCarsYamlFieldUnderArtifacts(YamlField artifacts) {
    return new YamlField(YamlTypes.SIDECARS_ARTIFACT_CONFIG,
        new YamlNode(YamlTypes.SIDECARS_ARTIFACT_CONFIG, SideCarsListArtifactsUtility.getSideCarsListJsonNode(),
            artifacts.getNode()));
  }
}