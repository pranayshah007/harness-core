/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type.HOSTED_VM;
import static io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type.KUBERNETES_DIRECT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.beans.FeatureName;
import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.BackgroundStepNode;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.integrationstage.K8InitializeStepUtils;
import io.harness.ci.integrationstage.K8InitializeTaskUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.yaml.ParameterField;

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
  @Inject K8InitializeTaskUtils k8InitializeTaskUtils;
  @Inject CIFeatureFlagService ciFeatureFlagService;

  private static String serviceRegex = "^[a-zA-Z][a-zA-Z0-9_]*$";
  public final String TEMPLATE = "template";

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

  public void validateStage(ExecutionElementConfig executionElementConfig, Infrastructure infrastructure) {
    List<ExecutionWrapperConfig> steps = executionElementConfig.getSteps();
    if (steps == null) {
      return;
    }

    for (ExecutionWrapperConfig executionWrapper : steps) {
      validateStageUtil(executionWrapper, infrastructure);
    }
  }

  public void validateCacheIntel(Caching caching, Infrastructure infrastructure) {
    if (caching != null && caching.getEnabled() != null && caching.getEnabled().getValue() != null) {
      if (caching.getEnabled().getValue() && infrastructure.getType() != HOSTED_VM) {
        throw new CIStageExecutionException("Cache Intelligence is only available on Harness Cloud Infrastructure");
      }
    }
  }

  public void validateHostedStage(ExecutionElementConfig executionElementConfig, OSType os, String accountId) {
    List<ExecutionWrapperConfig> steps = executionElementConfig.getSteps();
    if (steps == null) {
      return;
    }

    for (ExecutionWrapperConfig executionWrapper : steps) {
      validateHostedStageUtil(executionWrapper, os, accountId);
    }
  }

  public void validateHostedStageUtil(ExecutionWrapperConfig executionWrapper, OSType os, String accountId) {
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      if (executionWrapper.getStep().has(TEMPLATE)) {
        return;
      }
      CIAbstractStepNode stepNode = IntegrationStageUtils.getStepNode(executionWrapper);
      validateHostedStepUtil(stepNode, os, accountId);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElementConfig =
          IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallelStepElementConfig.getSections())) {
        for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
          validateHostedStageUtil(executionWrapperInParallel, os, accountId);
        }
      }
    } else {
      StepGroupElementConfig stepGroupElementConfig = IntegrationStageUtils.getStepGroupElementConfig(executionWrapper);
      if (isNotEmpty(stepGroupElementConfig.getSteps())) {
        for (ExecutionWrapperConfig executionWrapperInStepGroup : stepGroupElementConfig.getSteps()) {
          validateHostedStageUtil(executionWrapperInStepGroup, os, accountId);
        }
      }
    }
  }

  public void validateStageUtil(ExecutionWrapperConfig executionWrapper, Infrastructure infrastructure) {
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      if (executionWrapper.getStep().has(TEMPLATE)) {
        return;
      }
      CIAbstractStepNode stepNode = IntegrationStageUtils.getStepNode(executionWrapper);
      validateStepUtil(stepNode, infrastructure);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElementConfig =
          IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallelStepElementConfig.getSections())) {
        for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
          validateStageUtil(executionWrapperInParallel, infrastructure);
        }
      }
    } else {
      StepGroupElementConfig stepGroupElementConfig = IntegrationStageUtils.getStepGroupElementConfig(executionWrapper);
      if (isNotEmpty(stepGroupElementConfig.getSteps())) {
        for (ExecutionWrapperConfig executionWrapperInStepGroup : stepGroupElementConfig.getSteps()) {
          validateStageUtil(executionWrapperInStepGroup, infrastructure);
        }
      }
    }
  }

  private void validateStepUtil(CIAbstractStepNode stepElement, Infrastructure infrastructure) {
    if (infrastructure.getType() == KUBERNETES_DIRECT
        && k8InitializeTaskUtils.getOS(infrastructure) == OSType.Windows) {
      validateWindowsK8Step(stepElement);
    }

    validateActionStep(stepElement, infrastructure.getType());
  }

  private void validateHostedStepUtil(CIAbstractStepNode stepElement, OSType os, String accountId) {
    Boolean OOTBEnabled =
        ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId);
    if (os == OSType.MacOS) {
      String stepType = stepElement.getType();
      ParameterField<String> connector = new ParameterField<>();
      ParameterField<String> image = new ParameterField<>();
      switch (stepType) {
        case "Run":
          connector = ((RunStepNode) stepElement).getRunStepInfo().getConnectorRef();
          image = ((RunStepNode) stepElement).getRunStepInfo().getImage();
          break;
        case "RunTests":
          connector = ((RunTestStepNode) stepElement).getRunTestsStepInfo().getConnectorRef();
          image = ((RunTestStepNode) stepElement).getRunTestsStepInfo().getImage();
          break;
        case "Background":
          connector = ((BackgroundStepNode) stepElement).getBackgroundStepInfo().getConnectorRef();
          image = ((BackgroundStepNode) stepElement).getBackgroundStepInfo().getImage();
          break;
        case "Bitrise":
        case "Action":
          return;
        default:
          if (!OOTBEnabled) {
            throw new CIStageExecutionException(
                format("%s step is not applicable for builds on %s cloud infrastructure", stepType, os));
          }
      }

      if (ParameterField.isNotNull(connector) || ParameterField.isNotNull(image)) {
        throw new CIStageExecutionException(format(
            "MacOS Cloud infra doesn't support container based steps. Please remove connector and image field from step %s",
            stepElement.getIdentifier()));
      }
    }
  }

  private void validateWindowsK8Step(CIAbstractStepNode stepElement) {
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    k8InitializeStepUtils.validateStepType(ciStepInfo.getNonYamlInfo().getStepInfoType(), OSType.Windows);
  }

  private void validateActionStep(CIAbstractStepNode stepElement, Infrastructure.Type infraType) {
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return;
    }

    CIStepInfo stepInfo = (CIStepInfo) stepElement.getStepSpecType();
    CIStepInfoType stepType = stepInfo.getNonYamlInfo().getStepInfoType();
    if (stepType == CIStepInfoType.ACTION || stepType == CIStepInfoType.BITRISE) {
      if (infraType != HOSTED_VM) {
        throw new CIStageExecutionException(
            format("%s step is only applicable for builds on cloud infrastructure", stepType));
      }
    }
  }
}
