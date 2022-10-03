/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_HISTORY_LIMIT;

import io.harness.annotations.dev.OwnedBy;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class K8sReleaseHistoryHelper {
  public static Set<String> getReleaseNumbersToClean(
      @NotNull K8sReleaseHistory releaseHistory, int currentReleaseNumber) {
    List<K8sRelease> releases = releaseHistory.getReleaseHistory();

    List<String> failedReleaseNumbers = releases.stream()
                                            .filter(release -> Failed == release.getReleaseStatus())
                                            .map(release -> String.valueOf(release.getReleaseNumber()))
                                            .collect(Collectors.toList());

    List<String> oldSuccessfulReleaseNumbers =
        releases.stream()
            .filter(release -> Failed != release.getReleaseStatus())
            .filter(release -> release.getReleaseNumber() < currentReleaseNumber)
            .sorted(Comparator.comparing(release -> ((K8sRelease) release).getReleaseNumber()).reversed())
            .skip(RELEASE_HISTORY_LIMIT)
            .map(release -> String.valueOf(release.getReleaseNumber()))
            .collect(Collectors.toList());

    return Stream.of(failedReleaseNumbers, oldSuccessfulReleaseNumbers)
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }
}
