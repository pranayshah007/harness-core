package io.harness.steps.matrix;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGInstanceUnitType;

import software.wings.utils.ArtifactType;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CDP)
public class StrategyParameters {
  @JsonProperty("instances") Integer instances;
  @JsonProperty("unitType") NGInstanceUnitType unitType;
  @JsonProperty("phases") Integer[] phases;
  @JsonProperty("artifactType") ArtifactType artifactType;
}
