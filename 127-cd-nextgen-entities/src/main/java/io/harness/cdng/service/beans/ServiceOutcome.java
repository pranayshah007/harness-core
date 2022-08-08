/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ToBeDeleted;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("serviceOutcome")
@JsonTypeName("serviceOutcome")
@OwnedBy(CDC)
@ToBeDeleted
@RecasterAlias("io.harness.cdng.service.beans.ServiceOutcome")
public class ServiceOutcome implements Outcome {
  String identifier;
  String name;
  String description;
  String type;
  @Builder.Default Map<String, String> tags = new HashMap<>();

  // For expressions
  @Singular Map<String, Object> variables;
  @Singular Map<String, Map<String, Object>> artifacts;
  @Singular Map<String, Map<String, Object>> manifests;

  // When changing the name of this variable, change ImagePullSecretFunctor.
  ArtifactsOutcome artifactsResult;
  @Singular Map<String, ManifestOutcome> manifestResults;

  @Singular Map<String, ArtifactsWrapperOutcome> artifactOverrideSets;
  @Singular Map<String, VariablesWrapperOutcome> variableOverrideSets;
  @Singular Map<String, ManifestsWrapperOutcome> manifestOverrideSets;

  StageOverridesOutcome stageOverrides;

  public String getServiceDefinitionType() {
    return type;
  }

  @Data
  @Builder
  @TypeAlias("serviceOutcome_artifactsOutcome")
  @JsonTypeName("serviceOutcome_artifactsOutcome")
  @RecasterAlias("io.harness.cdng.service.beans.ServiceOutcome$ArtifactsOutcome")
  public static class ArtifactsOutcome implements Outcome {
    private ArtifactOutcome primary;
    @Singular private Map<String, ArtifactOutcome> sidecars;
  }

  @Data
  @Builder
  @TypeAlias("serviceOutcome_stageOverridesOutcome")
  @JsonTypeName("serviceOutcome_stageOverridesOutcome")
  @RecasterAlias("io.harness.cdng.service.beans.ServiceOutcome$StageOverridesOutcome")
  public static class StageOverridesOutcome implements Outcome {
    Map<String, Object> variables;
    ArtifactsOutcome artifacts;
    @Singular Map<String, ManifestOutcome> manifests;

    ParameterField<List<String>> useVariableOverrideSets;
    ParameterField<List<String>> useArtifactOverrideSets;
    ParameterField<List<String>> useManifestOverrideSets;
  }

  @Data
  @Builder
  @TypeAlias("serviceOutcome_artifactsWrapperOutcome")
  @JsonTypeName("serviceOutcome_artifactsWrapperOutcome")
  @RecasterAlias("io.harness.cdng.service.beans.ServiceOutcome$ArtifactsWrapperOutcome")
  public static class ArtifactsWrapperOutcome implements Outcome {
    ArtifactsOutcome artifacts;
  }

  @Data
  @Builder
  @TypeAlias("serviceOutcome_manifestsWrapperOutcome")
  @JsonTypeName("serviceOutcome_manifestsWrapperOutcome")
  @RecasterAlias("io.harness.cdng.service.beans.ServiceOutcome$ManifestsWrapperOutcome")
  public static class ManifestsWrapperOutcome implements Outcome {
    Map<String, ManifestOutcome> manifests;
  }

  @Data
  @Builder
  @TypeAlias("serviceOutcome_variablesWrapperOutcome")
  @JsonTypeName("serviceOutcome_variablesWrapperOutcome")
  @RecasterAlias("io.harness.cdng.service.beans.ServiceOutcome$VariablesWrapperOutcome")
  public static class VariablesWrapperOutcome implements Outcome {
    Map<String, Object> variables;
  }
}
