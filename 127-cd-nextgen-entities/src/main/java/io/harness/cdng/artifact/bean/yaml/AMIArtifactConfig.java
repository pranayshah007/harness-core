/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AMI_ARTIFACTS_NAME;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.validation.OneOfField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
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
@JsonTypeName(AMI_ARTIFACTS_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("amiArtifactConfig")
@OneOfField(fields = {"version", "versionRegex"})
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig")
public class AMIArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * AWS connector.
   */
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> connectorRef;

  /**
   * Region - AWS supported regions
   */
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> region;

  /**
   * AMI Tags
   */
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.AMI_TAG_LIST_CLASSPATH)
  @Wither
  ParameterField<List<AMITag>> tags;

  /**
   * AMI Filters
   */
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.AMI_FILTER_LIST_CLASSPATH)
  @Wither
  ParameterField<List<AMIFilter>> filters;

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

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.AMI;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList = Arrays.asList(connectorRef.getValue(), region.getValue());

    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    AMIArtifactConfig azureArtifactConfig = (AMIArtifactConfig) overrideConfig;

    AMIArtifactConfig resultantConfig = this;

    if (!ParameterField.isNull(azureArtifactConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(azureArtifactConfig.getConnectorRef());
    }

    if (!ParameterField.isNull(azureArtifactConfig.getRegion())) {
      resultantConfig = resultantConfig.withRegion(azureArtifactConfig.getRegion());
    }

    if (!ParameterField.isNull(azureArtifactConfig.getVersion())) {
      resultantConfig = resultantConfig.withVersion(azureArtifactConfig.getVersion());
    }

    if (!ParameterField.isNull(azureArtifactConfig.getVersionRegex())) {
      resultantConfig = resultantConfig.withVersionRegex(azureArtifactConfig.getVersionRegex());
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
