/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.WARN;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.ReleaseMetadata;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.logging.LogCallback;

import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class K8sReleaseWarningLogger {
  final String RELEASE_CONFLICT_WARNING_MESSAGE =
      "WARNING: This release is owned by a different service/env. This may affect features like rollback & instance tracking. %n"
      + "For more details on how Harness tracks Kubernetes releases, visit: "
      + "https://developer.harness.io/docs/continuous-delivery/deploy-srv-diff-platforms/kubernetes/cd-k8s-ref/kubernetes-releases-and-versioning/ %n%n%s";
  public void logWarningIfReleaseConflictExists(
      ReleaseMetadata currentReleaseMeta, IK8sReleaseHistory releaseHistory, LogCallback logCallback) {
    logWarningIfReleaseConflictExists(currentReleaseMeta, releaseHistory, logCallback, false);
  }
  public void logWarningIfReleaseConflictExists(ReleaseMetadata currentReleaseMeta, IK8sReleaseHistory releaseHistory,
      LogCallback logCallback, boolean inCanaryWorkflow) {
    if (K8sReleaseDiffCalculator.releaseConflicts(currentReleaseMeta, releaseHistory, inCanaryWorkflow)) {
      ReleaseMetadata previousReleaseMetadata =
          K8sReleaseDiffCalculator.getPreviousReleaseMetadata(releaseHistory, inCanaryWorkflow);
      if (currentReleaseMeta != null && !currentReleaseMeta.equals(previousReleaseMetadata)) {
        String diff = K8sReleaseDiffCalculator.calculateDiffForLogging(currentReleaseMeta, previousReleaseMetadata);
        logWarningMessage(logCallback, diff);
      }
    }
  }

  private void logWarningMessage(LogCallback logCallback, String message) {
    if (isNotEmpty(message)) {
      logCallback.saveExecutionLog(format(RELEASE_CONFLICT_WARNING_MESSAGE, message), WARN);
    }
  }
}
