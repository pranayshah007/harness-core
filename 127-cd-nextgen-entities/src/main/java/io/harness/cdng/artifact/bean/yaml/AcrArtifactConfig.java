/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ACR_NAME;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ACR_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("acrArtifactConfig")
@OneOfField(fields = {"tag", "tagRegex"})
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig")
public class AcrArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * Connector to connect to Azure Container Registry.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;
  /**
   * Subscriptions in Azure
   */
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> subscriptionId;
  /**
   * Registries in ACR
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> registry;
  /**
   * Images in repos need to be referenced via a path.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> repository;
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

  /**
   * Whether this config corresponds to primary artifact.
   */
  @VariableExpression(skipVariableExpression = true) boolean isPrimaryArtifact;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.ACR;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList =
        Arrays.asList(connectorRef.getValue(), subscriptionId.getValue(), registry.getValue(), repository.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    AcrArtifactConfig acrArtifactSpecConfig = (AcrArtifactConfig) overrideConfig;
    AcrArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(acrArtifactSpecConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(acrArtifactSpecConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(acrArtifactSpecConfig.getSubscriptionId())) {
      resultantConfig = resultantConfig.withSubscriptionId(acrArtifactSpecConfig.getSubscriptionId());
    }
    if (!ParameterField.isNull(acrArtifactSpecConfig.getRegistry())) {
      resultantConfig = resultantConfig.withRegistry(acrArtifactSpecConfig.getRegistry());
    }
    if (!ParameterField.isNull(acrArtifactSpecConfig.getRepository())) {
      resultantConfig = resultantConfig.withRepository(acrArtifactSpecConfig.getRepository());
    }
    if (!ParameterField.isNull(acrArtifactSpecConfig.getTag())) {
      resultantConfig = resultantConfig.withTag(acrArtifactSpecConfig.getTag());
    }
    if (!ParameterField.isNull(acrArtifactSpecConfig.getTagRegex())) {
      resultantConfig = resultantConfig.withTagRegex(acrArtifactSpecConfig.getTagRegex());
    }
    if (!ParameterField.isNull(acrArtifactSpecConfig.getDigest())) {
      resultantConfig = resultantConfig.withDigest(acrArtifactSpecConfig.getDigest());
    }
    return resultantConfig;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @JsonIgnore
  @ApiModelProperty(hidden = true)
  public List<ParameterField<String>> getStringParameterFields() {
    return Arrays.asList(connectorRef, subscriptionId, registry, repository, tag, tagRegex);
  }

  @Override
  public void validate() {
    ArtifactConfigHelper.checkTagAndTagRegex(tag, tagRegex);
  }
}
