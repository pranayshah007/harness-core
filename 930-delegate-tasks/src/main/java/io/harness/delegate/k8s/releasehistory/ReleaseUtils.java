/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.releasehistory;

import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.delegate.k8s.releasehistory.ReleaseConstants.RELEASE_HISTORY_LIMIT;
import static io.harness.delegate.k8s.releasehistory.ReleaseConstants.RELEASE_KEY;
import static io.harness.delegate.k8s.releasehistory.ReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.ReleaseConstants.RELEASE_STATUS_LABEL_KEY;

import static java.util.Collections.emptyList;

import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.Release;

import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1Secret;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ReleaseUtils {
  public Set<String> getReleaseNumbersToClean(List<V1Secret> releases, int currentReleaseNumber) {
    List<String> failedReleaseNumbers =
        releases.stream()
            .filter(release
                -> release.getMetadata() != null && release.getMetadata().getLabels() != null
                    && release.getMetadata().getLabels().containsKey(RELEASE_STATUS_LABEL_KEY))
            .filter(release
                -> Release.Status.Failed.name().equals(release.getMetadata().getLabels().get(RELEASE_STATUS_LABEL_KEY)))
            .map(release -> release.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY))
            .collect(Collectors.toList());

    List<String> oldSuccessfulReleases =
        releases.stream()
            .filter(release
                -> release.getMetadata() != null && release.getMetadata().getLabels() != null
                    && release.getMetadata().getLabels().containsKey(RELEASE_STATUS_LABEL_KEY))
            .filter(release
                -> Release.Status.Failed.name().equals(release.getMetadata().getLabels().get(RELEASE_STATUS_LABEL_KEY)))
            .filter(release
                -> currentReleaseNumber
                    > Integer.parseInt(release.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY)))
            .sorted(
                Comparator
                    .comparing(release -> ((V1Secret) release).getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY))
                    .reversed())
            .skip(RELEASE_HISTORY_LIMIT)
            .map(release -> release.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY))
            .collect(Collectors.toList());
    return Stream.of(failedReleaseNumbers, oldSuccessfulReleases)
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }

  public int getCurrentReleaseNumber(List<V1Secret> releases) {
    Optional<V1Secret> lastReleaseOptional =
        releases.stream()
            .filter(release
                -> release.getMetadata() != null && release.getMetadata().getLabels() != null
                    && release.getMetadata().getLabels().containsKey(RELEASE_NUMBER_LABEL_KEY))
            .max(Comparator.comparing(
                release -> Integer.valueOf(release.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY))));

    int currentReleaseNumber = 1;
    if (lastReleaseOptional.isPresent()) {
      V1Secret lastRelease = lastReleaseOptional.get();
      if (lastRelease.getMetadata() != null && lastRelease.getMetadata().getLabels() != null
          && lastRelease.getMetadata().getLabels().containsKey(RELEASE_NUMBER_LABEL_KEY)) {
        int lastReleaseNumber = Integer.parseInt(lastRelease.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY));
        log.info("Last release number is : " + lastReleaseNumber);
        currentReleaseNumber = 1 + lastReleaseNumber;
      }
    }
    log.info("Current release number is : " + currentReleaseNumber);
    return currentReleaseNumber;
  }

  public V1Secret getLastSuccessfulRelease(List<V1Secret> releases, int currentReleaseNumber) {
    Optional<V1Secret> lastSuccessfulReleaseOptional =
        releases.stream()
            .filter(release -> release.getMetadata() != null && release.getMetadata().getLabels() != null)
            .filter(release
                -> !release.getMetadata()
                        .getLabels()
                        .get(RELEASE_NUMBER_LABEL_KEY)
                        .equals(String.valueOf(currentReleaseNumber)))
            .filter(release
                -> release.getMetadata()
                       .getLabels()
                       .get(RELEASE_STATUS_LABEL_KEY)
                       .equals(Release.Status.Succeeded.name()))
            .max(Comparator.comparing(
                release -> Integer.valueOf(release.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY))));
    return lastSuccessfulReleaseOptional.orElse(null);
  }

  public V1Secret getLatestRelease(List<V1Secret> releases) {
    Optional<V1Secret> latestReleaseOptional =
        releases.stream()
            .filter(release -> release.getMetadata() != null && release.getMetadata().getLabels() != null)
            .max(Comparator.comparing(
                release -> Integer.valueOf(release.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY))));
    return latestReleaseOptional.orElse(null);
  }

  public List<KubernetesResource> getResourcesFromRelease(V1Secret release) throws IOException {
    if (release == null) {
      return emptyList();
    }

    List<KubernetesResource> resources = emptyList();
    Map<String, byte[]> secretData = release.getData();
    if (secretData != null && secretData.containsKey(RELEASE_KEY)) {
      byte[] compressedYaml = secretData.get(RELEASE_KEY);
      String manifestsYaml = deCompressString(compressedYaml);
      return ManifestHelper.processYaml(manifestsYaml);
    }

    return resources;
  }

  public V1Secret setResourcesInRelease(V1Secret release, List<KubernetesResource> resources) throws IOException {
    String manifestsYaml = ManifestHelper.toYaml(resources);
    byte[] compressedYaml = compressString(manifestsYaml, Deflater.BEST_COMPRESSION);
    release.setData(Map.of(RELEASE_KEY, compressedYaml));
    return release;
  }

  String createSetBasedArg(String key, Set<String> values) {
    return String.format("%s in (%s)", key, String.join(",", values));
  }

  String createListBasedArg(String key, String value) {
    return String.format("%s=%s", key, value);
  }

  String createCommaSeparatedKeyValueList(Map<String, String> k8sArg) {
    return k8sArg.entrySet()
        .stream()
        .map(entry -> createListBasedArg(entry.getKey(), entry.getValue()))
        .collect(Collectors.joining(","));
  }
}
