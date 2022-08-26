/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.releasehistory;

import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_HISTORY_LIMIT;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_LABEL_QUERY_LIST_FORMAT;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_LABEL_QUERY_SET_FORMAT;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_NAME_DELIMITER;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_VALUE;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.SECRET_LABEL_DELIMITER;
import static io.harness.k8s.manifest.ManifestHelper.getWorkloads;
import static io.harness.k8s.model.Release.Status.Failed;
import static io.harness.k8s.model.Release.Status.Succeeded;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
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
public class K8sReleaseService {
  @Inject KubernetesContainerService kubernetesContainerService;
  public V1Secret updateReleaseStatus(V1Secret release, String status) {
    V1ObjectMeta releaseMeta = release.getMetadata();
    if (releaseMeta != null && releaseMeta.getLabels() != null) {
      Map<String, String> labels = releaseMeta.getLabels();
      labels.put(RELEASE_STATUS_LABEL_KEY, status);
      releaseMeta.setLabels(labels);
      release.setMetadata(releaseMeta);
      return release;
    }
    return release;
  }

  public V1Secret saveRelease(V1Secret release, KubernetesConfig kubernetesConfig) {
    return kubernetesContainerService.createOrReplaceSecret(kubernetesConfig, release);
  }

  public Set<String> getReleaseNumbersToClean(List<V1Secret> releases, int currentReleaseNumber) {
    List<String> failedReleaseNumbers =
        releases.stream()
            .filter(release
                -> release.getMetadata() != null && release.getMetadata().getLabels() != null
                    && release.getMetadata().getLabels().containsKey(RELEASE_STATUS_LABEL_KEY))
            .filter(release -> Failed.name().equals(release.getMetadata().getLabels().get(RELEASE_STATUS_LABEL_KEY)))
            .map(release -> release.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY))
            .collect(Collectors.toList());

    List<String> oldSuccessfulReleaseNumbers =
        releases.stream()
            .filter(release
                -> release.getMetadata() != null && release.getMetadata().getLabels() != null
                    && release.getMetadata().getLabels().containsKey(RELEASE_STATUS_LABEL_KEY))
            .filter(release -> !Failed.name().equals(release.getMetadata().getLabels().get(RELEASE_STATUS_LABEL_KEY)))
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
    return Stream.of(failedReleaseNumbers, oldSuccessfulReleaseNumbers)
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
            .filter(release -> release.getMetadata().getLabels().get(RELEASE_STATUS_LABEL_KEY).equals(Succeeded.name()))
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

  public List<V1Secret> getReleasesMatchingColor(List<V1Secret> releases, String color, int currentReleaseNumber) {
    return releases.stream()
        .filter(release -> release.getMetadata() != null && release.getMetadata().getLabels() != null)
        .filter(release
            -> currentReleaseNumber
                != Integer.parseInt(release.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY)))
        .filter(release -> checkReleaseForColoredWorkloads(release, color))
        .collect(Collectors.toList());
  }

  public List<KubernetesResource> getResourcesFromRelease(V1Secret release) {
    if (release == null) {
      return emptyList();
    }

    try {
      Map<String, byte[]> secretData = release.getData();
      if (secretData != null && secretData.containsKey(RELEASE_KEY)) {
        byte[] compressedYaml = secretData.get(RELEASE_KEY);
        String manifestsYaml = deCompressString(compressedYaml);
        return ManifestHelper.processYaml(manifestsYaml);
      }
    } catch (IOException ex) {
      log.error("Failed to extract resources from release.", ex);
    }
    return emptyList();
  }

  public Set<String> getReleaseNumbers(List<V1Secret> releases) {
    return releases.stream()
        .filter(release
            -> release.getMetadata() != null && release.getMetadata().getLabels() != null
                && release.getMetadata().getLabels().containsKey(RELEASE_NUMBER_LABEL_KEY))
        .map(release -> release.getMetadata().getLabels().get(RELEASE_NUMBER_LABEL_KEY))
        .collect(Collectors.toSet());
  }

  public String getReleaseLabelValue(V1Secret release, String labelKey) {
    if (release != null && release.getMetadata() != null && release.getMetadata().getLabels() != null
        && release.getMetadata().getLabels().containsKey(labelKey)) {
      return release.getMetadata().getLabels().get(labelKey);
    }
    return EMPTY;
  }

  public V1Secret setResourcesInRelease(V1Secret release, List<KubernetesResource> resources) throws IOException {
    String manifestsYaml = ManifestHelper.toYaml(resources);
    byte[] compressedYaml = compressString(manifestsYaml, Deflater.BEST_COMPRESSION);
    release.setData(Map.of(RELEASE_KEY, compressedYaml));
    return release;
  }

  String createSetBasedArg(String key, Set<String> values) {
    return String.format(RELEASE_LABEL_QUERY_SET_FORMAT, key, String.join(SECRET_LABEL_DELIMITER, values));
  }

  String createListBasedArg(String key, String value) {
    return String.format(RELEASE_LABEL_QUERY_LIST_FORMAT, key, value);
  }

  String createCommaSeparatedKeyValueList(Map<String, String> k8sArg) {
    return k8sArg.entrySet()
        .stream()
        .map(entry -> createListBasedArg(entry.getKey(), entry.getValue()))
        .collect(Collectors.joining(SECRET_LABEL_DELIMITER));
  }

  String generateName(String releaseName, int releaseNumber) {
    return RELEASE_KEY + RELEASE_NAME_DELIMITER + releaseName + RELEASE_NAME_DELIMITER + releaseNumber;
  }

  Map<String, String> generateLabels(String releaseName, int releaseNumber, String status) {
    return Map.of(RELEASE_KEY, releaseName, RELEASE_NUMBER_LABEL_KEY, String.valueOf(releaseNumber),
        RELEASE_OWNER_LABEL_KEY, RELEASE_OWNER_LABEL_VALUE, RELEASE_STATUS_LABEL_KEY, status);
  }

  boolean checkReleaseForColoredWorkloads(V1Secret release, String color) {
    return getWorkloads(getResourcesFromRelease(release))
        .stream()
        .anyMatch(resource -> resource.getResourceId().getName().endsWith(color));
  }
}
