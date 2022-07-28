/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.CUSTOM_ARTIFACT_NAME;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScripts;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactSpecVisitorHelper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.List;
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
@SimpleVisitorHelper(helperClass = CustomArtifactSpecVisitorHelper.class)
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig")
public class CustomArtifactConfig implements ArtifactConfig, Visitable {
  /**
   * Identifier for artifact.
   */
  @EntityIdentifier @VariableExpression(skipVariableExpression = true) @Wither String identifier;
  /**
   * Artifact build info.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> version;
  /**
   * Version Regex
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> versionRegex;

  /**
   * Scripts Details
   */
  @NotNull @Wither CustomArtifactScripts scripts;

  /**
   * Input list
   */
  private List<NGVariable> inputs;

  /**
   * Delegate selectors
   */
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

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
    if (!ParameterField.isNull(customArtifactConfig.versionRegex)) {
      resultantConfig = resultantConfig.withVersionRegex(customArtifactConfig.getVersionRegex());
    }
    if (customArtifactConfig.getScripts() != null) {
      resultantConfig = resultantConfig.withScripts(customArtifactConfig.getScripts());
    }
    return resultantConfig;
  }
}
