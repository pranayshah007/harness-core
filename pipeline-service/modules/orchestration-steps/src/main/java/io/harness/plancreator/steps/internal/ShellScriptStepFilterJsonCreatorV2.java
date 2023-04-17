/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.shellscript.ShellScriptStepInfo;
import io.harness.steps.shellscript.ShellScriptStepNode;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(PIPELINE)
public class ShellScriptStepFilterJsonCreatorV2 extends GenericStepPMSFilterJsonCreatorV2 {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SHELL_SCRIPT);
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    ShellScriptStepNode scriptStepNode = (ShellScriptStepNode) yamlField;
    ShellScriptStepInfo scriptStepInfo = scriptStepNode.getShellScriptStepInfo();
    if (!Boolean.TRUE.equals(scriptStepInfo.getOnDelegate().getValue())) {
      if (scriptStepInfo.getExecutionTarget() == null) {
        throw new InvalidYamlRuntimeException(
            format("Execution target details cannot be null for step %s. Please add it & try again.",
                YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
      }
      if (ParameterField.isBlank(scriptStepInfo.getExecutionTarget().getHost())) {
        throw new InvalidYamlRuntimeException(
            format("Execution target host details cannot be null for step %s. Please add it & try again.",
                YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
      }

      if (ParameterField.isBlank(scriptStepInfo.getExecutionTarget().getConnectorRef())) {
        throw new InvalidYamlRuntimeException(format(
            "Execution target ssh connection attribute details cannot be null for step %s. Please add it & try again.",
            YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
      }
    }

    return super.handleNode(filterCreationContext, yamlField);
  }
}
