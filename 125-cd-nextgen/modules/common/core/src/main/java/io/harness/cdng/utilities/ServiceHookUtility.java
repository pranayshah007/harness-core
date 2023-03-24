/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
public class ServiceHookUtility {
  public static YamlField fetchIndividualServiceHookYamlField(
      final String serviceHookIdentifier, YamlField serviceHooksYamlField) {
    Map<String, YamlNode> serviceHookIdentifierToYamlNodeMap =
        getServiceHookIdentifierToYamlNodeMap(serviceHooksYamlField);

    if (serviceHookIdentifierToYamlNodeMap.containsKey(serviceHookIdentifier)) {
      return serviceHookIdentifierToYamlNodeMap.get(serviceHookIdentifier).getField(YamlTypes.PRE_HOOK);
    }

    return serviceHooksYamlField.getNode().asArray().get(0).getField(YamlTypes.PRE_HOOK);
  }

  @NotNull
  public static Map<String, YamlNode> getServiceHookIdentifierToYamlNodeMap(YamlField serviceHooksYamlField) {
    List<YamlNode> yamlNodes = Optional.of(serviceHooksYamlField.getNode().asArray()).orElse(Collections.emptyList());
    return yamlNodes.stream().collect(
        Collectors.toMap(e -> e.getField(YamlTypes.PRE_HOOK).getNode().getIdentifier(), k -> k));
  }

  public YamlField fetchServiceHooksYamlFieldAndSetYamlUpdates(
      YamlNode serviceConfigNode, boolean isUseFromStage, YamlUpdates.Builder yamlUpdates) {
    if (!isUseFromStage) {
      return serviceConfigNode.getField(YamlTypes.SERVICE_DEFINITION)
          .getNode()
          .getField(YamlTypes.SPEC)
          .getNode()
          .getField(YamlTypes.SERVICE_HOOKS);
    }
    YamlField stageOverrideField = serviceConfigNode.getField(YamlTypes.STAGE_OVERRIDES_CONFIG);

    if (stageOverrideField == null) {
      YamlField stageOverridesYamlField = fetchOverridesYamlField(serviceConfigNode);
      PlanCreatorUtils.setYamlUpdate(stageOverridesYamlField, yamlUpdates);
      return stageOverridesYamlField.getNode().getField(YamlTypes.SERVICE_HOOKS);
    }
    YamlField serviceHooksField = stageOverrideField.getNode().getField(YamlTypes.SERVICE_HOOKS);
    if (serviceHooksField == null || !EmptyPredicate.isNotEmpty(serviceHooksField.getNode().asArray())) {
      YamlField serviceHooksFieldYamlField = fetchServiceHooksFieldYamlFieldUnderStageOverride(stageOverrideField);
      PlanCreatorUtils.setYamlUpdate(serviceHooksFieldYamlField, yamlUpdates);
      return serviceHooksFieldYamlField;
    }

    return stageOverrideField.getNode().getField(YamlTypes.SERVICE_HOOKS);
  }

  private YamlField fetchOverridesYamlField(YamlNode serviceConfigNode) {
    return new YamlField(YamlTypes.STAGE_OVERRIDES_CONFIG,
        new YamlNode(
            YamlTypes.STAGE_OVERRIDES_CONFIG, StageOverridesUtility.getStageOverridesJsonNode(), serviceConfigNode));
  }

  private YamlField fetchServiceHooksFieldYamlFieldUnderStageOverride(YamlField stageOverride) {
    return new YamlField(YamlTypes.SERVICE_HOOKS,
        new YamlNode(YamlTypes.SERVICE_HOOKS, getConfigFilesJsonNode(), stageOverride.getNode()));
  }

  public JsonNode getConfigFilesJsonNode() {
    // preHook, postHook will give problem
    String yamlField = "---\n"
        + "- preHook:\n"
        + "     identifier: serviceHookIdentifier\n"
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
