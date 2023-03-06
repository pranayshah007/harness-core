/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.step;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.plan.creator.CICreatorUtils;
import io.harness.exception.InvalidYamlException;
import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlNode;

import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.util.Strings;

@OwnedBy(HarnessTeam.CI)
public class CIStepFilterJsonCreatorV2 extends GenericStepPMSFilterJsonCreatorV2 {
  @Override
  public Set<String> getSupportedStepTypes() {
    return CICreatorUtils.getSupportedStepsV2();
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    validateStep(filterCreationContext, yamlField);
    return super.handleNode(filterCreationContext, yamlField);
  }

  public void validateStep(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    String infra = getStageInfra(filterCreationContext);
    String k = CIStepInfoType.RUN.getDisplayName();
    String stepType = yamlField.getType();
    switch (stepType) {
      case "Run":
        validateRunStep(infra, (RunStepNode) yamlField);
        break;
      case "RunTests":
        validateRunTestsStep(infra, (RunTestStepNode) yamlField);
        break;
      default:
        break;
    }
  }

  private void validateRunStep(String infra, RunStepNode runStepNode) {
    RunStepInfo runStep = runStepNode.getRunStepInfo();
    if (Infrastructure.Type.KUBERNETES_DIRECT.getYamlName().equals(infra)) {
      String connectorRef = runStep.getConnectorRef().getValue();
      String image = runStep.getImage().getValue();
      if (Strings.isBlank(connectorRef)) {
        throw new InvalidYamlException("Run step with Kubernetes infra can't have empty connector field");
      }
      if (Strings.isBlank(image)) {
        throw new InvalidYamlException("Run step with Kubernetes infra can't have empty image field");
      }
    }
  }

  private void validateRunTestsStep(String infra, RunTestStepNode runStepNode) {
    RunTestsStepInfo runTestsStep = runStepNode.getRunTestsStepInfo();
    if (Infrastructure.Type.KUBERNETES_DIRECT.getYamlName().equals(infra)) {
      String connectorRef = runTestsStep.getConnectorRef().getValue();
      String image = runTestsStep.getImage().getValue();
      if (Strings.isBlank(connectorRef)) {
        throw new InvalidYamlException("RunTests step with Kubernetes infra can't have empty connector field");
      }
      if (Strings.isBlank(image)) {
        throw new InvalidYamlException("RunTests step with Kubernetes infra can't have empty image field");
      }
    }
  }

  private String getStageInfra(FilterCreationContext filterCreationContext) {
    YamlNode currNode = filterCreationContext.getCurrentField().getNode();
    while (!Objects.isNull(currNode)) {
      if (!Objects.isNull(currNode.getField("infrastructure"))) {
        break;
      }
      currNode = currNode.getParentNode();
    }
    if (!Objects.isNull(currNode)) {
      return currNode.getField("infrastructure").getType();
    }
    return null;
  }
}
