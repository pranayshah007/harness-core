/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.releasehistory;

import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;

import io.kubernetes.client.openapi.models.V1Secret;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ReleaseHistoryService {
  V1Secret createRelease(String releaseName, int releaseNumber, String releaseYaml, String status) throws IOException;
  V1Secret updateReleaseStatus(V1Secret release, String status);
  V1Secret saveRelease(V1Secret release, KubernetesConfig kubernetesConfig);
  V1Secret setResourcesInRelease(V1Secret release, List<KubernetesResource> resources) throws IOException;
  List<V1Secret> getReleaseHistory(
      KubernetesConfig kubernetesConfig, Map<String, String> labels, Map<String, String> fields);
  int getCurrentReleaseNumber(List<V1Secret> releases);
  V1Secret getLastSuccessfulRelease(List<V1Secret> releases, int currentReleaseNumber);
  V1Secret getLatestRelease(List<V1Secret> releases);
  List<KubernetesResource> getResourcesFromRelease(V1Secret release) throws IOException;
  void deleteReleases(KubernetesConfig kubernetesConfig, String releaseName, Set<String> releaseNumbers);
  void cleanReleases(
      KubernetesConfig kubernetesConfig, String releaseName, int currentReleaseNumber, List<V1Secret> releases);
}
