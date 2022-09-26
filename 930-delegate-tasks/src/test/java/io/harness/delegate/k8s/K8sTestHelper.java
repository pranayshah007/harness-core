/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.k8s.manifest.ManifestHelper.getKubernetesResourceFromSpec;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.releasehistory.K8sLegacyRelease;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(HarnessTeam.CDP)
public class K8sTestHelper {
  public static final String BASE_PATH = "k8s";
  public static final String CONFIG_MAP = "configMap.yaml";
  public static final String DEPLOYMENT_CONFIG = "deployment-config.yaml";
  public static final String DEPLOYMENT = "deployment.yaml";
  public static final String SERVICE = "service.yaml";
  public static final String CRD_OLD = "crd-old.yaml";
  public static final String CRD_NEW = "crd-new.yaml";
  public static final String EMPTY_RELEASE_HISTORY = "empty-release-history.yaml";
  public static final String RELEASE_HISTORY = "release-history.yaml";
  public static final String RELEASE_HISTORY_CANARY = "release-history-canary.yaml";
  public static final String RELEASE_HISTORY_FAILED_CANARY = "release-history-canary-failed.yaml";

  public static KubernetesResource configMap() throws IOException {
    String yamlFileContent = readResourceFileContent(CONFIG_MAP);
    KubernetesResource kubernetesResource = getKubernetesResourceFromSpec(yamlFileContent);
    kubernetesResource.getResourceId().setVersioned(true);
    return kubernetesResource;
  }

  public static KubernetesResource deploymentConfig() throws IOException {
    String yamlFileContent = readResourceFileContent(DEPLOYMENT_CONFIG);
    return getKubernetesResourceFromSpec(yamlFileContent);
  }

  public static KubernetesResource deployment() throws IOException {
    String yamlFileContent = readResourceFileContent(DEPLOYMENT);
    return getKubernetesResourceFromSpec(yamlFileContent);
  }

  public static KubernetesResource crdOld() throws IOException {
    String yamlFileContent = readResourceFileContent(CRD_OLD);
    return getKubernetesResourceFromSpec(yamlFileContent);
  }

  public static KubernetesResource crdNew() throws IOException {
    String yamlFileContent = readResourceFileContent(CRD_NEW);
    return getKubernetesResourceFromSpec(yamlFileContent);
  }

  public static KubernetesResource service() throws IOException {
    String yamlFileContent = readResourceFileContent(SERVICE);
    return getKubernetesResourceFromSpec(yamlFileContent);
  }

  public static String readResourceFileContent(String resourceFilePath) throws IOException {
    ClassLoader classLoader = K8sTestHelper.class.getClassLoader();
    return Resources.toString(
        Objects.requireNonNull(classLoader.getResource(BASE_PATH + "/" + resourceFilePath)), StandardCharsets.UTF_8);
  }

  public static K8sLegacyRelease buildRelease(K8sLegacyRelease.Status status, int number) throws IOException {
    return K8sLegacyRelease.builder()
        .number(number)
        .resources(asList(deployment().getResourceId(), configMap().getResourceId()))
        .managedWorkload(deployment().getResourceId())
        .status(status)
        .build();
  }

  public static K8sLegacyRelease buildReleaseMultipleManagedWorkloads(K8sLegacyRelease.Status status)
      throws IOException {
    return K8sLegacyRelease.builder()
        .resources(asList(deployment().getResourceId(), configMap().getResourceId()))
        .managedWorkloads(asList(K8sLegacyRelease.KubernetesResourceIdRevision.builder()
                                     .workload(deploymentConfig().getResourceId())
                                     .revision("2")
                                     .build(),
            K8sLegacyRelease.KubernetesResourceIdRevision.builder()
                .workload(deployment().getResourceId())
                .revision("2")
                .build()))
        .status(status)
        .build();
  }

  public static ProcessResult buildProcessResult(int exitCode, String output) {
    return new ProcessResult(exitCode, new ProcessOutput(output.getBytes()));
  }

  public static ProcessResult buildProcessResult(int exitCode) {
    return new ProcessResult(exitCode, new ProcessOutput(null));
  }
}
