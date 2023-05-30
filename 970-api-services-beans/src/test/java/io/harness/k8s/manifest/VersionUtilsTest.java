/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.manifest;

import static io.harness.k8s.manifest.ManifestHelper.processYaml;
import static io.harness.k8s.manifest.VersionUtils.CONFIGMAP_SECRET_MAX_NAME_LENGTH;
import static io.harness.k8s.manifest.VersionUtils.CONFIGMAP_SECRET_SUFFIX_APPEND_FAILURE_MESSAGE;
import static io.harness.k8s.manifest.VersionUtils.addRevisionNumber;
import static io.harness.k8s.manifest.VersionUtils.addSuffixToConfigmapsAndSecrets;
import static io.harness.k8s.manifest.VersionUtils.markVersionedResources;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.model.K8sRequestHandlerContext;
import io.harness.k8s.model.KubernetesResource;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class VersionUtilsTest extends CategoryTest {
  @Mock LogCallback logCallback;
  private AutoCloseable mocks;

  @Before
  public void setup() {
    mocks = MockitoAnnotations.openMocks(this);
    doNothing().when(logCallback).saveExecutionLog(anyString(), any(LogLevel.class));
  }

  @After
  public void shutdown() throws Exception {
    mocks.close();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void configMapAndPodEnvTest() throws Exception {
    URL url = this.getClass().getResource("/configmap-pod-env.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);

    List<KubernetesResource> resources = processYaml(fileContents);

    List<KubernetesResource> resourcesWithRevision = processYaml(fileContents);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithRevision);

    addRevisionNumber(context, 1);

    assertThat(resourcesWithRevision.get(0).getResourceId().isVersioned()).isEqualTo(true);

    assertThat(resourcesWithRevision.get(0).getField("metadata.name"))
        .isEqualTo(resources.get(0).getField("metadata.name") + "-1");

    assertThat(resourcesWithRevision.get(1).getField("spec.containers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo(resources.get(1).getField("spec.containers[0].env[0].valueFrom.configMapKeyRef.name") + "-1");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void configMapsAndPodEnvTest() throws Exception {
    URL url = this.getClass().getResource("/configmap-pod-env.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);

    List<KubernetesResource> resources = processYaml(fileContents);

    List<KubernetesResource> resourcesWithRevision = processYaml(fileContents);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithRevision);

    addRevisionNumber(context, 1);

    assertThat(resourcesWithRevision.get(0).getField("metadata.name"))
        .isEqualTo(resources.get(0).getField("metadata.name") + "-1");

    assertThat(resourcesWithRevision.get(1).getField("spec.containers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo(resources.get(1).getField("spec.containers[0].env[0].valueFrom.configMapKeyRef.name") + "-1");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void twoConfigMapsAndPodEnvTest() throws Exception {
    URL url = this.getClass().getResource("/two-configmap-pod-env.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);

    List<KubernetesResource> resources = processYaml(fileContents);

    int revision = 100;

    List<KubernetesResource> resourcesWithRevision = processYaml(fileContents);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithRevision);

    addRevisionNumber(context, revision);

    assertThat(resourcesWithRevision.get(0).getField("metadata.name"))
        .isEqualTo(resources.get(0).getField("metadata.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(1).getField("metadata.name"))
        .isEqualTo(resources.get(1).getField("metadata.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(2).getField("spec.containers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo(
            resources.get(2).getField("spec.containers[0].env[0].valueFrom.configMapKeyRef.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(2).getField("spec.containers[0].env[1].valueFrom.configMapKeyRef.name"))
        .isEqualTo(
            resources.get(2).getField("spec.containers[0].env[1].valueFrom.configMapKeyRef.name") + "-" + revision);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void configMapsAndPodEnvFromTest() throws Exception {
    URL url = this.getClass().getResource("/configmap-pod-envfrom.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);

    List<KubernetesResource> resources = processYaml(fileContents);

    int revision = 100;

    List<KubernetesResource> resourcesWithRevision = processYaml(fileContents);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithRevision);

    addRevisionNumber(context, revision);

    assertThat(resourcesWithRevision.get(0).getField("metadata.name"))
        .isEqualTo(resources.get(0).getField("metadata.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(1).getField("spec.containers[0].envFrom[0].configMapRef.name"))
        .isEqualTo(resources.get(1).getField("spec.containers[0].envFrom[0].configMapRef.name") + "-" + revision);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void configMapsAndPodVolumeTest() throws Exception {
    URL url = this.getClass().getResource("/configmap-pod-volume.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);

    List<KubernetesResource> resources = processYaml(fileContents);

    int revision = 100;

    List<KubernetesResource> resourcesWithRevision = processYaml(fileContents);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithRevision);

    addRevisionNumber(context, revision);

    assertThat(resourcesWithRevision.get(0).getField("metadata.name"))
        .isEqualTo(resources.get(0).getField("metadata.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(1).getField("spec.volumes[0].configMap.name"))
        .isEqualTo(resources.get(1).getField("spec.volumes[0].configMap.name") + "-" + revision);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void configMapAndRedisPodVolumeTest() throws Exception {
    URL url = this.getClass().getResource("/configmap-redis-pod-volume.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);

    List<KubernetesResource> resources = processYaml(fileContents);

    int revision = 100;

    List<KubernetesResource> resourcesWithRevision = processYaml(fileContents);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithRevision);

    addRevisionNumber(context, revision);

    assertThat(resourcesWithRevision.get(0).getField("metadata.name"))
        .isEqualTo(resources.get(0).getField("metadata.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(1).getField("spec.volumes[1].configMap.name"))
        .isEqualTo(resources.get(1).getField("spec.volumes[1].configMap.name") + "-" + revision);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void secretAndPodVolumeTest() throws Exception {
    URL url = this.getClass().getResource("/secret-pod-volume.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);

    List<KubernetesResource> resources = processYaml(fileContents);

    int revision = 100;

    List<KubernetesResource> resourcesWithRevision = processYaml(fileContents);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithRevision);

    addRevisionNumber(context, revision);

    assertThat(resourcesWithRevision.get(0).getField("metadata.name"))
        .isEqualTo(resources.get(0).getField("metadata.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(1).getField("spec.volumes[0].secret.secretName"))
        .isEqualTo(resources.get(1).getField("spec.volumes[0].secret.secretName") + "-" + revision);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void nginxDeploymentVersionTest() throws Exception {
    URL url = this.getClass().getResource("/nginx-full.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);

    List<KubernetesResource> resources = processYaml(fileContents);

    int revision = 100;

    List<KubernetesResource> resourcesWithRevision = processYaml(fileContents);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithRevision);

    addRevisionNumber(context, revision);

    assertThat(resourcesWithRevision.get(0).getField("metadata.name"))
        .isEqualTo(resources.get(0).getField("metadata.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(1).getField("metadata.name"))
        .isEqualTo(resources.get(1).getField("metadata.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(2).getField("metadata.name"))
        .isEqualTo(resources.get(2).getField("metadata.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(3).getField("spec.template.spec.volumes[0].configMap.name"))
        .isEqualTo(resources.get(3).getField("spec.template.spec.volumes[0].configMap.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(3).getField("spec.template.spec.volumes[1].configMap.name"))
        .isEqualTo(resources.get(3).getField("spec.template.spec.volumes[1].configMap.name") + "-" + revision);

    assertThat(resourcesWithRevision.get(3).getField("spec.template.spec.volumes[2].secret.secretName"))
        .isEqualTo(resources.get(3).getField("spec.template.spec.volumes[2].secret.secretName") + "-" + revision);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void configMapWithDirectApplyAndPodVolumeTest() throws Exception {
    URL url = this.getClass().getResource("/configmap-skip-versioning-pod-env.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);

    List<KubernetesResource> resources = processYaml(fileContents);

    int revision = 100;

    List<KubernetesResource> resourcesWithRevision = processYaml(fileContents);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithRevision);

    addRevisionNumber(context, revision);

    assertThat(resourcesWithRevision.get(0).getResourceId().isVersioned()).isEqualTo(false);

    assertThat(resourcesWithRevision.get(0).getField("metadata.name"))
        .isEqualTo(resources.get(0).getField("metadata.name"));

    assertThat(resourcesWithRevision.get(1).getField("spec.containers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo(resources.get(1).getField("spec.containers[0].env[0].valueFrom.configMapKeyRef.name"));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMarkVersionedResources() throws IOException {
    URL url = this.getClass().getResource("/configmap-pod-env.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    List<KubernetesResource> resources = processYaml(fileContents);

    List<KubernetesResource> resourcesAfterMarkedVersioned = processYaml(fileContents);
    markVersionedResources(resourcesAfterMarkedVersioned);

    assertThat(resourcesAfterMarkedVersioned.get(0).getField("metadata.name"))
        .isEqualTo(resources.get(0).getField("metadata.name"));
    assertThat(resourcesAfterMarkedVersioned.get(0).getResourceId().isVersioned()).isTrue();
    assertThat(resourcesAfterMarkedVersioned.get(1).getResourceId().isVersioned()).isFalse();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSuffixAdditionAndRefUpdatesInWorkloadsForConfigMapsAndSecrets() throws IOException {
    String testSuffix = "testsuffix";
    URL url = this.getClass().getResource("/add-suffix-to-configmaps-secrets.yaml");
    String fileContents = Resources.toString(url, StandardCharsets.UTF_8);
    List<KubernetesResource> resources = processYaml(fileContents);
    List<KubernetesResource> resourcesWithSuffixedResources = processYaml(fileContents);

    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithSuffixedResources);

    addSuffixToConfigmapsAndSecrets(context, testSuffix, logCallback);

    assertThat(resourcesWithSuffixedResources.get(2).getField(
                   "spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo(resources.get(2).getField("spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name")
            + "-" + testSuffix);

    assertThat(resourcesWithSuffixedResources.get(2).getField(
                   "spec.template.spec.containers[0].env[1].valueFrom.secretKeyRef.name"))
        .isEqualTo(resources.get(2).getField("spec.template.spec.containers[0].env[1].valueFrom.secretKeyRef.name")
            + "-" + testSuffix);

    assertThat(
        resourcesWithSuffixedResources.get(2).getField("spec.template.spec.containers[0].envFrom[0].configMapRef.name"))
        .isEqualTo(resources.get(2).getField("spec.template.spec.containers[0].envFrom[0].configMapRef.name") + "-"
            + testSuffix);

    assertThat(
        resourcesWithSuffixedResources.get(2).getField("spec.template.spec.containers[0].envFrom[1].secretRef.name"))
        .isEqualTo(
            resources.get(2).getField("spec.template.spec.containers[0].envFrom[1].secretRef.name") + "-" + testSuffix);

    assertThat(resourcesWithSuffixedResources.get(2).getField("spec.template.spec.volumes[0].configMap.name"))
        .isEqualTo(resources.get(2).getField("spec.template.spec.volumes[0].configMap.name") + "-" + testSuffix);
    assertThat(resourcesWithSuffixedResources.get(2).getField("spec.template.spec.volumes[1].secret.secretName"))
        .isEqualTo(resources.get(2).getField("spec.template.spec.volumes[1].secret.secretName") + "-" + testSuffix);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSuffixLengthEdgeCases() throws IOException {
    String name512 = generateString(512);
    String toBeIgnoredSuffix = "to-be-ignored";
    assertConfigMapName(name512, toBeIgnoredSuffix, name512);

    String name253 = generateString(CONFIGMAP_SECRET_MAX_NAME_LENGTH);
    assertConfigMapName(name253, toBeIgnoredSuffix, name253);
    verify(logCallback, times(1))
        .saveExecutionLog(
            format(CONFIGMAP_SECRET_SUFFIX_APPEND_FAILURE_MESSAGE, "-" + toBeIgnoredSuffix), LogLevel.WARN);
    Mockito.clearInvocations(logCallback);

    String name252 = generateString(CONFIGMAP_SECRET_MAX_NAME_LENGTH - 1);
    assertConfigMapName(name252, toBeIgnoredSuffix, name252);
    verify(logCallback, times(1))
        .saveExecutionLog(
            format(CONFIGMAP_SECRET_SUFFIX_APPEND_FAILURE_MESSAGE, "-" + toBeIgnoredSuffix), LogLevel.WARN);

    String name64 = generateString(64);
    String suffix = "to-add";
    String expectedName = name64 + "-" + suffix;
    assertConfigMapName(name64, suffix, expectedName);

    String name250 = generateString(250);
    expectedName = name250 + "-"
        + "to";
    assertConfigMapName(name250, suffix, expectedName);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testSuffixAdditionOnlyForVersionedResourcesAndNotForExternalConfigmapsSecrets() throws IOException {
    String testSuffix = "testsuffix";
    URL url = this.getClass().getResource("/add-suffix-to-only-required-configmaps-secrets.yaml");
    String fileContents = Resources.toString(url, StandardCharsets.UTF_8);
    List<KubernetesResource> resources = processYaml(fileContents);
    List<KubernetesResource> resourcesWithSuffixedResources = processYaml(fileContents);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(resourcesWithSuffixedResources);

    addSuffixToConfigmapsAndSecrets(context, testSuffix, logCallback);

    assertThat(resourcesWithSuffixedResources.get(2).getField(
                   "spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo(resources.get(2).getField("spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name"));

    assertThat(resourcesWithSuffixedResources.get(2).getField(
                   "spec.template.spec.containers[0].env[1].valueFrom.secretKeyRef.name"))
        .isEqualTo(resources.get(2).getField("spec.template.spec.containers[0].env[1].valueFrom.secretKeyRef.name"));

    assertThat(
        resourcesWithSuffixedResources.get(2).getField("spec.template.spec.containers[0].envFrom[0].configMapRef.name"))
        .isEqualTo(resources.get(2).getField("spec.template.spec.containers[0].envFrom[0].configMapRef.name") + "-"
            + testSuffix);

    assertThat(
        resourcesWithSuffixedResources.get(2).getField("spec.template.spec.containers[0].envFrom[1].secretRef.name"))
        .isEqualTo(
            resources.get(2).getField("spec.template.spec.containers[0].envFrom[1].secretRef.name") + "-" + testSuffix);

    assertThat(resourcesWithSuffixedResources.get(2).getField("spec.template.spec.volumes[0].configMap.name"))
        .isEqualTo(resources.get(2).getField("spec.template.spec.volumes[0].configMap.name") + "-" + testSuffix);
    assertThat(resourcesWithSuffixedResources.get(2).getField("spec.template.spec.volumes[1].secret.secretName"))
        .isEqualTo(resources.get(2).getField("spec.template.spec.volumes[1].secret.secretName") + "-" + testSuffix);
  }

  private void assertConfigMapName(String name, String suffix, String expectedName) throws IOException {
    URL url = this.getClass().getResource("/configmap.yaml");
    String fileContents = Resources.toString(url, StandardCharsets.UTF_8);
    List<KubernetesResource> resources = processYaml(fileContents);

    KubernetesResource configmap = resources.get(0);
    configmap.getResourceId().setName(name);
    configmap.setSpec(configmap.getSpec().replace("name: configmap", "name: " + name));

    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(List.of(configmap));

    addSuffixToConfigmapsAndSecrets(context, suffix, logCallback);
    assertThat(configmap.getResourceId().getName()).isEqualTo(expectedName);
  }

  private String generateString(int length) {
    return StringUtils.repeat("X", length);
  }
}
