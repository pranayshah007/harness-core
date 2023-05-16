/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.cdng.infra.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfraMapping;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(InfrastructureKind.TAS)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("tanzuApplicationServiceInfrastructure")
@RecasterAlias("io.harness.cdng.infra.yaml.TanzuApplicationServiceInfrastructure")
public class TanzuApplicationServiceInfrastructure
    extends InfrastructureDetailsAbstract implements Infrastructure, Visitable, WithConnectorRef {
  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @With
  ParameterField<String> connectorRef;

  @NotNull
  @NotEmpty
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @With
  ParameterField<String> organization;

  @NotNull @NotEmpty @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @With ParameterField<String> space;

  @Override
  public InfraMapping getInfraMapping() {
    return TanzuApplicationServiceInfraMapping.builder()
        .connectorRef(connectorRef.getValue())
        .organization(organization.getValue())
        .space(space.getValue())
        .build();
  }

  @Override
  public String getKind() {
    return InfrastructureKind.TAS;
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public String[] getInfrastructureKeyValues() {
    return new String[] {connectorRef.getValue(), organization.getValue(), space.getValue()};
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    return Collections.singletonMap(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
  }

  @Override
  public Infrastructure applyOverrides(Infrastructure overrideConfig) {
    TanzuApplicationServiceInfrastructure config = (TanzuApplicationServiceInfrastructure) overrideConfig;
    TanzuApplicationServiceInfrastructure resultantInfra = this;

    if (ParameterField.isNotNull(config.getConnectorRef())) {
      resultantInfra = resultantInfra.withConnectorRef(config.getConnectorRef());
    }

    if (ParameterField.isNotNull(config.getOrganization())) {
      resultantInfra = resultantInfra.withOrganization(config.getOrganization());
    }

    if (ParameterField.isNotNull(config.getSpace())) {
      resultantInfra = resultantInfra.withOrganization(config.getSpace());
    }

    if (!ParameterField.isNull(config.getProvisioner())) {
      resultantInfra.setProvisioner(config.getProvisioner());
    }

    return resultantInfra;
  }
}
