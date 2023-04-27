/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME;

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
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
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

/**
 * This is Yaml POJO class which may contain expressions as well.
 * Used mainly for converter layer to store yaml.
 */
@OwnedBy(CDP)
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ARTIFACTORY_REGISTRY_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("artifactoryRegistryArtifactConfig")
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig")
public class ArtifactoryRegistryArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * Artifactory registry connector.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;
  /**
   * Repo name.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> repository;
  /**
   * Images in repos need to be referenced via a path.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> artifactPath;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> artifactPathFilter;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> artifactDirectory;
  /**
   * Repo format.
   */
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH, allowableValues = "docker, generic")
  @Wither
  ParameterField<String> repositoryFormat;
  /**
   * repo server hostname.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> repositoryUrl;
  /**
   * Tag refers to exact tag number.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> tag;
  /**
   * Tag regex is used to get latest build from builds matching regex.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> tagRegex;
  /**
   * Digest refers to the SHA256 digest of the docker image file.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> digest;
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
    return ArtifactSourceType.ARTIFACTORY_REGISTRY;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList = Arrays.asList(connectorRef.getValue(), artifactPath.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
        (ArtifactoryRegistryArtifactConfig) overrideConfig;
    ArtifactoryRegistryArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(artifactoryRegistryArtifactConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(artifactoryRegistryArtifactConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(artifactoryRegistryArtifactConfig.getRepository())) {
      resultantConfig = resultantConfig.withArtifactPath(artifactoryRegistryArtifactConfig.getRepository());
    }
    if (!ParameterField.isNull(artifactoryRegistryArtifactConfig.getArtifactPath())) {
      resultantConfig = resultantConfig.withArtifactPath(artifactoryRegistryArtifactConfig.getArtifactPath());
    }
    if (!ParameterField.isNull(artifactoryRegistryArtifactConfig.getRepositoryUrl())) {
      resultantConfig = resultantConfig.withArtifactPath(artifactoryRegistryArtifactConfig.getRepositoryUrl());
    }
    if (!ParameterField.isNull(artifactoryRegistryArtifactConfig.getTag())) {
      resultantConfig = resultantConfig.withTag(artifactoryRegistryArtifactConfig.getTag());
    }
    if (!ParameterField.isNull(artifactoryRegistryArtifactConfig.getTagRegex())) {
      resultantConfig = resultantConfig.withTagRegex(artifactoryRegistryArtifactConfig.getTagRegex());
    }
    if (!ParameterField.isNull(artifactoryRegistryArtifactConfig.getArtifactDirectory())) {
      resultantConfig = resultantConfig.withArtifactDirectory(artifactoryRegistryArtifactConfig.getArtifactDirectory());
    }
    if (!ParameterField.isNull(artifactoryRegistryArtifactConfig.getArtifactPathFilter())) {
      resultantConfig =
          resultantConfig.withArtifactPathFilter(artifactoryRegistryArtifactConfig.getArtifactPathFilter());
    }
    if (!ParameterField.isNull(artifactoryRegistryArtifactConfig.getDigest())) {
      resultantConfig = resultantConfig.withDigest(artifactoryRegistryArtifactConfig.getDigest());
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
