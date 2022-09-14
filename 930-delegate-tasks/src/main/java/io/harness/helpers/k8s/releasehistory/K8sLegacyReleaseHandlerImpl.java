/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sReleaseCleanupDTO;
import io.harness.k8s.releasehistory.K8sReleasePersistDTO;
import io.harness.k8s.releasehistory.ReleaseHistory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
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
  public void saveRelease(K8sReleasePersistDTO releasePersistDTO) throws Exception {
    k8sTaskHelperBase.saveReleaseHistory(releasePersistDTO.getKubernetesConfig(), releasePersistDTO.getReleaseName(),
        releasePersistDTO.getReleaseHistoryYaml(), releasePersistDTO.isStoreInSecrets());
  }

  @Override
  public void cleanReleaseHistory(K8sReleaseCleanupDTO releaseCleanupDTO) throws Exception {
    ReleaseHistory releaseHistory = (ReleaseHistory) releaseCleanupDTO.getReleaseHistory();
    k8sTaskHelperBase.cleanup(releaseCleanupDTO.getClient(), releaseCleanupDTO.getDelegateTaskParams(), releaseHistory,
        releaseCleanupDTO.getLogCallback());
  }
}
