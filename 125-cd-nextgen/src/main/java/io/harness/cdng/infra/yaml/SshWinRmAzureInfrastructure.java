/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.SshWinRmAzureInfraMapping;
import io.harness.cdng.infra.beans.SshWinRmAzureInfraMapping.SshWinRmAzureInfraMappingBuilder;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@JsonTypeName(InfrastructureKind.SSH_WINRM_AZURE)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("SshWinRmAzureInfrastructure")
@RecasterAlias("io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure")
public class SshWinRmAzureInfrastructure implements Infrastructure, Visitable, WithConnectorRef {
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

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_MAP_CLASSPATH)
  @Wither
  ParameterField<Map<String, String>> tags;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @Wither
  ParameterField<Boolean> usePublicDns;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Wither
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public InfraMapping getInfraMapping() {
    SshWinRmAzureInfraMappingBuilder builder = SshWinRmAzureInfraMapping.builder()
                                                   .connectorRef(connectorRef.getValue())
                                                   .subscriptionId(subscriptionId.getValue())
                                                   .resourceGroup(resourceGroup.getValue())
                                                   .credentialsRef(credentialsRef.getValue())
                                                   .usePublicDns(usePublicDns.getValue());

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
    if (!ParameterField.isNull(config.getUsePublicDns())) {
      resultantInfra = resultantInfra.withUsePublicDns(config.getUsePublicDns());
    }
    if (!ParameterField.isNull(config.getDelegateSelectors())) {
      resultantInfra = resultantInfra.withDelegateSelectors(config.getDelegateSelectors());
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
