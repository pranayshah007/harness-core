/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.utilities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class StageOverridesUtility {
  public JsonNode getStageOverridesJsonNode() {
    String yamlField = "---\n"
        + "artifacts:\n"
        + "  primary:\n"
        + "  sidecars: []\n"
        + "manifests:\n"
        + "   - manifest:\n"
        + "       identifier: manifestIdentifier\n"
        + "       spec:\n"
        + "       type: K8sManifest\n"
        + "configFiles:\n"
        + "   - configFile:\n"
        + "       identifier: configFileIdentifier\n"
        + "       spec:\n";

    YamlField overrideYamlField;
    try {
      String yamlFieldWithUuid = YamlUtils.injectUuid(yamlField);
      overrideYamlField = YamlUtils.readTree(yamlFieldWithUuid);
    } catch (IOException e) {
      throw new InvalidRequestException("Exception while creating stageOverrides");
    }
    return overrideYamlField.getNode().getCurrJsonNode();
  }
}
