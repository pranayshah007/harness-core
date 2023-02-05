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
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class AzureConfigsUtility {
  public YamlField fetchAzureConfigYamlFieldAndSetYamlUpdates(
      YamlNode serviceConfigNode, boolean isUseFromStage, YamlUpdates.Builder yamlUpdates, String yamlType) {
    if (!isUseFromStage) {
      return serviceConfigNode.getField(YamlTypes.SERVICE_DEFINITION)
          .getNode()
          .getField(YamlTypes.SPEC)
          .getNode()
          .getField(yamlType);
    }
    YamlField stageOverrideField = serviceConfigNode.getField(YamlTypes.STAGE_OVERRIDES_CONFIG);

    if (stageOverrideField == null) {
      YamlField stageOverridesYamlField = fetchOverridesYamlField(serviceConfigNode);
      PlanCreatorUtils.setYamlUpdate(stageOverridesYamlField, yamlUpdates);
      return stageOverridesYamlField.getNode().getField(yamlType);
    }
    YamlField azureConfigField = stageOverrideField.getNode().getField(yamlType);
    if (azureConfigField == null || !EmptyPredicate.isNotEmpty(azureConfigField.getNode().asArray())) {
      YamlField azureConfigYamlField = fetchAzureConfigYamlFieldUnderStageOverride(stageOverrideField, yamlType);
      PlanCreatorUtils.setYamlUpdate(azureConfigYamlField, yamlUpdates);
      return azureConfigYamlField;
    }
    return stageOverrideField.getNode().getField(yamlType);
  }

  private YamlField fetchOverridesYamlField(YamlNode serviceConfigNode) {
    return new YamlField(YamlTypes.STAGE_OVERRIDES_CONFIG,
        new YamlNode(
            YamlTypes.STAGE_OVERRIDES_CONFIG, StageOverridesUtility.getStageOverridesJsonNode(), serviceConfigNode));
  }

  private YamlField fetchAzureConfigYamlFieldUnderStageOverride(YamlField stageOverride, String yamlType) {
    return new YamlField(yamlType, new YamlNode(yamlType, getAzureConfigJsonNode(), stageOverride.getNode()));
  }

  public JsonNode getAzureConfigJsonNode() {
    String yamlField = "---\n"
        + "spec:\n";

    YamlField azureConfigYamlField;
    try {
      String yamlFieldWithUuid = YamlUtils.injectUuid(yamlField);
      azureConfigYamlField = YamlUtils.readTree(yamlFieldWithUuid);
    } catch (IOException ex) {
      throw new InvalidRequestException("Exception while creating stageOverrides", ex);
    }

    return azureConfigYamlField.getNode().getCurrJsonNode();
  }

  public Dependency getDependencyMetadata(final String azureConfigPlanNodeId, StepParameters stepParameters,
      KryoSerializer kryoSerializer, String metadataKey) {
    Map<String, ByteString> metadataDependency =
        prepareMetadataForAzureConfigPlanCreator(azureConfigPlanNodeId, stepParameters, kryoSerializer, metadataKey);
    return Dependency.newBuilder().putAllMetadata(metadataDependency).build();
  }

  private Map<String, ByteString> prepareMetadataForAzureConfigPlanCreator(
      String azureConfigPlanNodeId, StepParameters stepParameters, KryoSerializer kryoSerializer, String metadataKey) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(azureConfigPlanNodeId)));
    metadataDependency.put(metadataKey, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(stepParameters)));
    return metadataDependency;
  }
}
