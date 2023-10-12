/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.delegate.task.k8s.ReleaseMetadata;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class K8sReleaseDiffCalculator {
  static final String DIFF_FORMAT = "Field: [%s] has changed -> Old value: [%s], Current value: [%s] %n";
  static final String DIFF_KEY_SVC_ID = "Service ID";
  static final String DIFF_KEY_ENV_ID = "Environment ID";
  static final String DIFF_KEY_INFRA_ID = "Infrastructure Definition ID";
  static final String DIFF_KEY_INFRA_KEY = "Infrastructure Definition Key";

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
    if (!current.getInfraKey().equals(previous.getInfraKey())) {
      sb.append(String.format(DIFF_FORMAT, DIFF_KEY_INFRA_KEY, previous.getInfraKey(), current.getInfraKey()));
    }
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
