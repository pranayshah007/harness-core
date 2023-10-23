/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml.nexusartifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.BAMBOO_ARTIFACTS_NAME;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.visitor.helpers.SecretConnectorRefExtractorHelper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(BAMBOO_ARTIFACTS_NAME)
@SimpleVisitorHelper(helperClass = SecretConnectorRefExtractorHelper.class)
@TypeAlias("bambooArtifactConfig")
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.BambooArtifactConfig")
public class BambooArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * Jenkins ConnectorDisconnectHandler.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;

  /**
   * Job name.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> planKey;

  /**
   * Build/Version
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> build;

  /**
   * Artifact FilePaths
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {runtime})
  @Wither
  ParameterField<List<String>> artifactPaths;

  /**
   * Identifier for artifact.
   */
  @EntityIdentifier @VariableExpression(skipVariableExpression = true) String identifier;
  /** Whether this config corresponds to primary artifact.*/
  @VariableExpression(skipVariableExpression = true) boolean primaryArtifact;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.BAMBOO;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList = Arrays.asList(connectorRef.getValue(), planKey.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    BambooArtifactConfig bambooArtifactConfig = (BambooArtifactConfig) overrideConfig;
    BambooArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(bambooArtifactConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(bambooArtifactConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(bambooArtifactConfig.getPlanKey())) {
      resultantConfig = resultantConfig.withPlanKey(bambooArtifactConfig.getPlanKey());
    }
    if (!ParameterField.isNull(bambooArtifactConfig.getArtifactPaths())) {
      resultantConfig = resultantConfig.withArtifactPaths(bambooArtifactConfig.getArtifactPaths());
    }
    if (!ParameterField.isNull(bambooArtifactConfig.getBuild())) {
      resultantConfig = resultantConfig.withBuild(bambooArtifactConfig.getBuild());
    }
    return resultantConfig;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }
}
