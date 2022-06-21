/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AMAZON_S3_NAME;

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
@JsonTypeName(AMAZON_S3_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("amazonS3ArtifactConfig")
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig")
public class AmazonS3ArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * Amazon S3 connector.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;

  /**
   * Bucket name.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> bucketName;

  /**
   * Artifact FilePaths
   */
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
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
    return ArtifactSourceType.AMAZONS3;
  }

  @Override
  public String getUniqueHash() {
    List<String> artifactPathsValues = artifactPaths.getValue();
    artifactPathsValues.add(0, connectorRef.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(artifactPathsValues);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) overrideConfig;
    AmazonS3ArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(amazonS3ArtifactConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(amazonS3ArtifactConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(amazonS3ArtifactConfig.getBucketName())) {
      resultantConfig = resultantConfig.withBucketName(amazonS3ArtifactConfig.getBucketName());
    }
    if (!ParameterField.isNull(amazonS3ArtifactConfig.getArtifactPaths())) {
      resultantConfig = resultantConfig.withArtifactPaths(amazonS3ArtifactConfig.getArtifactPaths());
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
