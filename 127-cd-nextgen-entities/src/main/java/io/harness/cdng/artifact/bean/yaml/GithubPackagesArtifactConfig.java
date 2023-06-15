/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GITHUB_PACKAGES_NAME;

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
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(GITHUB_PACKAGES_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("githubPackageArtifactConfig")
@OneOfField(fields = {"version", "versionRegex"})
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.GithubPackageArtifactConfig")
public class GithubPackagesArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * Git connector.
   */
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> connectorRef;

  /**
   * Package Type.
   */
  @NotNull
  @ApiModelProperty(
      dataType = SwaggerConstants.STRING_CLASSPATH, allowableValues = "npm, maven, rubygems, nuget, container")
  @Wither
  ParameterField<String> packageType;

  /**
   * packageName.
   */
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> packageName;

  /**
   * Organization
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> org;

  /**
   * Version
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> version;

  /**
   * Version Regex
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> versionRegex;
  /**
   * Digest refers to the SHA256 digest of the docker image file.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> digest;

  /**
   * Identifier for artifact.
   */
  @EntityIdentifier @VariableExpression(skipVariableExpression = true) String identifier;

  /**
   * Whether this config corresponds to primary artifact.
   */
  @VariableExpression(skipVariableExpression = true) boolean primaryArtifact;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> groupId;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> artifactId;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> repository;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> user;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> extension;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.GITHUB_PACKAGES;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList = Arrays.asList(connectorRef.getValue(), packageName.getValue(), version.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig = (GithubPackagesArtifactConfig) overrideConfig;

    GithubPackagesArtifactConfig resultantConfig = this;

    if (!ParameterField.isNull(githubPackagesArtifactConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(githubPackagesArtifactConfig.getConnectorRef());
    }

    if (!ParameterField.isNull(githubPackagesArtifactConfig.getPackageType())) {
      resultantConfig = resultantConfig.withPackageType(githubPackagesArtifactConfig.getPackageType());
    }

    if (!ParameterField.isNull(githubPackagesArtifactConfig.getPackageName())) {
      resultantConfig = resultantConfig.withPackageName(githubPackagesArtifactConfig.getPackageName());
    }

    if (!ParameterField.isNull(githubPackagesArtifactConfig.getOrg())) {
      resultantConfig = resultantConfig.withOrg(githubPackagesArtifactConfig.getOrg());
    }

    if (!ParameterField.isNull(githubPackagesArtifactConfig.getVersion())) {
      resultantConfig = resultantConfig.withVersion(githubPackagesArtifactConfig.getVersion());
    }

    if (!ParameterField.isNull(githubPackagesArtifactConfig.getVersionRegex())) {
      resultantConfig = resultantConfig.withVersionRegex(githubPackagesArtifactConfig.getVersionRegex());
    }

    if (!ParameterField.isNull(githubPackagesArtifactConfig.getDigest())) {
      resultantConfig = resultantConfig.withDigest(githubPackagesArtifactConfig.getDigest());
    }

    return resultantConfig;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();

    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);

    return connectorRefMap;
  }

  @Override
  public void validate() {
    ArtifactConfigHelper.checkVersionAndVersionRegex(version, versionRegex);
  }
}
