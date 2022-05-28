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
public class ManifestsUtility {
  public JsonNode getManifestsJsonNode() {
    String yamlField = "---\n"
        + "- manifest:\n"
        + "     identifier: manifestIdentifier\n"
        + "     spec:\n"
        + "     type: K8sManifest\n";

    YamlField manifestsYamlField;
    try {
      String yamlFieldWithUuid = YamlUtils.injectUuid(yamlField);
      manifestsYamlField = YamlUtils.readTree(yamlFieldWithUuid);
    } catch (IOException e) {
      throw new InvalidRequestException("Exception while creating stageOverrides");
    }
    return manifestsYamlField.getNode().getCurrJsonNode();
  }

  public YamlField fetchManifestsYamlFieldAndSetYamlUpdates(
      YamlNode serviceConfigNode, Boolean isUseFromStage, YamlUpdates.Builder yamlUpdates) {
    if (isUseFromStage == false) {
      return serviceConfigNode.getField(YamlTypes.SERVICE_DEFINITION)
          .getNode()
          .getField(YamlTypes.SPEC)
          .getNode()
          .getField(YamlTypes.MANIFEST_LIST_CONFIG);
    }
    YamlField stageOverrideField = serviceConfigNode.getField(YamlTypes.STAGE_OVERRIDES_CONFIG);

    if (stageOverrideField == null) {
      YamlField stageOverridesYamlField = fetchOverridesYamlField(serviceConfigNode);
      PlanCreatorUtils.setYamlUpdate(stageOverridesYamlField, yamlUpdates);
      return stageOverridesYamlField.getNode().getField(YamlTypes.MANIFEST_LIST_CONFIG);
    }
    YamlField manifestsField = stageOverrideField.getNode().getField(YamlTypes.MANIFEST_LIST_CONFIG);
    if (manifestsField == null || !EmptyPredicate.isNotEmpty(manifestsField.getNode().asArray())) {
      YamlField manifestsYamlField = fetchManifestYamlFieldUnderStageOverride(stageOverrideField);
      PlanCreatorUtils.setYamlUpdate(manifestsYamlField, yamlUpdates);
      return manifestsYamlField;
    }
    return stageOverrideField.getNode().getField(YamlTypes.MANIFEST_LIST_CONFIG);
  }

  private YamlField fetchManifestYamlFieldUnderStageOverride(YamlField stageOverride) {
    return new YamlField(YamlTypes.MANIFEST_LIST_CONFIG,
        new YamlNode(YamlTypes.MANIFEST_LIST_CONFIG, ManifestsUtility.getManifestsJsonNode(), stageOverride.getNode()));
  }

  private YamlField fetchOverridesYamlField(YamlNode serviceConfigNode) {
    return new YamlField(YamlTypes.STAGE_OVERRIDES_CONFIG,
        new YamlNode(
            YamlTypes.STAGE_OVERRIDES_CONFIG, StageOverridesUtility.getStageOverridesJsonNode(), serviceConfigNode));
  }

  public YamlField fetchIndividualManifestYamlField(YamlField manifestListYamlField,
      String individualManifestIdentifier, Map<String, YamlNode> manifestIdentifierToYamlNodeMap) {
    if (manifestIdentifierToYamlNodeMap.containsKey(individualManifestIdentifier)) {
      return manifestIdentifierToYamlNodeMap.get(individualManifestIdentifier).getField(YamlTypes.MANIFEST_CONFIG);
    }

    return manifestListYamlField.getNode().asArray().get(0).getField(YamlTypes.MANIFEST_CONFIG);
  }
}