/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.integrationstage.K8InitializeStepUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ValidationUtils {
  @Inject K8InitializeStepUtils k8InitializeStepUtils;

  private static String serviceRegex = "^[a-zA-Z][a-zA-Z0-9_]*$";

  public void validateVmInfraDependencies(List<DependencyElement> dependencyElements) {
    if (isEmpty(dependencyElements)) {
      return;
    }

    for (DependencyElement dependencyElement : dependencyElements) {
      if (dependencyElement == null) {
        continue;
      }
      if (dependencyElement.getDependencySpecType() instanceof CIServiceInfo) {
        CIServiceInfo ciServiceInfo = (CIServiceInfo) dependencyElement.getDependencySpecType();
        Pattern p = Pattern.compile(serviceRegex);
        Matcher m = p.matcher(ciServiceInfo.getIdentifier());
        if (!m.find()) {
          throw new CIStageExecutionException(format(
              "Service dependency identifier %s does not match regex %s", ciServiceInfo.getIdentifier(), serviceRegex));
        }
      }
    }
  }

  public void validateWindowsK8Stage(ExecutionElementConfig executionElementConfig) {
    List<ExecutionWrapperConfig> steps = executionElementConfig.getSteps();
    if (steps == null) {
      return;
    }

    for (ExecutionWrapperConfig executionWrapper : steps) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
        validateWindowsK8Step(stepElementConfig);
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig =
            IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
        if (isNotEmpty(parallelStepElementConfig.getSections())) {
          for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
            if (executionWrapperInParallel.getStep() == null || executionWrapperInParallel.getStep().isNull()) {
              continue;
            }

            StepElementConfig stepElementConfig =
                IntegrationStageUtils.getStepElementConfig(executionWrapperInParallel);
            validateWindowsK8Step(stepElementConfig);
          }
        }
      }
    }
  }

  private void validateWindowsK8Step(StepElementConfig stepElement) {
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    k8InitializeStepUtils.validateStepType(ciStepInfo.getNonYamlInfo().getStepInfoType(), OSType.Windows);
  }
}
