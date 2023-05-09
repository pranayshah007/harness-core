/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AZURE_ARTIFACTS_NAME;

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
import lombok.Getter;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(AZURE_ARTIFACTS_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("azureArtifactConfig")
@OneOfField(fields = {"version", "versionRegex"})
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig")
public class AzureArtifactsConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * Azure Artifacts connector.
   */
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> connectorRef;

  /**
   * PackageType - maven/nuget.
   */
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH, allowableValues = "maven, nuget, upack")
  @Wither
  ParameterField<String> packageType;

  /**
   * Feed Scope.
   */
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH, allowableValues = "project, org")
  @Wither
  ParameterField<String> scope;

  /**
   * Project
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> project;

  /**
   * Feed
   */
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> feed;

  /**
   * PackageName
   */
  @JsonProperty("package")
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> packageName;

  /**
   * Version
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> version;

  /**
   * Version Regex
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> versionRegex;

  /**
   * Identifier for artifact.
   */
  @EntityIdentifier @VariableExpression(skipVariableExpression = true) String identifier;

  /**
   * Whether this config corresponds to primary artifact.
   * */
  @VariableExpression(skipVariableExpression = true) boolean primaryArtifact;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.AZURE_ARTIFACTS;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList =
        Arrays.asList(connectorRef.getValue(), feed.getValue(), packageName.getValue(), version.getValue());

    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) overrideConfig;

    AzureArtifactsConfig resultantConfig = this;

    if (!ParameterField.isNull(azureArtifactsConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(azureArtifactsConfig.getConnectorRef());
    }

    if (!ParameterField.isNull(azureArtifactsConfig.getPackageType())) {
      resultantConfig = resultantConfig.withPackageType(azureArtifactsConfig.getPackageType());
    }

    if (!ParameterField.isNull(azureArtifactsConfig.getScope())) {
      resultantConfig = resultantConfig.withScope(azureArtifactsConfig.getScope());
    }

    if (!ParameterField.isNull(azureArtifactsConfig.getProject())) {
      resultantConfig = resultantConfig.withProject(azureArtifactsConfig.getProject());
    }

    if (!ParameterField.isNull(azureArtifactsConfig.getFeed())) {
      resultantConfig = resultantConfig.withFeed(azureArtifactsConfig.getFeed());
    }

    if (!ParameterField.isNull(azureArtifactsConfig.getPackageName())) {
      resultantConfig = resultantConfig.withPackageName(azureArtifactsConfig.getPackageName());
    }

    if (!ParameterField.isNull(azureArtifactsConfig.getVersion())) {
      resultantConfig = resultantConfig.withVersion(azureArtifactsConfig.getVersion());
    }

    if (!ParameterField.isNull(azureArtifactsConfig.getVersionRegex())) {
      resultantConfig = resultantConfig.withVersionRegex(azureArtifactsConfig.getVersionRegex());
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
