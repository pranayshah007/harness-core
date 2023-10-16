/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.ReleaseMetadata;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class K8sReleaseDiffCalculator {
  final String DIFF_FORMAT = "[%s] has changed -> Old value: [%s], New value: [%s] %n";
  final String DIFF_KEY_SVC_ID = "Service";
  final String DIFF_KEY_ENV_ID = "Environment";
  final String DIFF_KEY_INFRA_ID = "Infrastructure Definition";
  final String ORIGINAL_OWNER_FORMAT = "Original release owners: %s: [%s], %s: [%s], %s: [%s]";

  public boolean releaseConflicts(
      ReleaseMetadata releaseMetadata, IK8sReleaseHistory releaseHistory, boolean inCanaryWorkflow) {
    if (isEmpty(releaseMetadata) || isEmpty(releaseHistory)) {
      return false;
    }
    ReleaseMetadata previousReleaseMetadata = getPreviousReleaseMetadata(releaseHistory, inCanaryWorkflow);
    return !releaseMetadata.equals(previousReleaseMetadata);
  }

  public String calculateDiffForLogging(ReleaseMetadata current, ReleaseMetadata previous) {
    if (isEmpty(current) || isEmpty(previous)) {
      return StringUtils.EMPTY;
    }
    StringBuilder sb = new StringBuilder();
    if (!current.getServiceId().equals(previous.getServiceId())) {
      sb.append(String.format(DIFF_FORMAT, DIFF_KEY_SVC_ID, previous.getServiceId(), current.getServiceId()));
    }
    if (!current.getEnvId().equals(previous.getEnvId())) {
      sb.append(String.format(DIFF_FORMAT, DIFF_KEY_ENV_ID, previous.getEnvId(), current.getEnvId()));
    }
    if (!current.getInfraId().equals(previous.getInfraId())) {
      sb.append(String.format(DIFF_FORMAT, DIFF_KEY_INFRA_ID, previous.getInfraId(), current.getInfraId()));
    }
    sb.append(String.format(ORIGINAL_OWNER_FORMAT, DIFF_KEY_SVC_ID, previous.getServiceId(), DIFF_KEY_ENV_ID,
        previous.getEnvId(), DIFF_KEY_INFRA_ID, previous.getInfraId()));
    return sb.toString();
  }

  public ReleaseMetadata getPreviousReleaseMetadata(IK8sReleaseHistory releaseHistory, boolean inCanaryWorkflow) {
    Optional<IK8sRelease> previousReleaseOptional = getPreviousRelease(releaseHistory, inCanaryWorkflow);
    return previousReleaseOptional.map(IK8sRelease::getReleaseMetadata).orElse(null);
  }

  private Optional<IK8sRelease> getPreviousRelease(IK8sReleaseHistory releaseHistory, boolean inCanaryWorkflow) {
    if (inCanaryWorkflow) {
      return Optional.ofNullable(releaseHistory.getLastSuccessfulRelease());
    }
    return Optional.ofNullable(releaseHistory.getLatestRelease());
  }
}
