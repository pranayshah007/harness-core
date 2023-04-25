/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GCR_NAME;

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
import lombok.Getter;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

/**
 * This is Yaml POJO class which may contain expressions as well.
 * Used mainly for converter layer to store yaml.
 */
@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(GCR_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("gcrArtifactConfig")
@OneOfField(fields = {"tag", "tagRegex"})
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig")
public class GcrArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * GCP connector to connect to Google Container Registry.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;
  /**
   * Registry where the artifact source is located.
   */
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> registryHostname;
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
  @VariableExpression(skipVariableExpression = true) @EntityIdentifier String identifier;
  /**
   * Whether this config corresponds to primary artifact.
   */
  @VariableExpression(skipVariableExpression = true) boolean isPrimaryArtifact;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.GCR;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList = Arrays.asList(connectorRef.getValue(), registryHostname.getValue(), imagePath.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    GcrArtifactConfig gcrArtifactSpecConfig = (GcrArtifactConfig) overrideConfig;
    GcrArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(gcrArtifactSpecConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(gcrArtifactSpecConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(gcrArtifactSpecConfig.getImagePath())) {
      resultantConfig = resultantConfig.withImagePath(gcrArtifactSpecConfig.getImagePath());
    }
    if (!ParameterField.isNull(gcrArtifactSpecConfig.getRegistryHostname())) {
      resultantConfig = resultantConfig.withRegistryHostname(gcrArtifactSpecConfig.getRegistryHostname());
    }
    if (!ParameterField.isNull(gcrArtifactSpecConfig.getTag())) {
      resultantConfig = resultantConfig.withTag(gcrArtifactSpecConfig.getTag());
    }
    if (!ParameterField.isNull(gcrArtifactSpecConfig.getTagRegex())) {
      resultantConfig = resultantConfig.withTagRegex(gcrArtifactSpecConfig.getTagRegex());
    }
    if (!ParameterField.isNull(gcrArtifactSpecConfig.getDigest())) {
      resultantConfig = resultantConfig.withDigest(gcrArtifactSpecConfig.getDigest());
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
