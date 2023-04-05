/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.SshWinRmAzureInfraMapping;
import io.harness.cdng.infra.beans.SshWinRmAzureInfraMapping.SshWinRmAzureInfraMappingBuilder;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.infra.HostConnectionTypeKind;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(InfrastructureKind.SSH_WINRM_AZURE)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("SshWinRmAzureInfrastructure")
@RecasterAlias("io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure")
public class SshWinRmAzureInfrastructure
    extends InfrastructureDetailsAbstract implements SshWinRmInfrastructure, AzureInfrastructure {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> connectorRef;

  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> subscriptionId;

  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> resourceGroup;

  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> credentialsRef;

  @YamlSchemaTypes({string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_MAP_CLASSPATH)
  @Wither
  ParameterField<Map<String, String>> tags;

  @NotNull
  @NotEmpty
  @ApiModelProperty(
      dataType = SwaggerConstants.STRING_CLASSPATH, allowableValues = HostConnectionTypeKind.AZURE_ALLOWABLE_VALUES)
  @Wither
  ParameterField<String> hostConnectionType;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public InfraMapping getInfraMapping() {
    SshWinRmAzureInfraMappingBuilder builder = SshWinRmAzureInfraMapping.builder()
                                                   .connectorRef(connectorRef.getValue())
                                                   .subscriptionId(subscriptionId.getValue())
                                                   .resourceGroup(resourceGroup.getValue())
                                                   .credentialsRef(credentialsRef.getValue())
                                                   .hostConnectionType(hostConnectionType.getValue());

    if (tags != null) {
      builder.tags(tags.getValue());
    }

    return builder.build();
  }

  @Override
  public String getKind() {
    return InfrastructureKind.SSH_WINRM_AZURE;
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public String[] getInfrastructureKeyValues() {
    return new String[] {
        connectorRef.getValue(), subscriptionId.getValue(), resourceGroup.getValue(), credentialsRef.getValue()};
  }

  @Override
  public SshWinRmAzureInfrastructure applyOverrides(Infrastructure overrideConfig) {
    SshWinRmAzureInfrastructure config = (SshWinRmAzureInfrastructure) overrideConfig;
    SshWinRmAzureInfrastructure resultantInfra = this;
    if (!ParameterField.isNull(config.getConnectorRef())) {
      resultantInfra = resultantInfra.withConnectorRef(config.getConnectorRef());
    }
    if (!ParameterField.isNull(config.getSubscriptionId())) {
      resultantInfra = resultantInfra.withSubscriptionId(config.getSubscriptionId());
    }
    if (!ParameterField.isNull(config.getResourceGroup())) {
      resultantInfra = resultantInfra.withResourceGroup(config.getResourceGroup());
    }
    if (!ParameterField.isNull(config.getCredentialsRef())) {
      resultantInfra = resultantInfra.withCredentialsRef(config.getCredentialsRef());
    }
    if (!ParameterField.isNull(config.getTags())) {
      resultantInfra = resultantInfra.withTags(config.getTags());
    }
    if (!ParameterField.isNull(config.getHostConnectionType())) {
      resultantInfra = resultantInfra.withHostConnectionType(config.getHostConnectionType());
    }
    if (!ParameterField.isNull(config.getProvisioner())) {
      resultantInfra.setProvisioner(config.getProvisioner());
    }

    return resultantInfra;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }
}
