/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.NEXUS3_REGISTRY_NAME;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.integer;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryConfigSpec;
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
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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

/**
 * This is Yaml POJO class which may contain expressions as well.
 * Used mainly for converter layer to store yaml.
 */
@OwnedBy(CDP)
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(NEXUS3_REGISTRY_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("nexusRegistryArtifactConfig")
@OneOfField(fields = {"tag", "tagRegex"})
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig")
public class NexusRegistryArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * Nexus registry connector.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;
  /**
   * Repo name.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> repository;
  /**
   * Artifacts in repos need to be referenced via a path.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> artifactPath;

  /**
   * Repo port.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @YamlSchemaTypes(value = {integer})
  @Wither
  ParameterField<String> repositoryPort;

  /**
   * repo server hostname.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> repositoryUrl;
  /**
   * Repo format.
   */
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH, allowableValues = "docker, maven, npm, nuget, raw")
  @Wither
  ParameterField<String> repositoryFormat;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "repositoryFormat", include = EXTERNAL_PROPERTY, visible = true)
  NexusRegistryConfigSpec nexusRegistryConfigSpec;

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
  String metadata;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.NEXUS3_REGISTRY;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList = Arrays.asList(connectorRef.getValue(), repository.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) overrideConfig;
    NexusRegistryArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(nexusRegistryArtifactConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(nexusRegistryArtifactConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(nexusRegistryArtifactConfig.getTag())) {
      resultantConfig = resultantConfig.withTag(nexusRegistryArtifactConfig.getTag());
    }
    if (!ParameterField.isNull(nexusRegistryArtifactConfig.getTagRegex())) {
      resultantConfig = resultantConfig.withTagRegex(nexusRegistryArtifactConfig.getTagRegex());
    }
    if (!ParameterField.isNull(nexusRegistryArtifactConfig.getDigest())) {
      resultantConfig = resultantConfig.withDigest(nexusRegistryArtifactConfig.getDigest());
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
    ArtifactConfigHelper.checkTagAndTagRegex(tag, tagRegex);
  }
}
