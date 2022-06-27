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
import io.harness.cdng.infra.beans.AwsInstanceFilter;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.SshWinRmAwsInfraMapping;
import io.harness.cdng.infra.beans.SshWinRmAwsInfraMapping.SshWinRmAwsInfraMappingBuilder;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.OneOfSet;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
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
@JsonTypeName(InfrastructureKind.SSH_WINRM_AWS)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@OneOfSet(fields = {"autoScalingGroupName", "awsInstanceFilter"})
@TypeAlias("SshWinRmAwsInfrastructure")
@RecasterAlias("io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure")
public class SshWinRmAwsInfrastructure implements Infrastructure, Visitable, WithConnectorRef {
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
  ParameterField<String> credentialsRef;

  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> region;

  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> loadBalancer;

  @NotNull
  @NotEmpty
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> hostNameConvention;

  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH) @Wither ParameterField<Boolean> useAutoScalingGroup;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> autoScalingGroupName;

  @Wither AwsInstanceFilter awsInstanceFilter;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Wither
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public InfraMapping getInfraMapping() {
    final SshWinRmAwsInfraMappingBuilder builder = SshWinRmAwsInfraMapping.builder();

    if (connectorRef != null) {
      builder.connectorRef(connectorRef.getValue());
    }
    if (region != null) {
      builder.region(region.getValue());
    }
    if (loadBalancer != null) {
      builder.loadBalancer(loadBalancer.getValue());
    }

    return builder.build();
  }

  @Override
  public String getKind() {
    return InfrastructureKind.SSH_WINRM_AWS;
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public String[] getInfrastructureKeyValues() {
    return new String[] {connectorRef.getValue()};
  }

  @Override
  public SshWinRmAwsInfrastructure applyOverrides(Infrastructure overrideConfig) {
    SshWinRmAwsInfrastructure config = (SshWinRmAwsInfrastructure) overrideConfig;
    SshWinRmAwsInfrastructure resultantInfra = this;
    if (!ParameterField.isNull(config.getConnectorRef())) {
      resultantInfra = resultantInfra.withConnectorRef(config.getConnectorRef());
    }
    if (!ParameterField.isNull(config.getCredentialsRef())) {
      resultantInfra = resultantInfra.withCredentialsRef(config.getCredentialsRef());
    }
    if (!ParameterField.isNull(config.getRegion())) {
      resultantInfra = resultantInfra.withRegion(config.getRegion());
    }
    if (!ParameterField.isNull(config.getLoadBalancer())) {
      resultantInfra = resultantInfra.withLoadBalancer(config.getLoadBalancer());
    }
    if (!ParameterField.isNull(config.getHostNameConvention())) {
      resultantInfra = resultantInfra.withHostNameConvention(config.getHostNameConvention());
    }
    if (!ParameterField.isNull(config.getUseAutoScalingGroup())) {
      resultantInfra = resultantInfra.withUseAutoScalingGroup(config.getUseAutoScalingGroup());
    }
    if (!ParameterField.isNull(config.getAutoScalingGroupName())) {
      resultantInfra = resultantInfra.withAutoScalingGroupName(config.getAutoScalingGroupName());
    }
    if (config.getAwsInstanceFilter() != null) {
      resultantInfra = resultantInfra.withAwsInstanceFilter(config.getAwsInstanceFilter());
    }
    if (!ParameterField.isNull(config.getDelegateSelectors())) {
      resultantInfra = resultantInfra.withDelegateSelectors(config.getDelegateSelectors());
    }

    return resultantInfra;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    return Collections.singletonMap(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
  }
}
