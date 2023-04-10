/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.PdcInfraMapping;
import io.harness.cdng.infra.beans.PdcInfraMapping.PdcInfraMappingBuilder;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.OneOfSet;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(InfrastructureKind.PDC)
@OneOfSet(fields = {"hosts", "connectorRef", "hostArrayPath"},
    requiredFieldNames = {"hosts", "connectorRef", "hostArrayPath"})
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("PdcInfrastructure")
@RecasterAlias("io.harness.cdng.infra.yaml.PdcInfrastructure")
public class PdcInfrastructure extends InfrastructureDetailsAbstract implements SshWinRmInfrastructure {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> credentialsRef;

  @YamlSchemaTypes({string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Wither
  @SkipAutoEvaluation
  ParameterField<List<String>> hosts;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> hostArrayPath;

  @YamlSchemaTypes({string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_MAP_CLASSPATH)
  @Wither
  ParameterField<Map<String, String>> hostAttributes;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;

  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.INFRASTRUCTURE_DEFINITION_YAML_HOST_FILTER_CLASSPATH)
  @Wither
  HostFilter hostFilter;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Wither
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public InfraMapping getInfraMapping() {
    final PdcInfraMappingBuilder builder = PdcInfraMapping.builder().credentialsRef(credentialsRef.getValue());

    if (hosts != null) {
      builder.hosts(hosts.getValue());
    }
    if (connectorRef != null) {
      builder.connectorRef(connectorRef.getValue());
    }
    if (hostFilter != null) {
      builder.hostFilter(hostFilter);
    }
    if (hostArrayPath != null) {
      builder.hostObjectArray(hostArrayPath.getValue());
    }
    if (hostAttributes != null) {
      builder.hostAttributes(hostAttributes.getValue());
    }

    return builder.build();
  }

  @Override
  public String getKind() {
    return InfrastructureKind.PDC;
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public String[] getInfrastructureKeyValues() {
    if (connectorRef == null) {
      return new String[] {credentialsRef.getValue()};
    } else {
      return new String[] {credentialsRef.getValue(), connectorRef.getValue()};
    }
  }

  @Override
  public PdcInfrastructure applyOverrides(Infrastructure overrideConfig) {
    PdcInfrastructure config = (PdcInfrastructure) overrideConfig;
    PdcInfrastructure resultantInfra = this;
    if (!ParameterField.isNull(config.getCredentialsRef())) {
      resultantInfra = resultantInfra.withCredentialsRef(config.getCredentialsRef());
    }
    if (!ParameterField.isNull(config.getHosts())) {
      resultantInfra = resultantInfra.withHosts(config.getHosts());
    }
    if (!ParameterField.isNull(config.getConnectorRef())) {
      resultantInfra = resultantInfra.withConnectorRef(config.getConnectorRef());
    }
    if (config.hostFilter != null) {
      resultantInfra = resultantInfra.withHostFilter(config.getHostFilter());
    }
    if (!ParameterField.isNull(config.getDelegateSelectors())) {
      resultantInfra = resultantInfra.withDelegateSelectors(config.getDelegateSelectors());
    }
    if (!ParameterField.isNull(config.getProvisioner())) {
      resultantInfra.setProvisioner(config.getProvisioner());
    }
    if (!ParameterField.isNull(config.getHostArrayPath())) {
      resultantInfra = resultantInfra.withHostArrayPath(config.getHostArrayPath());
    }
    if (!ParameterField.isNull(config.getHostAttributes())) {
      resultantInfra = resultantInfra.withHostAttributes(config.getHostAttributes());
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
