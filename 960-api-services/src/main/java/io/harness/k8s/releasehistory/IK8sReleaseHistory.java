/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate.IsEmpty;

import java.util.List;

@OwnedBy(CDP)
public interface IK8sReleaseHistory extends IsEmpty {
  int getAndIncrementLastReleaseNumber();
  IK8sRelease getLastSuccessfulRelease(int currentReleaseNumber);
  IK8sRelease getLatestRelease();
  boolean isEmpty();
  int size();
  IK8sReleaseHistory cloneInternal();

  List<IK8sRelease> getReleasesMatchingColor(String color, int currentReleaseNumber);
  IK8sRelease getLatestSuccessfulBlueGreenRelease();

  default int getNextReleaseNumber(boolean inCanaryWorkflow) {
    if (!inCanaryWorkflow) {
      return getAndIncrementLastReleaseNumber();
    }

    IK8sRelease latestRelease = getLatestRelease();
    if (latestRelease == null) {
      return 1;
    }
    return latestRelease.getReleaseNumber();
  }
}
