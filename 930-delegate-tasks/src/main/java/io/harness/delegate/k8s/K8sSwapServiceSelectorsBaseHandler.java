/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.KubernetesHelperService.toDisplayYaml;
import static io.harness.k8s.model.HarnessLabelValues.bgPrimaryEnv;
import static io.harness.k8s.model.HarnessLabelValues.bgStageEnv;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1Service;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
@Singleton
@Slf4j
public class K8sSwapServiceSelectorsBaseHandler {
  @Inject private KubernetesContainerService kubernetesContainerService;

  public boolean swapServiceSelectors(
      KubernetesConfig kubernetesConfig, String serviceOne, String serviceTwo, LogCallback logCallback) {
    return swapServiceSelectors(kubernetesConfig, serviceOne, serviceTwo, logCallback, false);
  }

  public boolean swapServiceSelectors(KubernetesConfig kubernetesConfig, String serviceOne, String serviceTwo,
      LogCallback logCallback, boolean isErrorFrameworkSupported) {
    V1Service service1 = kubernetesContainerService.getService(kubernetesConfig, serviceOne);
    V1Service service2 = kubernetesContainerService.getService(kubernetesConfig, serviceTwo);

    if (service1 == null) {
      return handleServiceNotFound(serviceOne, logCallback, isErrorFrameworkSupported,
          format(KubernetesExceptionMessages.BG_SWAP_SERVICES_FAILED, serviceOne, serviceTwo));
    }

    if (service2 == null) {
      return handleServiceNotFound(serviceTwo, logCallback, isErrorFrameworkSupported,
          format(KubernetesExceptionMessages.BG_SWAP_SERVICES_FAILED, serviceOne, serviceTwo));
    }

    Map<String, String> serviceOneSelectors = service1.getSpec().getSelector();
    Map<String, String> serviceTwoSelectors = service2.getSpec().getSelector();

    logCallback.saveExecutionLog(format("%nSelectors for Service One : [name:%s]%n%s", service1.getMetadata().getName(),
                                     toDisplayYaml(service1.getSpec().getSelector())),
        LogLevel.INFO);

    logCallback.saveExecutionLog(format("%nSelectors for Service Two : [name:%s]%n%s", service2.getMetadata().getName(),
                                     toDisplayYaml(service2.getSpec().getSelector())),
        LogLevel.INFO);

    logCallback.saveExecutionLog(format("%nSwapping Service Selectors..%n"), LogLevel.INFO);

    service1.getSpec().setSelector(serviceTwoSelectors);
    service2.getSpec().setSelector(serviceOneSelectors);

    V1Service serviceOneUpdated = kubernetesContainerService.createOrReplaceService(kubernetesConfig, service1);

    V1Service serviceTwoUpdated = kubernetesContainerService.createOrReplaceService(kubernetesConfig, service2);

    logCallback.saveExecutionLog(
        format("%nUpdated Selectors for Service One : [name:%s]%n%s", serviceOneUpdated.getMetadata().getName(),
            toDisplayYaml(serviceOneUpdated.getSpec().getSelector())),
        LogLevel.INFO);

    logCallback.saveExecutionLog(
        format("%nUpdated Selectors for Service Two : [name:%s]%n%s", serviceTwoUpdated.getMetadata().getName(),
            toDisplayYaml(serviceTwoUpdated.getSpec().getSelector())),
        LogLevel.INFO);

    logCallback.saveExecutionLog("Done", LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return true;
  }

  private boolean handleServiceNotFound(
      String service, LogCallback logCallback, boolean isErrorFrameworkSupported, String message) {
    logCallback.saveExecutionLog(
        format("Service %s not found.", service), LogLevel.ERROR, CommandExecutionStatus.FAILURE);
    if (isErrorFrameworkSupported) {
      throw NestedExceptionUtils.hintWithExplanationException(
          KubernetesExceptionHints.BG_SWAP_SERVICES_SERVICE_NOT_FOUND,
          format(KubernetesExceptionExplanation.BG_SWAP_SERVICES_SERVICE_NOT_FOUND, service),
          new KubernetesTaskException(message));
    }
    return false;
  }

  public void updateReleaseHistory(IK8sRelease primaryRelease, IK8sRelease stageRelease) {
    if (primaryRelease != null) {
      swapBgEnvironment(primaryRelease);
    }
    if (stageRelease != null) {
      swapBgEnvironment(stageRelease);
    }
  }

  public void swapBgEnvironment(IK8sRelease release) {
    if (isNotEmpty(release.getBgEnvironment())) {
      if (bgStageEnv.equals(release.getBgEnvironment())) {
        release.setBgEnvironment(bgPrimaryEnv);
      } else {
        release.setBgEnvironment(bgStageEnv);
      }
    }
  }

  public String getColorOfService(KubernetesConfig kubernetesConfig, String service) {
    V1Service primaryService = kubernetesContainerService.getService(kubernetesConfig, service);
    if (primaryService == null) {
      return EMPTY;
    }
    return primaryService.getSpec().getSelector().get(HarnessLabels.color);
  }
}
