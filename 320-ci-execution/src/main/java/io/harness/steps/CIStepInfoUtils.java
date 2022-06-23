/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.StepImageConfig;
import io.harness.execution.CIExecutionConfigService;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.OutputNGVariable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CIStepInfoUtils {
  public static String getPluginCustomStepImage(
      PluginCompatibleStep step, CIExecutionConfigService ciExecutionConfigService, Type infraType, String accountId) {
    if (infraType == Type.K8) {
      return getK8PluginCustomStepImageConfig(step, ciExecutionConfigService, accountId).getImage();
    } else if (infraType == Type.VM) {
      return getVmPluginCustomStepImageConfig(step, ciExecutionConfigService, accountId);
    }
    return null;
  }

  public static ParameterField<Boolean> getPrivilegedMode(PluginCompatibleStep step) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case SECURITY:
        return ((SecurityStepInfo) step).getPrivileged();
      default:
        return null;
    }
  }

  public static List<String> getOutputVariables(PluginCompatibleStep step) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case SECURITY:
        ParameterField<List<OutputNGVariable>> outputVars = ((SecurityStepInfo) step).getOutputVariables();

        if (isNotEmpty(outputVars.getValue())) {
          return outputVars.getValue().stream().map(OutputNGVariable::getName).collect(Collectors.toList());
        }

        return Collections.emptyList();
      default:
        return Collections.emptyList();
    }
  }

  public static ParameterField<ImagePullPolicy> getImagePullPolicy(PluginCompatibleStep step) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case SECURITY:
        return ((SecurityStepInfo) step).getImagePullPolicy();
      default:
        return null;
    }
  }

  public static List<String> getK8PluginCustomStepEntrypoint(
      PluginCompatibleStep step, CIExecutionConfigService ciExecutionConfigService, String accountId, OSType os) {
    StepImageConfig stepImageConfig = getK8PluginCustomStepImageConfig(step, ciExecutionConfigService, accountId);
    if (os == OSType.Windows) {
      return stepImageConfig.getWindowsEntrypoint();
    }
    return stepImageConfig.getEntrypoint();
  }

  private static StepImageConfig getK8PluginCustomStepImageConfig(
      PluginCompatibleStep step, CIExecutionConfigService ciExecutionConfigService, String accountId) {
    return ciExecutionConfigService.getPluginVersionForK8(step.getNonYamlInfo().getStepInfoType(), accountId);
  }

  private static String getVmPluginCustomStepImageConfig(
      PluginCompatibleStep step, CIExecutionConfigService ciExecutionConfigService, String accountId) {
    return ciExecutionConfigService.getPluginVersionForVM(step.getNonYamlInfo().getStepInfoType(), accountId);
  }
}
