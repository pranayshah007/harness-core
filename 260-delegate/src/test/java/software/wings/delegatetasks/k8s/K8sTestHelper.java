/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.manifest.ManifestHelper.getKubernetesResourceFromSpec;

import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.releasehistory.K8sLegacyRelease;

import com.google.common.io.Resources;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sTestHelper {
  private static String resourcePath = "k8s";
  private static String deploymentYaml = "deployment.yaml";
  private static String deploymentConfigYaml = "deployment-config.yaml";
  private static String configMapYaml = "configMap.yaml";
  private static String serviceYaml = "service.yaml";
  private static String primaryServiceYaml = "primaryService.yaml";
  private static String stageServiceYaml = "stageService.yaml";
  private static String namespaceYaml = "namespace.yaml";
  private static String configMapPrunedYaml = "configMapPruned.yaml";

  public static KubernetesResource configMap() throws IOException {
    String yamlString = readFileContent(configMapYaml, resourcePath);
    KubernetesResource kubernetesResource = getKubernetesResourceFromSpec(yamlString);
    kubernetesResource.getResourceId().setVersioned(true);
    return kubernetesResource;
  }

  public static KubernetesResource stageService() throws IOException {
    String yamlString = readFileContent(stageServiceYaml, resourcePath);
    return getKubernetesResourceFromSpec(yamlString);
  }

  public static KubernetesResource namespace() throws IOException {
    String yamlString = readFileContent(namespaceYaml, resourcePath);
    return getKubernetesResourceFromSpec(yamlString);
  }

  public static KubernetesResource primaryService() throws IOException {
    String yamlString = readFileContent(primaryServiceYaml, resourcePath);
    return getKubernetesResourceFromSpec(yamlString);
  }

  public static KubernetesResource service() throws IOException {
    String yamlString = readFileContent(serviceYaml, resourcePath);
    return getKubernetesResourceFromSpec(yamlString);
  }

  public static KubernetesResource deployment() throws IOException {
    String yamlString = readFileContent(deploymentYaml, resourcePath);
    return getKubernetesResourceFromSpec(yamlString);
  }

  public static KubernetesResource configMapPruned() throws IOException {
    String yamlString = readFileContent(configMapPrunedYaml, resourcePath);
    return getKubernetesResourceFromSpec(yamlString);
  }

  public static KubernetesResource deploymentConfig() throws IOException {
    String yamlString = readFileContent(deploymentConfigYaml, resourcePath);
    return getKubernetesResourceFromSpec(yamlString);
  }

  public static String readFileContent(String filePath, String resourcePath) throws IOException {
    ClassLoader classLoader = K8sTestHelper.class.getClassLoader();
    return Resources.toString(Objects.requireNonNull(classLoader.getResource(resourcePath + PATH_DELIMITER + filePath)),
        StandardCharsets.UTF_8);
  }

  public static K8sLegacyRelease buildRelease(K8sLegacyRelease.Status status, int number) throws IOException {
    return K8sLegacyRelease.builder()
        .number(number)
        .resources(asList(deployment().getResourceId(), configMap().getResourceId()))
        .managedWorkload(deployment().getResourceId())
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
