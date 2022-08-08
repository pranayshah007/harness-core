/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.utilities;

import static io.harness.annotations.dev.HarnessTeam.CDP;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@UtilityClass
public class ConfigFileUtility {
  public static YamlField fetchIndividualConfigFileYamlField(
      final String configFileIdentifier, YamlField configFilesYamlField) {
    Map<String, YamlNode> configFileIdentifierToYamlNodeMap =
        getConfigFileIdentifierToYamlNodeMap(configFilesYamlField);

    if (configFileIdentifierToYamlNodeMap.containsKey(configFileIdentifier)) {
      return configFileIdentifierToYamlNodeMap.get(configFileIdentifier).getField(YamlTypes.CONFIG_FILE);
    }

    return configFilesYamlField.getNode().asArray().get(0).getField(YamlTypes.CONFIG_FILE);
  }

  @NotNull
  public static Map<String, YamlNode> getConfigFileIdentifierToYamlNodeMap(YamlField configFilesYamlField) {
    List<YamlNode> yamlNodes = Optional.of(configFilesYamlField.getNode().asArray()).orElse(Collections.emptyList());
    return yamlNodes.stream().collect(
        Collectors.toMap(e -> e.getField(YamlTypes.CONFIG_FILE).getNode().getIdentifier(), k -> k));
  }

  public YamlField fetchConfigFilesYamlFieldAndSetYamlUpdates(
      YamlNode serviceConfigNode, boolean isUseFromStage, YamlUpdates.Builder yamlUpdates) {
    if (!isUseFromStage) {
      return serviceConfigNode.getField(YamlTypes.SERVICE_DEFINITION)
          .getNode()
          .getField(YamlTypes.SPEC)
          .getNode()
          .getField(YamlTypes.CONFIG_FILES);
    }
    YamlField stageOverrideField = serviceConfigNode.getField(YamlTypes.STAGE_OVERRIDES_CONFIG);

    if (stageOverrideField == null) {
      YamlField stageOverridesYamlField = fetchOverridesYamlField(serviceConfigNode);
      PlanCreatorUtils.setYamlUpdate(stageOverridesYamlField, yamlUpdates);
      return stageOverridesYamlField.getNode().getField(YamlTypes.CONFIG_FILES);
    }
    YamlField configFilesField = stageOverrideField.getNode().getField(YamlTypes.CONFIG_FILES);
    if (configFilesField == null || !EmptyPredicate.isNotEmpty(configFilesField.getNode().asArray())) {
      YamlField configFilesYamlField = fetchConfigFilesYamlFieldUnderStageOverride(stageOverrideField);
      PlanCreatorUtils.setYamlUpdate(configFilesYamlField, yamlUpdates);
      return configFilesYamlField;
    }

    return stageOverrideField.getNode().getField(YamlTypes.CONFIG_FILES);
  }

  private YamlField fetchOverridesYamlField(YamlNode serviceConfigNode) {
    return new YamlField(YamlTypes.STAGE_OVERRIDES_CONFIG,
        new YamlNode(
            YamlTypes.STAGE_OVERRIDES_CONFIG, StageOverridesUtility.getStageOverridesJsonNode(), serviceConfigNode));
  }

  private YamlField fetchConfigFilesYamlFieldUnderStageOverride(YamlField stageOverride) {
    return new YamlField(YamlTypes.CONFIG_FILES,
        new YamlNode(YamlTypes.CONFIG_FILES, getConfigFilesJsonNode(), stageOverride.getNode()));
  }

  public JsonNode getConfigFilesJsonNode() {
    String yamlField = "---\n"
        + "- configFile:\n"
        + "     identifier: configFileIdentifier\n"
        + "     spec:\n";

    YamlField configFilesYamlField;
    try {
      String yamlFieldWithUuid = YamlUtils.injectUuid(yamlField);
      configFilesYamlField = YamlUtils.readTree(yamlFieldWithUuid);
    } catch (IOException ex) {
      throw new InvalidRequestException("Exception while creating stageOverrides", ex);
    }

    return configFilesYamlField.getNode().getCurrJsonNode();
  }
}
