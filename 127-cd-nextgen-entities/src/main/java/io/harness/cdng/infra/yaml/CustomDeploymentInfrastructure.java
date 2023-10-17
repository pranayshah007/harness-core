/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.customdeployment.CustomDeploymentNGVariable;
import io.harness.cdng.customdeployment.CustomDeploymentNGVariableType;
import io.harness.cdng.infra.beans.CustomDeploymentInfraMapping;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.visitor.helpers.SecretConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

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
@SimpleVisitorHelper(helperClass = SecretConnectorRefExtractorHelper.class)
@TypeAlias("customDeploymentInfrastructure")
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure")
public class CustomDeploymentInfrastructure
    extends InfrastructureDetailsAbstract implements Infrastructure, Visitable, WithConnectorRef {
  @Wither List<CustomDeploymentNGVariable> variables;
  @NotNull @NotEmpty @Wither StepTemplateRef customDeploymentRef;
  @Override
  public InfraMapping getInfraMapping() {
    Map<String, String> infraVars = new HashMap<>();
    if (!isNull(variables)) {
      for (CustomDeploymentNGVariable variable : variables) {
        infraVars.put(variable.getName(), variable.getCurrentValue().toString());
      }
    }
    return CustomDeploymentInfraMapping.builder().variables(infraVars).build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return null;
  }

  @Override
  public List<ParameterField<String>> getConnectorReferences() {
    List<ParameterField<String>> connectorRefs = new ArrayList<>();
    if (!isNull(variables)) {
      for (CustomDeploymentNGVariable variable : variables) {
        if (variable.getType() == CustomDeploymentNGVariableType.CONNECTOR) {
          connectorRefs.add((ParameterField<String>) variable.getCurrentValue());
        }
      }
    }
    return connectorRefs;
  }

  @Override
  public String[] getInfrastructureKeyValues() {
    List<String> infraKeys = new ArrayList<>();
    if (!isNull(customDeploymentRef)) {
      infraKeys.add(customDeploymentRef.getTemplateRef());
      infraKeys.add(customDeploymentRef.getVersionLabel());
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
    return new HashMap<>();
  }
}
