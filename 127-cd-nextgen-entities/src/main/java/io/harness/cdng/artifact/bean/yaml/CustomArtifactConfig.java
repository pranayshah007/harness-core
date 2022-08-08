/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.CUSTOM_ARTIFACT_NAME;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@OwnedBy(CDC)
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TypeAlias("customArtifactConfig")
@JsonTypeName(CUSTOM_ARTIFACT_NAME)
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig")
public class CustomArtifactConfig implements ArtifactConfig {
  /**
   * Identifier for artifact.
   */
  @EntityIdentifier @VariableExpression(skipVariableExpression = true) private String identifier;
  /**
   * Artifact build number.
   */
  @NotNull @YamlSchemaTypes({string}) @Wither private ParameterField<String> version;

  /** Whether this config corresponds to primary artifact.*/
  @VariableExpression(skipVariableExpression = true) private boolean primaryArtifact;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.CUSTOM_ARTIFACT;
  }

  @Override
  public String getUniqueHash() {
    return ArtifactUtils.generateUniqueHashFromStringList(Collections.singletonList(version.getValue()));
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    CustomArtifactConfig customArtifactConfig = (CustomArtifactConfig) overrideConfig;
    CustomArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(customArtifactConfig.getVersion())) {
      resultantConfig = resultantConfig.withVersion(customArtifactConfig.getVersion());
    }

    return resultantConfig;
  }
}
