/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ECR_NAME;

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
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

/**
 * This is Yaml POJO class which may contain expressions as well.
 * Used mainly for converter layer to store yaml.
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ECR_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("ecrArtifactConfig")
@OwnedBy(CDC)
@OneOfField(fields = {"tag", "tagRegex"})
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig")
public class EcrArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * AWS connector to connect to Google Container Registry.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;
  /**
   * Region in which the artifact source is located.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> region;
  /**
   * Images in repos need to be referenced via a path.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> imagePath;
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
  /**
   * Whether this config corresponds to primary artifact.
   */
  @VariableExpression(skipVariableExpression = true) boolean isPrimaryArtifact;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.ECR;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList = Arrays.asList(connectorRef.getValue(), imagePath.getValue(), region.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public boolean isPrimaryArtifact() {
    return isPrimaryArtifact;
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    EcrArtifactConfig ecrArtifactSpecConfig = (EcrArtifactConfig) overrideConfig;
    EcrArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(ecrArtifactSpecConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(ecrArtifactSpecConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(ecrArtifactSpecConfig.getImagePath())) {
      resultantConfig = resultantConfig.withImagePath(ecrArtifactSpecConfig.getImagePath());
    }
    if (!ParameterField.isNull(ecrArtifactSpecConfig.getRegion())) {
      resultantConfig = resultantConfig.withRegion(ecrArtifactSpecConfig.getRegion());
    }
    if (!ParameterField.isNull(ecrArtifactSpecConfig.getTag())) {
      resultantConfig = resultantConfig.withTag(ecrArtifactSpecConfig.getTag());
    }
    if (!ParameterField.isNull(ecrArtifactSpecConfig.getTagRegex())) {
      resultantConfig = resultantConfig.withTagRegex(ecrArtifactSpecConfig.getTagRegex());
    }
    if (!ParameterField.isNull(ecrArtifactSpecConfig.getDigest())) {
      resultantConfig = resultantConfig.withDigest(ecrArtifactSpecConfig.getDigest());
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
