/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GOOGLE_ARTIFACT_REGISTRY_NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.validation.OneOfField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(GOOGLE_ARTIFACT_REGISTRY_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("googleArtifactRegistryConfig")
@OneOfField(fields = {"version", "versionRegex"})
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig")
public class GoogleArtifactRegistryConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * GoogleArtifactRegistry connector to connect to Google Artifact Registry.
   */

  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> project;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> region;
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> repositoryName;

  @JsonProperty("package")
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> pkg;

  /**
   * Identifier for artifact.
   */
  @EntityIdentifier @VariableExpression(skipVariableExpression = true) String identifier;
  /**
   * Whether this config corresponds to primary artifact.
   */
  @VariableExpression(skipVariableExpression = true) boolean isPrimaryArtifact;
  /**
   * Version refers to exact Version number.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> version;
  /**
   * Version regex is used to get latest build from builds matching regex.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> versionRegex;
  /**
   * Digest refers to the SHA256 digest of the docker image file.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> digest;

  @JsonProperty("repositoryType")
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH, allowableValues = "docker")
  ParameterField<String> googleArtifactRegistryType;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList =
        Arrays.asList(connectorRef.getValue(), project.getValue(), repositoryName.getValue(), region.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public boolean isPrimaryArtifact() {
    return isPrimaryArtifact;
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig = (GoogleArtifactRegistryConfig) overrideConfig;
    GoogleArtifactRegistryConfig resultantConfig = this;
    if (!ParameterField.isNull(googleArtifactRegistryConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(googleArtifactRegistryConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(googleArtifactRegistryConfig.getRegion())) {
      resultantConfig = resultantConfig.withRegion(googleArtifactRegistryConfig.getRegion());
    }
    if (!ParameterField.isNull(googleArtifactRegistryConfig.getRepositoryName())) {
      resultantConfig = resultantConfig.withRepositoryName(googleArtifactRegistryConfig.getRepositoryName());
    }
    if (!ParameterField.isNull(googleArtifactRegistryConfig.getProject())) {
      resultantConfig = resultantConfig.withProject(googleArtifactRegistryConfig.getProject());
    }
    if (!ParameterField.isNull(googleArtifactRegistryConfig.getPkg())) {
      resultantConfig = resultantConfig.withPkg(googleArtifactRegistryConfig.getPkg());
    }
    if (!ParameterField.isNull(googleArtifactRegistryConfig.getDigest())) {
      resultantConfig = resultantConfig.withDigest(googleArtifactRegistryConfig.getDigest());
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
