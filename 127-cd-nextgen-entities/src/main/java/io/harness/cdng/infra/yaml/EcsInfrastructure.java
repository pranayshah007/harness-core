/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.infra.beans.EcsInfraMapping;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

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

@OwnedBy(HarnessTeam.CDP)
@Value
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(InfrastructureKind.ECS)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("ecsInfrastructure")
@RecasterAlias("io.harness.cdng.infra.yaml.EcsInfrastructure")
public class EcsInfrastructure extends InfrastructureDetailsAbstract
    implements Infrastructure, Visitable, WithConnectorRef, AwsBaseInfrastructure {
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
  ParameterField<String> cluster;
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> region;

  @Override
  public InfraMapping getInfraMapping() {
    return EcsInfraMapping.builder()
        .awsConnector(connectorRef.getValue())
        .region(region.getValue())
        .cluster(cluster.getValue())
        .build();
  }

  @Override
  public String getKind() {
    return InfrastructureKind.ECS;
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public String[] getInfrastructureKeyValues() {
    return new String[] {connectorRef.getValue(), cluster.getValue(), region.getValue()};
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Override
  public Infrastructure applyOverrides(Infrastructure overrideConfig) {
    EcsInfrastructure config = (EcsInfrastructure) overrideConfig;
    EcsInfrastructure resultantInfra = this;
    if (!ParameterField.isNull(config.getConnectorRef())) {
      resultantInfra = resultantInfra.withConnectorRef(config.getConnectorRef());
    }
    if (!ParameterField.isNull(config.getRegion())) {
      resultantInfra = resultantInfra.withRegion(config.getRegion());
    }
    if (!ParameterField.isNull(config.getCluster())) {
      resultantInfra = resultantInfra.withCluster(config.getCluster());
    }
    if (!ParameterField.isNull(config.getProvisioner())) {
      resultantInfra.setProvisioner(config.getProvisioner());
    }

    return resultantInfra;
  }
}
