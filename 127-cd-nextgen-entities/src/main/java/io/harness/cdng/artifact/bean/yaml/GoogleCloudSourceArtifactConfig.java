/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GOOGLE_CLOUD_SOURCE_ARTIFACT_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.GOOGLE_CLOUD_SOURCE_ARTIFACT;

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

@OwnedBy(CDC)
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(GOOGLE_CLOUD_SOURCE_ARTIFACT_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("googleCloudSourceArtifactConfig")
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.GoogleCloudSourceArtifactConfig")
public class GoogleCloudSourceArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * GCP connector to connect to Google Cloud Source.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;
  /**
   * Project in which the artifact source repository is located.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> project;
  /**
   * Repository in which the artifact source is located.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> repository;
  /**
   * Directory Path where artifact source is located.
   */
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> sourceDirectory;

  @NotNull @Wither GoogleCloudSourceFetchType fetchType;

  /**
   * branch where artifact source is located.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> branch;
  /**
   * commit id on which artifact source is located.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> commitId;
  /**
   * tag with which artifact source is located.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> tag;
  /**
   * Identifier for artifact.
   */
  @VariableExpression(skipVariableExpression = true) @EntityIdentifier String identifier;
  /**
   * Whether this config corresponds to primary artifact.
   */
  @VariableExpression(skipVariableExpression = true) boolean isPrimaryArtifact;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public ArtifactSourceType getSourceType() {
    return GOOGLE_CLOUD_SOURCE_ARTIFACT;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList =
        Arrays.asList(connectorRef.getValue(), project.getValue(), repository.getValue(), sourceDirectory.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public boolean isPrimaryArtifact() {
    return isPrimaryArtifact;
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    GoogleCloudSourceArtifactConfig googleCloudSourceArtifactConfig = (GoogleCloudSourceArtifactConfig) overrideConfig;
    GoogleCloudSourceArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(googleCloudSourceArtifactConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(googleCloudSourceArtifactConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(googleCloudSourceArtifactConfig.getProject())) {
      resultantConfig = resultantConfig.withProject(googleCloudSourceArtifactConfig.getProject());
    }
    if (!ParameterField.isNull(googleCloudSourceArtifactConfig.getRepository())) {
      resultantConfig = resultantConfig.withRepository(googleCloudSourceArtifactConfig.getRepository());
    }
    if (!ParameterField.isNull(googleCloudSourceArtifactConfig.getSourceDirectory())) {
      resultantConfig = resultantConfig.withSourceDirectory(googleCloudSourceArtifactConfig.getSourceDirectory());
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
