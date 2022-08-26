/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.releasehistory;

import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_HARNESS_SECRET_TYPE;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_NAME_DELIMITER;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_VALUE;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_TYPE_VALUE;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.SECRET_LABEL_DELIMITER;

import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class K8sReleaseHistoryService {
  @Inject KubernetesContainerService kubernetesContainerService;
  @Inject K8sReleaseService releaseService;

  public V1Secret createRelease(String releaseName, int releaseNumber, String status) throws IOException {
    return new V1SecretBuilder()
        .withMetadata(
            new V1ObjectMetaBuilder()
                .withName(RELEASE_KEY + RELEASE_NAME_DELIMITER + releaseName + RELEASE_NAME_DELIMITER + releaseNumber)
                .withLabels(Map.of(RELEASE_KEY, releaseName, RELEASE_NUMBER_LABEL_KEY, String.valueOf(releaseNumber),
                    RELEASE_OWNER_LABEL_KEY, RELEASE_OWNER_LABEL_VALUE, RELEASE_STATUS_LABEL_KEY, status))
                .build())
        .withType(RELEASE_SECRET_TYPE_VALUE)
        .build();
  }

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

  public void deleteReleases(KubernetesConfig kubernetesConfig, String releaseName, Set<String> releaseNumbers) {
    log.info("Release numbers to be deleted are: {}", String.join(SECRET_LABEL_DELIMITER, releaseNumbers));
    String labelArg = releaseService.createListBasedArg(RELEASE_KEY, releaseName) + SECRET_LABEL_DELIMITER
        + releaseService.createSetBasedArg(RELEASE_NUMBER_LABEL_KEY, releaseNumbers);
    String fieldArg = releaseService.createCommaSeparatedKeyValueList(RELEASE_HARNESS_SECRET_TYPE);
    kubernetesContainerService.deleteSecrets(kubernetesConfig, labelArg, fieldArg);
  }

  public void cleanReleaseHistory(
      KubernetesConfig kubernetesConfig, String releaseName, int currentReleaseNumber, List<V1Secret> releases) {
    Set<String> releasesToDelete = releaseService.getReleaseNumbersToClean(releases, currentReleaseNumber);
    deleteReleases(kubernetesConfig, releaseName, releasesToDelete);
  }

  public List<V1Secret> getReleaseHistory(
      KubernetesConfig kubernetesConfig, Map<String, String> labels, Map<String, String> fields) {
    String labelArg = releaseService.createCommaSeparatedKeyValueList(labels);
    String fieldArg = releaseService.createCommaSeparatedKeyValueList(fields);
    return kubernetesContainerService.getSecretsWithLabelsAndFields(kubernetesConfig, labelArg, fieldArg);
  }

  public V1Secret saveRelease(V1Secret release, KubernetesConfig kubernetesConfig) {
    return kubernetesContainerService.createOrReplaceSecret(kubernetesConfig, release);
  }

  public V1Secret markStatusAndSaveRelease(V1Secret release, String status, KubernetesConfig kubernetesConfig) {
    release = releaseService.updateReleaseStatus(release, status);
    return releaseService.saveRelease(release, kubernetesConfig);
  }
}
