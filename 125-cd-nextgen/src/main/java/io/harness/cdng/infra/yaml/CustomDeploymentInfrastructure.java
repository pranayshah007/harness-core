/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.CustomDeploymentInfraMapping;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.InfrastructureDetailsAbstract;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName(InfrastructureKind.CUSTOM_DEPLOYMENT)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("customDeploymentInfrastructure")
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure")
public class CustomDeploymentInfrastructure
    extends InfrastructureDetailsAbstract implements Infrastructure, Visitable, WithConnectorRef {
  @NotNull @NotEmpty @Wither List<NGVariable> variables;

  @Override
  public InfraMapping getInfraMapping() {
    Map<String, String> infraVars = new HashMap<>();
    for (NGVariable variable : variables) {
      infraVars.put(variable.getName(), variable.getCurrentValue().toString());
    }
    return CustomDeploymentInfraMapping.builder().variables(infraVars).build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    // TODO - need to figure out connector ref details
    return null;
  }

  @Override
  public String[] getInfrastructureKeyValues() {
    List<String> infraKeys = new ArrayList<>();
    for (NGVariable variable : variables) {
      infraKeys.add(variable.getName());
    }
    return infraKeys.toArray(new String[0]);
  }

  @Override
  public String getKind() {
    return InfrastructureKind.CUSTOM_DEPLOYMENT;
  }

  @Override
  public Infrastructure applyOverrides(Infrastructure overrideConfig) {
    CustomDeploymentInfrastructure config = (CustomDeploymentInfrastructure) overrideConfig;
    CustomDeploymentInfrastructure resultantInfra = this;
    if (!isEmpty(config.getVariables())) {
      resultantInfra = resultantInfra.withVariables(config.getVariables());
    }
    return resultantInfra;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    //    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    // TODO - need to figure out connector ref details
    // connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return new HashMap<>();
  }
}
