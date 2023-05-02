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
@JsonTypeName(AMAZON_S3_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("amazonS3ArtifactConfig")
@OneOfField(fields = {"filePath", "filePathRegex"})
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig")
public class AmazonS3ArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * Amazon S3 connector.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;

  /**
   * Region.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> region;

  /**
   * Bucket name.
   */
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> bucketName;

  /**
   * FilePath Regex
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> filePathRegex;

  /**
   * Artifact FilePaths
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> filePath;

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
    List<String> valuesList = Arrays.asList(connectorRef.getValue(), filePath.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) overrideConfig;
    AmazonS3ArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(amazonS3ArtifactConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(amazonS3ArtifactConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(amazonS3ArtifactConfig.getRegion())) {
      resultantConfig = resultantConfig.withRegion(amazonS3ArtifactConfig.getRegion());
    }
    if (!ParameterField.isNull(amazonS3ArtifactConfig.getBucketName())) {
      resultantConfig = resultantConfig.withBucketName(amazonS3ArtifactConfig.getBucketName());
    }
    if (!ParameterField.isNull(amazonS3ArtifactConfig.getFilePath())) {
      resultantConfig = resultantConfig.withFilePath(amazonS3ArtifactConfig.getFilePath());
    }
    if (!ParameterField.isNull(amazonS3ArtifactConfig.getFilePathRegex())) {
      resultantConfig = resultantConfig.withFilePathRegex(amazonS3ArtifactConfig.getFilePathRegex());
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
    ArtifactConfigHelper.checkFilePathAndFilePathRegex(filePath, filePathRegex);
  }
}
