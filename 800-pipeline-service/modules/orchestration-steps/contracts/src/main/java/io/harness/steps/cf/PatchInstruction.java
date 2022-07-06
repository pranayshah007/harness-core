/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.cf;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SetFeatureFlagStateYaml.class, name = "SetFeatureFlagState")
  , @JsonSubTypes.Type(value = SetOnVariationYaml.class, name = "SetOnVariation"),
      @JsonSubTypes.Type(value = SetOffVariationYaml.class, name = "SetOffVariation"),
      @JsonSubTypes.Type(value = SetDefaultVariationsYaml.class, name = "SetDefaultVariations"),
      @JsonSubTypes.Type(value = AddRuleYaml.class, name = "AddRule"),
      @JsonSubTypes.Type(value = UpdateRuleYaml.class, name = "UpdateRule"),
      @JsonSubTypes.Type(value = AddTargetsToVariationTargetMapYaml.class, name = "AddTargetsToVariationTargetMap"),
      @JsonSubTypes.Type(
          value = RemoveTargetsToVariationTargetMapYaml.class, name = "RemoveTargetsToVariationTargetMap"),
      @JsonSubTypes.Type(value = AddSegmentToVariationTargetMapYaml.class, name = "AddSegmentToVariationTargetMap"),
      @JsonSubTypes.Type(
          value = RemoveSegmentToVariationTargetMapYaml.class, name = "RemoveSegmentToVariationTargetMap"),
})
@OwnedBy(HarnessTeam.CF)
public interface PatchInstruction {
  @TypeAlias("instruction_kind")
  enum Type {
    @JsonProperty("SetFeatureFlagState") SET_FEATURE_FLAG_STATE("SetFeatureFlagState"),
    @JsonProperty("SetOnVariation") SET_ON_VARIATION("SetOnVariation"),
    @JsonProperty("SetOffVariation") SET_OFF_VARIATION("SetOffVariation"),
    @JsonProperty("SetDefaultVariations") SET_DEFAULT_VARIATIONS("SetDefaultVariations"),
    @JsonProperty("AddRule") ADD_RULE("AddRule"),
    @JsonProperty("UpdateRule") UPDATE_RULE("UpdateRule"),
    @JsonProperty("AddTargetsToVariationTargetMap")
    ADD_TARGETS_TO_VARIATION_TARGET_MAP("AddTargetsToVariationTargetMap"),
    @JsonProperty("RemoveTargetsToVariationTargetMap")
    REMOVE_TARGETS_TO_VARIATION_TARGET_MAP("RemoveTargetsToVariationTargetMap"),
    @JsonProperty("AddSegmentToVariationTargetMap")
    ADD_SEGMENT_TO_VARIATION_TARGET_MAP("AddSegmentToVariationTargetMap"),
    @JsonProperty("RemoveSegmentsToVariationTargetMap")
    REMOVE_SEGMENT_TO_VARIATION_TARGET_MAP("RemoveSegmentToVariationTargetMap");
    private final String yamlName;

    Type(String yamlName) {
      this.yamlName = yamlName;
    }

    @JsonValue
    public String getYamlName() {
      return yamlName;
    }
  }
  Type getType();
}
