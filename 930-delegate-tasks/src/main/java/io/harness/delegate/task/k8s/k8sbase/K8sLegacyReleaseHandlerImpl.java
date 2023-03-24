/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.k8sbase;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sBGReleaseHistoryCleanupDTO;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sReleaseHistoryCleanupDTO;
import io.harness.k8s.releasehistory.K8sReleasePersistDTO;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class K8sLegacyReleaseHandlerImpl implements K8sReleaseHandler {
  @Inject private final K8sTaskHelperBase k8sTaskHelperBase;

  @Override
  public IK8sReleaseHistory getReleaseHistory(KubernetesConfig kubernetesConfig, String releaseName) throws Exception {
    String releaseHistoryData = k8sTaskHelperBase.getReleaseHistoryData(kubernetesConfig, releaseName);
    ReleaseHistory releaseHistory =
        (isEmpty(releaseHistoryData)) ? ReleaseHistory.createNew() : ReleaseHistory.createFromData(releaseHistoryData);
    return K8SLegacyReleaseHistory.builder().releaseHistory(releaseHistory).build();
  }

  @Override
  public IK8sRelease createRelease(String name, int number) {
    return K8sLegacyRelease.builder().number(number).status(IK8sRelease.Status.InProgress).build();
  }

  @Override
  public void saveRelease(K8sReleasePersistDTO releasePersistDTO) throws IOException {
    K8SLegacyReleaseHistory legacyReleaseHistory = (K8SLegacyReleaseHistory) releasePersistDTO.getReleaseHistory();
    String releaseHistoryYaml = legacyReleaseHistory.getReleaseHistory().getAsYaml();
    k8sTaskHelperBase.saveReleaseHistory(releasePersistDTO.getKubernetesConfig(), releasePersistDTO.getReleaseName(),
        releaseHistoryYaml, releasePersistDTO.isStoreInSecrets());
  }

  @Override
  public void cleanReleaseHistory(K8sReleaseHistoryCleanupDTO releaseCleanupDTO) throws Exception {
    K8SLegacyReleaseHistory legacyReleaseHistory = (K8SLegacyReleaseHistory) releaseCleanupDTO.getReleaseHistory();
    k8sTaskHelperBase.cleanup(releaseCleanupDTO.getClient(), releaseCleanupDTO.getDelegateTaskParams(),
        legacyReleaseHistory.getReleaseHistory(), releaseCleanupDTO.getLogCallback());
  }

  @Override
  public void cleanReleaseHistoryBG(K8sBGReleaseHistoryCleanupDTO releaseHistoryCleanupDTO) {
    K8SLegacyReleaseHistory releaseHistory = (K8SLegacyReleaseHistory) releaseHistoryCleanupDTO.getReleaseHistory();
    int releaseNumber = releaseHistoryCleanupDTO.getCurrentReleaseNumber();
    String color = releaseHistoryCleanupDTO.getColor();
    releaseHistory.getReleaseHistory().getReleases().removeIf(release
        -> releaseNumber != release.getReleaseNumber() && release.getManagedWorkload() != null
            && release.getManagedWorkload().getName().endsWith(color));
  }

  @Override
  public List<KubernetesResourceId> getResourceIdsToDelete(
      String releaseName, KubernetesConfig kubernetesConfig, LogCallback logCallback) throws IOException {
    return k8sTaskHelperBase.fetchAllResourcesForRelease(releaseName, kubernetesConfig, logCallback);
  }
}
