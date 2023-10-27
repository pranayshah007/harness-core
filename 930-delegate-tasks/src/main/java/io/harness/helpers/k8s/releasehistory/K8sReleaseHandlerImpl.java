/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_TYPE_MAP;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_TYPE_VALUE;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.SECRET_LABEL_DELIMITER;

import static java.lang.String.format;
import static java.lang.String.join;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.k8s.ReleaseMetadata;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sBGReleaseHistoryCleanupDTO;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.K8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sReleaseHistoryCleanupDTO;
import io.harness.k8s.releasehistory.K8sReleaseHistoryHelper;
import io.harness.k8s.releasehistory.K8sReleasePersistDTO;
import io.harness.k8s.releasehistory.K8sReleaseSecretHelper;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class K8sReleaseHandlerImpl implements K8sReleaseHandler {
  @Inject private final KubernetesContainerService kubernetesContainerService;

  @Override
  public IK8sReleaseHistory getReleaseHistory(KubernetesConfig kubernetesConfig, String releaseName) {
    Map<String, String> labels = K8sReleaseSecretHelper.createLabelsMap(releaseName);

    String labelArg = K8sReleaseSecretHelper.createCommaSeparatedKeyValueList(labels);
    String fieldArg = K8sReleaseSecretHelper.createCommaSeparatedKeyValueList(RELEASE_SECRET_TYPE_MAP);
    List<V1Secret> releaseSecrets =
        kubernetesContainerService.getSecretsWithLabelsAndFields(kubernetesConfig, labelArg, fieldArg);
    List<K8sRelease> releases = createReleasesFromSecrets(releaseSecrets);

    return K8sReleaseHistory.builder().releaseHistory(releases).build();
  }

  @Override
  public IK8sRelease createRelease(String name, int number) {
    String status = IK8sRelease.Status.InProgress.name();
    String generatedReleaseName = K8sReleaseSecretHelper.generateName(name, number);
    Map<String, String> labels = K8sReleaseSecretHelper.generateLabels(name, number, status);
    V1Secret releaseSecret =
        new V1SecretBuilder()
            .withMetadata(new V1ObjectMetaBuilder().withName(generatedReleaseName).withLabels(labels).build())
            .withType(RELEASE_SECRET_TYPE_VALUE)
            .build();

    return K8sRelease.builder().releaseSecret(releaseSecret).build();
  }

  @Override
  public IK8sRelease createRelease(String name, int number, ReleaseMetadata releaseMetadata) {
    K8sRelease release = (K8sRelease) createRelease(name, number);
    release.setReleaseMetadata(releaseMetadata);
    return release;
  }

  @Override
  public void saveRelease(K8sReleasePersistDTO releasePersistDTO) throws InvalidRequestException {
    K8sRelease release = (K8sRelease) releasePersistDTO.getRelease();
    V1Secret releaseSecret = K8sReleaseSecretHelper.resetSecretVersionMetadata(release.getReleaseSecret());

    kubernetesContainerService.createOrReplaceSecret(releasePersistDTO.getKubernetesConfig(), releaseSecret);
  }

  @Override
  public void cleanReleaseHistory(K8sReleaseHistoryCleanupDTO releaseCleanupDTO) throws InvalidRequestException {
    K8sReleaseHistory releaseHistory = (K8sReleaseHistory) releaseCleanupDTO.getReleaseHistory();
    LogCallback logCallback = releaseCleanupDTO.getLogCallback();
    Set<String> releasesToDelete =
        K8sReleaseHistoryHelper.getReleaseNumbersToClean(releaseHistory, releaseCleanupDTO.getCurrentReleaseNumber());
    if (isNotEmpty(releasesToDelete)) {
      logCallback.saveExecutionLog(
          format("Release numbers to be deleted are: %s", join(SECRET_LABEL_DELIMITER, releasesToDelete)));
      deleteReleases(releaseCleanupDTO.getKubernetesConfig(), releaseCleanupDTO.getReleaseName(), releasesToDelete);
    }
  }

  @Override
  public void cleanReleaseHistoryBG(K8sBGReleaseHistoryCleanupDTO releaseHistoryCleanupDTO)
      throws InvalidRequestException {
    LogCallback logCallback = releaseHistoryCleanupDTO.getLogCallback();
    List<K8sRelease> releasesToClean = releaseHistoryCleanupDTO.getReleasesToClean()
                                           .stream()
                                           .map(K8sRelease.class ::cast)
                                           .collect(Collectors.toList());
    if (isNotEmpty(releasesToClean)) {
      Set<String> releaseNumbersToClean = K8sReleaseHistoryHelper.getReleaseNumbersFromReleases(releasesToClean);
      logCallback.saveExecutionLog(
          format("Release numbers to be deleted are: %s", join(SECRET_LABEL_DELIMITER, releaseNumbersToClean)));

      deleteReleases(releaseHistoryCleanupDTO.getKubernetesConfig(), releaseHistoryCleanupDTO.getReleaseName(),
          releaseNumbersToClean);
    }
  }

  @Override
  public List<KubernetesResourceId> getResourceIdsToDelete(
      String releaseName, KubernetesConfig kubernetesConfig, LogCallback logCallback) {
    K8sReleaseHistory releaseHistory = (K8sReleaseHistory) getReleaseHistory(kubernetesConfig, releaseName);

    Map<String, KubernetesResourceId> kubernetesResourceIdMap = new HashMap<>();
    for (K8sRelease release : releaseHistory.getReleaseHistory()) {
      release.getResourceIds().forEach(
          resourceId -> kubernetesResourceIdMap.put(resourceId.namespaceKindNameRef(), resourceId));

      // Create entry for release secret and add to map
      String releaseSecretName = K8sReleaseSecretHelper.getSecretName(release.getReleaseSecret());
      KubernetesResourceId releaseSecretResource = KubernetesResourceId.builder()
                                                       .name(releaseSecretName)
                                                       .kind(Kind.Secret.name())
                                                       .namespace(kubernetesConfig.getNamespace())
                                                       .build();
      kubernetesResourceIdMap.put(releaseSecretResource.namespaceKindNameRef(), releaseSecretResource);
    }

    return new ArrayList<>(kubernetesResourceIdMap.values());
  }

  private List<K8sRelease> createReleasesFromSecrets(List<V1Secret> releaseSecrets) {
    return releaseSecrets.stream()
        .map(releaseSecret -> K8sRelease.builder().releaseSecret(releaseSecret).build())
        .collect(Collectors.toList());
  }

  private void deleteReleases(KubernetesConfig kubernetesConfig, String releaseName, Set<String> releaseNumbers)
      throws InvalidRequestException {
    String labelArg = join(SECRET_LABEL_DELIMITER, K8sReleaseSecretHelper.createListBasedArg(RELEASE_KEY, releaseName),
        K8sReleaseSecretHelper.createSetBasedArg(RELEASE_NUMBER_LABEL_KEY, releaseNumbers));
    String fieldArg = K8sReleaseSecretHelper.createCommaSeparatedKeyValueList(RELEASE_SECRET_TYPE_MAP);
    kubernetesContainerService.deleteSecrets(kubernetesConfig, labelArg, fieldArg);
  }
}
