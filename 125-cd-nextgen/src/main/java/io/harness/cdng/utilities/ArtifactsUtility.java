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
public class ArtifactsUtility {
  public JsonNode getArtifactsJsonNode() {
    String yamlField = "---\n"
        + "primary:\n";
    YamlField artifactsYamlField;
    try {
      String yamlFieldWithUuid = YamlUtils.injectUuid(yamlField);
      artifactsYamlField = YamlUtils.readTree(yamlFieldWithUuid);
    } catch (IOException e) {
      throw new InvalidRequestException("Exception while creating stageOverrides");
    }
    return artifactsYamlField.getNode().getCurrJsonNode();
  }

  public YamlField fetchArtifactYamlFieldAndSetYamlUpdates(
      YamlNode serviceConfigNode, Boolean isUseFromStage, YamlUpdates.Builder yamlUpdates) {
    if (!isUseFromStage) {
      return serviceConfigNode.getField(YamlTypes.SERVICE_DEFINITION)
          .getNode()
          .getField(YamlTypes.SPEC)
          .getNode()
          .getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    }
    YamlField stageOverrideField = serviceConfigNode.getField(YamlTypes.STAGE_OVERRIDES_CONFIG);

    if (stageOverrideField == null) {
      YamlField stageOverridesYamlField = fetchOverridesYamlField(serviceConfigNode);
      PlanCreatorUtils.setYamlUpdate(stageOverridesYamlField, yamlUpdates);
      return stageOverridesYamlField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    }
    if (stageOverrideField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG) == null) {
      YamlField artifactsYamlField = fetchArtifactYamlFieldUnderStageOverride(stageOverrideField);
      PlanCreatorUtils.setYamlUpdate(artifactsYamlField, yamlUpdates);
      return artifactsYamlField;
    }
    return stageOverrideField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
  }

  private YamlField fetchArtifactYamlFieldUnderStageOverride(YamlField stageOverride) {
    return new YamlField(YamlTypes.ARTIFACT_LIST_CONFIG,
        new YamlNode(YamlTypes.ARTIFACT_LIST_CONFIG, ArtifactsUtility.getArtifactsJsonNode(), stageOverride.getNode()));
  }

  private YamlField fetchOverridesYamlField(YamlNode serviceConfigNode) {
    return new YamlField(YamlTypes.STAGE_OVERRIDES_CONFIG,
        new YamlNode(
            YamlTypes.STAGE_OVERRIDES_CONFIG, StageOverridesUtility.getStageOverridesJsonNode(), serviceConfigNode));
  }
}