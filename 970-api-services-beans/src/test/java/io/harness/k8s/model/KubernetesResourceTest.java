/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.ReportTarget.LOG_SYSTEM;
import static io.harness.k8s.manifest.ManifestHelper.processYaml;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.fail;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesYamlException;
import io.harness.rule.Owner;
import io.harness.yaml.BooleanPatchedRepresenter;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.yaml.snakeyaml.LoaderOptions;

@OwnedBy(CDP)
public class KubernetesResourceTest extends CategoryTest {
  @Before
  public void setup() {
    initializeLogging();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void setAndGetTest() throws Exception {
    URL url = this.getClass().getResource("/deploy.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    assertThat(resource.getField("random")).isEqualTo(null);
    assertThat(resource.getField("random.random")).isEqualTo(null);

    resource.setField("kind", "myKind");
    String kind = (String) resource.getField("kind");
    assertThat(kind).isEqualTo("myKind");

    resource.setField("metadata.name", "myName");
    String name = (String) resource.getField("metadata.name");
    assertThat(name).isEqualTo("myName");

    resource.setField("metadata.labels.key1", "value1");
    String key = (String) resource.getField("metadata.labels.key1");
    assertThat(key).isEqualTo("value1");

    resource.setField("metadata.labels.key2", "value2");
    Map labels = (Map) resource.getField("metadata.labels");
    assertThat(labels).hasSize(3);
    assertThat(labels.get("app")).isEqualTo("nginx");
    assertThat(labels.get("key1")).isEqualTo("value1");
    assertThat(labels.get("key2")).isEqualTo("value2");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void arrayFieldsSetAndGetTest() throws Exception {
    URL url = this.getClass().getResource("/two-containers.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    String containerName = (String) resource.getField("spec.containers[0].name");
    assertThat(containerName).isEqualTo("nginx-container");

    containerName = (String) resource.getField("spec.containers[1].name");
    assertThat(containerName).isEqualTo("debian-container");

    Object obj = resource.getField("spec.containers[0]");
    assertThat(obj).isInstanceOf(Map.class);

    obj = resource.getFields("spec.containers[]");
    assertThat(obj).isInstanceOf(List.class);

    resource.setField("spec.containers[0].name", "hello");
    containerName = (String) resource.getField("spec.containers[0].name");
    assertThat(containerName).isEqualTo("hello");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void addAnnotationTest() throws Exception {
    URL url = this.getClass().getResource("/deploy.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    resource.addAnnotations(ImmutableMap.of("key1", "value1", "key2", "value2"));

    Map annotations = (Map) resource.getField("metadata.annotations");

    assertThat(annotations).hasSize(2);
    assertThat(annotations.get("key1")).isEqualTo("value1");
    assertThat(annotations.get("key2")).isEqualTo("value2");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void addLabelsTest() throws Exception {
    URL url = this.getClass().getResource("/deploy.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    resource.addLabels(ImmutableMap.of("key1", "value1", "key2", "value2"));

    Map labels = (Map) resource.getField("metadata.labels");

    assertThat(labels).hasSize(3);
    assertThat(labels.get("app")).isEqualTo("nginx");
    assertThat(labels.get("key1")).isEqualTo("value1");
    assertThat(labels.get("key2")).isEqualTo("value2");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void nameUpdateTests() throws Exception {
    K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
    nameUpdateTestsUtil("deploy.yaml", true, k8sRequestHandlerContext);
    nameUpdateTestsUtil("service.yaml", true, k8sRequestHandlerContext);
    nameUpdateTestsUtil("configmap.yaml", true, k8sRequestHandlerContext);
    nameUpdateTestsUtil("secret.yaml", true, k8sRequestHandlerContext);
    nameUpdateTestsUtil("daemonset.yaml", false, k8sRequestHandlerContext);
    nameUpdateTestsUtil("deployment-config.yaml", true, k8sRequestHandlerContext);
  }

  private void nameUpdateTestsUtil(String resourceFile, boolean shouldNameChange,
      K8sRequestHandlerContext k8sRequestHandlerContext) throws Exception {
    URL url = this.getClass().getResource("/" + resourceFile);
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    List<KubernetesResource> resources = processYaml(fileContents);
    k8sRequestHandlerContext.setResources(resources);
    KubernetesResource resource = resources.get(0);
    UnaryOperator<Object> appendRevision = t -> t + "-1";

    String oldName = (String) resource.getField("metadata.name");

    resource.transformName(appendRevision, k8sRequestHandlerContext);

    if (shouldNameChange) {
      assertThat(resource.getField("metadata.name")).isEqualTo(oldName + "-1");
    } else {
      assertThat(resource.getField("metadata.name")).isEqualTo(oldName);
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void nameUpdateWithHPATest() throws Exception {
    nameUpdateWithHPATest("deployment-with-multiple-hpa-resources.yaml", Kind.Deployment, true);
    nameUpdateWithHPATest("deployment-with-multiple-hpa-resources.yaml", Kind.Deployment, false);
    nameUpdateWithHPATest("statefulset-with-multiple-hpa-resources.yaml", Kind.StatefulSet, true);
    nameUpdateWithHPATest("statefulset-with-multiple-hpa-resources.yaml", Kind.StatefulSet, false);
    nameUpdateWithHPATest("deploymentconfig-with-multiple-hpa-resources.yaml", Kind.DeploymentConfig, true);
    nameUpdateWithHPATest("deploymentconfig-with-multiple-hpa-resources.yaml", Kind.DeploymentConfig, false);
  }

  private void nameUpdateWithHPATest(String resourceFile, Kind workloadKind, boolean enabledHPAAndPDBSupport)
      throws Exception {
    K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
    URL url = this.getClass().getResource("/" + resourceFile);
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    List<KubernetesResource> resources = processYaml(fileContents);
    k8sRequestHandlerContext.setResources(resources);
    k8sRequestHandlerContext.setEnabledSupportHPAAndPDB(enabledHPAAndPDBSupport);

    UnaryOperator<Object> appendRevision = t -> t + "-addedSuffix";
    KubernetesResource managedWorkload =
        resources.stream()
            .filter(resource -> resource.getResourceId().getKind().equals(workloadKind.name()))
            .collect(Collectors.toList())
            .get(0);
    String oldWorkloadName = (String) managedWorkload.getField("metadata.name");
    String newWorkloadName = oldWorkloadName + "-addedSuffix";

    List<KubernetesResource> hpaResourcesToBeUpdated =
        resources.stream()
            .filter(resource -> resource.getResourceId().getKind().equals(Kind.HorizontalPodAutoscaler.name()))
            .filter(resource
                -> String.valueOf(resource.getField("spec.scaleTargetRef.name")).equalsIgnoreCase(oldWorkloadName))
            .collect(Collectors.toList());
    assertThat(hpaResourcesToBeUpdated.size()).isEqualTo(3);

    List<KubernetesResource> hpaResourcesNotToBeUpdated =
        resources.stream()
            .filter(resource -> resource.getResourceId().getKind().equals(Kind.HorizontalPodAutoscaler.name()))
            .filter(resource
                -> !String.valueOf(resource.getField("spec.scaleTargetRef.name")).equalsIgnoreCase(oldWorkloadName))
            .collect(Collectors.toList());
    List<String> targetRefNames = hpaResourcesNotToBeUpdated.stream()
                                      .map(resource -> String.valueOf(resource.getField("spec.scaleTargetRef.name")))
                                      .collect(Collectors.toList());
    assertThat(hpaResourcesNotToBeUpdated.size()).isEqualTo(1);

    managedWorkload.transformName(appendRevision, k8sRequestHandlerContext);

    assertThat(managedWorkload.getField("metadata.name")).isEqualTo(newWorkloadName);
    if (enabledHPAAndPDBSupport) {
      hpaResourcesToBeUpdated.forEach(
          resource -> assertThat(resource.getField("spec.scaleTargetRef.name")).isEqualTo(newWorkloadName));
      hpaResourcesNotToBeUpdated.forEach(resource -> {
        assertThat(resource.getField("spec.scaleTargetRef.name")).isNotEqualTo(newWorkloadName);
        assertThat(String.valueOf(resource.getField("spec.scaleTargetRef.name"))).isIn(targetRefNames);
      });
    } else {
      hpaResourcesToBeUpdated.forEach(
          resource -> assertThat(resource.getField("spec.scaleTargetRef.name")).isEqualTo(oldWorkloadName));
      hpaResourcesNotToBeUpdated.forEach(resource -> {
        assertThat(resource.getField("spec.scaleTargetRef.name")).isNotEqualTo(oldWorkloadName);
        assertThat(String.valueOf(resource.getField("spec.scaleTargetRef.name"))).isIn(targetRefNames);
      });
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void addLabelsInResourceSelectorTest() throws Exception {
    addLabelsInResourceSelectorTest("deployment-with-multiple-pdb-resources.yaml", Kind.Deployment, true);
    addLabelsInResourceSelectorTest("deployment-with-multiple-pdb-resources.yaml", Kind.Deployment, false);
    addLabelsInResourceSelectorTest("statefulset-with-multiple-pdb-resources.yaml", Kind.StatefulSet, true);
    addLabelsInResourceSelectorTest("statefulset-with-multiple-pdb-resources.yaml", Kind.StatefulSet, false);
    addLabelsInResourceSelectorTest("deploymentconfig-with-multiple-pdb-resources.yaml", Kind.DeploymentConfig, true);
    addLabelsInResourceSelectorTest("deploymentconfig-with-multiple-pdb-resources.yaml", Kind.DeploymentConfig, false);
  }

  private void addLabelsInResourceSelectorTest(String resourceFile, Kind workloadKind, boolean enabledHPAAndPDBSupport)
      throws Exception {
    K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
    URL url = this.getClass().getResource("/" + resourceFile);
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    List<KubernetesResource> resources = processYaml(fileContents);
    k8sRequestHandlerContext.setResources(resources);
    k8sRequestHandlerContext.setEnabledSupportHPAAndPDB(enabledHPAAndPDBSupport);

    KubernetesResource managedWorkload =
        resources.stream()
            .filter(resource -> resource.getResourceId().getKind().equals(workloadKind.name()))
            .collect(Collectors.toList())
            .get(0);

    String existingSelectorName = "spec.selector.matchLabels.app";
    String newSelectorName = "spec.selector.matchLabels.key";
    String existingWorkloadSelectorName = existingSelectorName;
    String newWorkloadSelectorName = newSelectorName;
    if (workloadKind.name().equalsIgnoreCase(Kind.DeploymentConfig.name())) {
      existingWorkloadSelectorName = "spec.selector.app";
      newWorkloadSelectorName = "spec.selector.key";
    }
    String existingSelectorValue = String.valueOf(managedWorkload.getField(existingWorkloadSelectorName));
    String newSelectorValue = "val";
    Map<String, String> selectorsToBeAdded = ImmutableMap.of("key", newSelectorValue);

    List<KubernetesResource> pdbResourcesToBeUpdated =
        resources.stream()
            .filter(resource -> resource.getResourceId().getKind().equals(Kind.PodDisruptionBudget.name()))
            .filter(resource
                -> String.valueOf(resource.getField(existingSelectorName)).equalsIgnoreCase(existingSelectorValue))
            .collect(Collectors.toList());
    assertThat(pdbResourcesToBeUpdated.size()).isEqualTo(3);

    List<KubernetesResource> pdbResourcesNotToBeUpdated =
        resources.stream()
            .filter(resource -> resource.getResourceId().getKind().equals(Kind.PodDisruptionBudget.name()))
            .filter(resource
                -> !String.valueOf(resource.getField(existingSelectorName)).equalsIgnoreCase(existingSelectorValue))
            .collect(Collectors.toList());
    assertThat(pdbResourcesNotToBeUpdated.size()).isEqualTo(1);

    managedWorkload = managedWorkload.addLabelsInResourceSelector(selectorsToBeAdded, k8sRequestHandlerContext);

    assertThat(managedWorkload.getField(existingWorkloadSelectorName)).isEqualTo(existingSelectorValue);
    assertThat(managedWorkload.getField(newWorkloadSelectorName)).isEqualTo(newSelectorValue);

    if (enabledHPAAndPDBSupport) {
      pdbResourcesToBeUpdated.forEach(resource -> {
        assertThat(resource.getField(existingSelectorName)).isEqualTo(existingSelectorValue);
        assertThat(resource.getField(newSelectorName)).isEqualTo(newSelectorValue);
      });
      pdbResourcesNotToBeUpdated.forEach(resource -> {
        assertThat(resource.getField(existingSelectorName)).isNotEqualTo(existingSelectorValue);
        assertThat(resource.getField(newSelectorName)).isNull();
      });
    } else {
      pdbResourcesToBeUpdated.forEach(resource -> {
        assertThat(resource.getField(existingSelectorName)).isEqualTo(existingSelectorValue);
        assertThat(resource.getField(newSelectorName)).isNull();
      });
      pdbResourcesNotToBeUpdated.forEach(resource -> {
        assertThat(resource.getField(existingSelectorName)).isNotEqualTo(existingSelectorValue);
        assertThat(resource.getField(newSelectorName)).isNull();
      });
    }
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAddLabelsInPodSpecNullPodTemplateSpec() throws Exception {
    URL url = this.getClass().getResource("/null-pod-template.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    resource = resource.addLabelsInPodSpec(ImmutableMap.of("k", "v"));
    assertThat(resource).isNotNull();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testTransformConfigMapAndSecretRef() throws Exception {
    URL url = this.getClass().getResource("/spec-in-template-null.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    resource = resource.transformConfigMapAndSecretRef(UnaryOperator.identity(), UnaryOperator.identity());
    assertThat(resource).isNotNull();
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testGetSpecForStatefulSet() throws Exception {
    URL url1 = this.getClass().getResource("/denormalized-stateful-set-spec.yaml");
    String denormalizedSpec = IOUtils.toString(url1, Charsets.UTF_8);

    URL url2 = this.getClass().getResource("/normalized-stateful-set-spec.yaml");
    String normalizedSpec = IOUtils.toString(url2, Charsets.UTF_8);

    KubernetesResource denormalizedResource = processYaml(denormalizedSpec).get(0);
    assertEquals(normalizedSpec, denormalizedResource.getSpec());

    KubernetesResource normalizedResource = processYaml(normalizedSpec).get(0);
    assertEquals(normalizedSpec, normalizedResource.getSpec());
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testGetSpecForNonStatefulSet() throws Exception {
    URL url1 = this.getClass().getResource("/deploy.yaml");
    String spec = IOUtils.toString(url1, Charsets.UTF_8);

    KubernetesResource denormalizedResource = processYaml(spec).get(0);
    assertEquals(spec.trim(), denormalizedResource.getSpec().trim());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddLabelsInPodSpec() throws Exception {
    addLabelsInPodSpecUtil("job.yaml", true);
    addLabelsInPodSpecUtil("daemonset.yaml", true);
    addLabelsInPodSpecUtil("statefulset.yaml", true);
    addLabelsInPodSpecUtil("secret.yaml", false);
    addLabelsInPodSpecUtil("deployment-config_custom.yaml", true);
  }

  private void addLabelsInPodSpecUtil(String resourceFile, boolean verifyLabels) throws Exception {
    URL url = this.getClass().getResource("/" + resourceFile);
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    resource = resource.addLabelsInPodSpec(ImmutableMap.of("key", "val"));
    assertThat(resource).isNotNull();
    if (verifyLabels) {
      assertThat(resource.getField("spec.template.metadata.labels.key")).isEqualTo("val");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetV1PodSpec() throws Exception {
    testGetV1PodSpecUtil("job.yaml");
    testGetV1PodSpecUtil("daemonset.yaml");
    testGetV1PodSpecUtil("statefulset.yaml");

    URL url = this.getClass().getResource("/pod.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    resource = resource.transformConfigMapAndSecretRef(UnaryOperator.identity(), UnaryOperator.identity());
    assertThat(resource.getField("spec.containers")).isNotNull();

    url = this.getClass().getResource("/secret.yaml");
    fileContents = Resources.toString(url, Charsets.UTF_8);
    resource = processYaml(fileContents).get(0);
    resource = resource.transformConfigMapAndSecretRef(UnaryOperator.identity(), UnaryOperator.identity());
    assertThat(resource).isNotNull();
  }

  private void testGetV1PodSpecUtil(String resourceFile) throws Exception {
    URL url = this.getClass().getResource("/" + resourceFile);
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    resource = resource.transformConfigMapAndSecretRef(UnaryOperator.identity(), UnaryOperator.identity());
    assertThat(resource.getField("spec.template.spec.containers")).isNotNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testIsLoadBalancerService() throws Exception {
    URL url = this.getClass().getResource("/pod.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    assertThat(resource.isLoadBalancerService()).isFalse();

    url = this.getClass().getResource("/loadbalancer_service.yaml");
    fileContents = Resources.toString(url, Charsets.UTF_8);
    resource = processYaml(fileContents).get(0);
    assertThat(resource.isLoadBalancerService()).isTrue();

    url = this.getClass().getResource("/service.yaml");
    fileContents = Resources.toString(url, Charsets.UTF_8);
    resource = processYaml(fileContents).get(0);
    assertThat(resource.isLoadBalancerService()).isFalse();

    url = this.getClass().getResource("/service-with-extra-unknown-fields.yaml");
    fileContents = Resources.toString(url, Charsets.UTF_8);
    resource = processYaml(fileContents).get(0);
    assertThat(resource.isLoadBalancerService()).isFalse();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddColorSelectorInService() throws Exception {
    URL url = this.getClass().getResource("/loadbalancer_service.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    resource = resource.addColorSelector("blue", null);
    Map<String, String> selectors = (Map<String, String>) resource.getField("spec.selector");
    assertThat(selectors).containsKey("harness.io/color");
    assertThat(selectors.get("harness.io/color")).isEqualTo("blue");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetReplicaCount() throws Exception {
    URL url = this.getClass().getResource("/deploy.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    assertThat(resource.getReplicaCount()).isEqualTo(3);
    url = this.getClass().getResource("/deployment-config.yaml");
    fileContents = Resources.toString(url, Charsets.UTF_8);
    resource = processYaml(fileContents).get(0);
    assertThat(resource.getReplicaCount()).isEqualTo(5);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetReplicaCountForUnhandledResourceKind() throws Exception {
    URL url = this.getClass().getResource("/job.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    try {
      resource.getReplicaCount();
      fail("Should not reach here");
    } catch (InvalidRequestException e) {
      assertThat(ExceptionLogger.getResponseMessageList(e, LOG_SYSTEM))
          .extracting(ResponseMessage::getMessage)
          .containsExactly("Invalid request: Unhandled Kubernetes resource Job while getting replicaCount");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSetReplicaCount() throws Exception {
    URL url = this.getClass().getResource("/deploy.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    resource = resource.setReplicaCount(5);
    assertThat(resource.getReplicaCount()).isEqualTo(5);

    url = this.getClass().getResource("/deployment-config.yaml");
    fileContents = Resources.toString(url, Charsets.UTF_8);
    resource = processYaml(fileContents).get(0);
    resource = resource.setReplicaCount(50);
    assertThat(resource.getReplicaCount()).isEqualTo(50);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSetReplicaCountForUnhandledResourceKind() throws Exception {
    URL url = this.getClass().getResource("/job.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    try {
      resource.setReplicaCount(5);
      fail("Should not reach here");
    } catch (InvalidRequestException e) {
      assertThat(ExceptionLogger.getResponseMessageList(e, LOG_SYSTEM))
          .extracting(ResponseMessage::getMessage)
          .containsExactly("Invalid request: Unhandled Kubernetes resource Job while setting replicaCount");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddLabelsInDeploymentSelector() throws Exception {
    URL url = this.getClass().getResource("/deploy.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
    k8sRequestHandlerContext.setResources(asList(resource));
    resource = resource.addLabelsInResourceSelector(ImmutableMap.of("key", "val"), k8sRequestHandlerContext);
    assertThat(resource.getField("spec.selector.matchLabels.key")).isEqualTo("val");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testRedactSecretValues() throws Exception {
    URL url = this.getClass().getResource("/secret.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    resource = processYaml(resource.redactSecretValues(resource.getSpec())).get(0);
    assertThat(resource.getField("stringData.cred")).isEqualTo("***");
    assertThat(resource.getField("data.username")).isEqualTo("***");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdateConfigMapAndSecretRef() throws Exception {
    URL url = this.getClass().getResource("/deployment-envfrom.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    UnaryOperator<Object> configMapRevision = t -> t + "-1";
    UnaryOperator<Object> secretRevision = t -> t + "-2";
    resource = resource.transformConfigMapAndSecretRef(configMapRevision, secretRevision);
    assertThat(resource).isNotNull();
    assertThat(resource.getField("spec.template.spec.containers[0].envFrom.configMapRef[0].configMapRef.name"))
        .isEqualTo("example-1");
    assertThat(resource.getField("spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo("myconfig-1");
    assertThat(resource.getField("spec.template.spec.initContainers[0].envFrom.configMapRef[0].configMapRef.name"))
        .isEqualTo("example-1");
    assertThat(resource.getField("spec.template.spec.initContainers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo("myconfig-1");

    assertThat(resource.getField("spec.template.spec.volumes[0].configMap.name")).isEqualTo("volume-config-1");
    assertThat(resource.getField("spec.template.spec.volumes[1].projected.sources[0].configMap.name"))
        .isEqualTo("configmap-projection-1");

    assertThat(resource.getField("spec.template.spec.containers[0].envFrom[1].secretRef.name")).isEqualTo("example-2");
    assertThat(resource.getField("spec.template.spec.containers[0].env[1].valueFrom.secretKeyRef.name"))
        .isEqualTo("mysecret-2");
    assertThat(resource.getField("spec.template.spec.initContainers[0].envFrom[1].secretRef.name"))
        .isEqualTo("example-2");
    assertThat(resource.getField("spec.template.spec.initContainers[0].env[1].valueFrom.secretKeyRef.name"))
        .isEqualTo("mysecret-2");
    assertThat(resource.getField("spec.template.spec.volumes[0].secret.secretName")).isEqualTo("volume-secret-2");
    assertThat(resource.getField("spec.template.spec.volumes[1].projected.sources[1].secret.name"))
        .isEqualTo("secret-projection-2");
    assertThat(resource.getField("spec.template.spec.imagePullSecrets[0].name")).isEqualTo("image_pull_secret-2");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testUpdateConfigMapAndSecretRefEmptyInitContainers() throws Exception {
    URL url = this.getClass().getResource("/deployment-emptyInitContainer.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    UnaryOperator<Object> configMapRevision = t -> t + "-1";
    UnaryOperator<Object> secretRevision = t -> t + "-2";
    assertThatThrownBy(() -> resource.transformConfigMapAndSecretRef(configMapRevision, secretRevision))
        .hasMessage("The container or initContainer list contains empty elements. Please remove the empty elements");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeploymentConfigCustomStrategy() throws Exception {
    URL url = this.getClass().getResource("/deployment-config_custom.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    UnaryOperator<Object> configMapRevision = t -> t + "-1";
    UnaryOperator<Object> secretRevision = t -> t + "-2";
    resource = resource.transformConfigMapAndSecretRef(configMapRevision, secretRevision);
    assertThat(resource).isNotNull();
    assertThat(resource.getField("spec.template.spec.containers[0].env[1].valueFrom.secretKeyRef.name"))
        .isEqualTo("mysecret-2");
    assertThat(resource.getField("spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo("myconfig-1");
    assertThat(resource.getField("spec.strategy.customParams.environment[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo("customParamsName-1");
    assertThat(resource.getField("spec.strategy.customParams.environment[1].valueFrom.secretKeyRef.name"))
        .isEqualTo("customParamsSecretName-2");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddLabelsInDeploymentWithNullMatchLabels() throws Exception {
    URL url = this.getClass().getResource("/deployment-null-match-labels.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
    k8sRequestHandlerContext.setResources(asList(resource));
    resource = resource.addLabelsInResourceSelector(ImmutableMap.of("key", "val"), k8sRequestHandlerContext);
    assertThat(resource.getField("spec.selector.matchLabels.key")).isEqualTo("val");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeploymentConfigRollingStrategy() throws Exception {
    URL url = this.getClass().getResource("/deployment-config-rolling.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    UnaryOperator<Object> configMapRevision = t -> t + "-1";
    UnaryOperator<Object> secretRevision = t -> t + "-2";
    resource = resource.transformConfigMapAndSecretRef(configMapRevision, secretRevision);
    assertThat(resource).isNotNull();
    assertThat(resource.getField("spec.strategy.rollingParams.pre.execNewPod.env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo("pre-varName-1");
    assertThat(resource.getField("spec.strategy.rollingParams.pre.execNewPod.env[1].valueFrom.secretKeyRef.name"))
        .isEqualTo("pre-secretName-2");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddLabelsInDeploymentWithNullSelector() throws Exception {
    URL url = this.getClass().getResource("/deployment-null-selector.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
    k8sRequestHandlerContext.setResources(asList(resource));
    try {
      resource.addLabelsInResourceSelector(ImmutableMap.of("key", "val"), k8sRequestHandlerContext);
    } catch (KubernetesYamlException e) {
      assertThat(ExceptionLogger.getResponseMessageList(e, LOG_SYSTEM))
          .extracting(ResponseMessage::getMessage)
          .containsExactly("Invalid Kubernetes YAML Spec. Deployment spec does not have selector.");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeploymentConfigRecreateStrategy() throws Exception {
    URL url = this.getClass().getResource("/deployment-config-recreate.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    UnaryOperator<Object> configMapRevision = t -> t + "-1";
    UnaryOperator<Object> secretRevision = t -> t + "-2";
    resource = resource.transformConfigMapAndSecretRef(configMapRevision, secretRevision);
    assertThat(resource).isNotNull();
    assertThat(resource.getField("spec.strategy.recreateParams.mid.execNewPod.env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo("mid-varName-1");
    assertThat(resource.getField("spec.strategy.recreateParams.mid.execNewPod.env[1].valueFrom.secretKeyRef.name"))
        .isEqualTo("mid-secretName-2");
    assertThat(resource.getField("spec.template.spec.containers[0].env[1].valueFrom.secretKeyRef.name"))
        .isEqualTo("mysecret-2");
    assertThat(resource.getField("spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo("myconfig-1");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddLabelsInDeploymentConfigWithNullSelector() throws Exception {
    URL url = this.getClass().getResource("/deployment-config-null-selector.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
    k8sRequestHandlerContext.setResources(asList(resource));
    try {
      resource.addLabelsInResourceSelector(ImmutableMap.of("key", "val"), k8sRequestHandlerContext);
    } catch (KubernetesYamlException e) {
      assertThat(ExceptionLogger.getResponseMessageList(e, LOG_SYSTEM))
          .extracting(ResponseMessage::getMessage)
          .containsExactly("Invalid Kubernetes YAML Spec. DeploymentConfig spec does not have selector.");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeploymentConfig() throws Exception {
    URL url = this.getClass().getResource("/deployment-config.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    UnaryOperator<Object> configMapRevision = t -> t + "-1";
    UnaryOperator<Object> secretRevision = t -> t + "-2";
    resource = resource.transformConfigMapAndSecretRef(configMapRevision, secretRevision);
    assertThat(resource).isNotNull();
    assertThat(resource.getField("spec.template.spec.containers[0].env[1].valueFrom.secretKeyRef.name"))
        .isEqualTo("mysecret-2");
    assertThat(resource.getField("spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name"))
        .isEqualTo("myconfig-1");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddLabelsInDeploymentConfigSelector() throws Exception {
    URL url = this.getClass().getResource("/deployment-config.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
    k8sRequestHandlerContext.setResources(asList(resource));
    resource = resource.addLabelsInResourceSelector(ImmutableMap.of("key", "val"), k8sRequestHandlerContext);
    assertThat(resource.getField("spec.selector.key")).isEqualTo("val");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddLabelsInJobSelector() throws Exception {
    URL url = this.getClass().getResource("/job.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
    k8sRequestHandlerContext.setResources(asList(resource));

    try {
      resource.addLabelsInResourceSelector(ImmutableMap.of("key", "val"), k8sRequestHandlerContext);
      fail("Should not reach here");
    } catch (InvalidRequestException e) {
      assertThat(ExceptionLogger.getResponseMessageList(e, LOG_SYSTEM))
          .extracting(ResponseMessage::getMessage)
          .containsExactly("Invalid request: Unhandled Kubernetes resource Job while adding labels to selector");
    }
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testNullCreationTimestamp() throws Exception {
    URL url = this.getClass().getResource("/deployment-null-creationtimestamp.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    assertThat(resource.getK8sResource()).isInstanceOfSatisfying(V1Deployment.class, dep -> {
      assertThat(dep.getMetadata().getCreationTimestamp()).isNull();
      assertThat(dep.getSpec().getTemplate().getMetadata().getCreationTimestamp()).isNull();
    });
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testTriggersInDeploymentConfig() throws Exception {
    UnaryOperator<Object> appendRevision = t -> t + "-1";

    URL url = this.getClass().getResource("/deployment-config-null-trigger.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    String oldName = (String) resource.getField("metadata.name");
    resource.transformName(appendRevision, null);
    assertThat(resource.getField("metadata.name")).isEqualTo(oldName + "-1");
    assertThat(resource.getField("spec.triggers")).isNull();

    url = this.getClass().getResource("/deployment-config.yaml");
    fileContents = Resources.toString(url, Charsets.UTF_8);
    resource = processYaml(fileContents).get(0);
    oldName = (String) resource.getField("metadata.name");
    resource.transformName(appendRevision, null);
    assertThat(resource.getField("metadata.name")).isEqualTo(oldName + "-1");
    assertThat(resource.getField("spec.triggers[0].type")).isEqualTo("ConfigChange");

    url = this.getClass().getResource("/deployment-config-empty-triggers.yaml");
    fileContents = Resources.toString(url, Charsets.UTF_8);
    resource = processYaml(fileContents).get(0);
    oldName = (String) resource.getField("metadata.name");
    resource.transformName(appendRevision, null);
    assertThat(resource.getField("metadata.name")).isEqualTo(oldName + "-1");
    assertThat((List) resource.getField("spec.triggers")).isEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateConfigMapAndSecretRefForCronJob() throws IOException {
    URL url = this.getClass().getResource("/cronjob-envfrom.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    UnaryOperator<Object> configMapRevision = t -> t + "-1";
    UnaryOperator<Object> secretRevision = t -> t + "-2";
    resource = resource.transformConfigMapAndSecretRef(configMapRevision, secretRevision);
    final String podTemplateSpec = "spec.jobTemplate.spec.template.spec.%s";
    assertThat(resource.getField(format(podTemplateSpec, "containers[0].envFrom[0].configMapRef.name")))
        .isEqualTo("envfrom-configmap-1");
    assertThat(resource.getField(format(podTemplateSpec, "containers[0].env[0].valueFrom.configMapKeyRef.name")))
        .isEqualTo("config-key-value-1");
    assertThat(resource.getField(format(podTemplateSpec, "volumes[0].configMap.name"))).isEqualTo("volume-configmap-1");

    assertThat(resource.getField(format(podTemplateSpec, "containers[0].envFrom[1].secretRef.name")))
        .isEqualTo("envfrom-secret-2");
    assertThat(resource.getField(format(podTemplateSpec, "containers[0].env[1].valueFrom.secretKeyRef.name")))
        .isEqualTo("secret-key-value-2");
    assertThat(resource.getField(format(podTemplateSpec, "volumes[0].secret.secretName"))).isEqualTo("volume-secret-2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testYamlDumpQuotingBooleanRegex() throws Exception {
    URL url = this.getClass().getResource("/deployment-with-boolean.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);
    Object k8sResource = resource.getK8sResource();

    URL resultUrl = this.getClass().getResource("/deployment-after-dump.yaml");
    String resultContents = Resources.toString(resultUrl, Charsets.UTF_8);

    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
        new Yaml.CustomConstructor(Object.class, new LoaderOptions()), new BooleanPatchedRepresenter());

    assertThat(yaml.dump(k8sResource)).isEqualTo(resultContents);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSkipPruningAnnotation() throws Exception {
    URL url = this.getClass().getResource("/podWithSkipPruneAnnotation.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resourceWithAnnotation = processYaml(fileContents).get(0);
    assertThat(resourceWithAnnotation.isSkipPruning()).isTrue();

    url = this.getClass().getResource("/pod.yaml");
    fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resourceWithoutAnnotation = processYaml(fileContents).get(0);
    assertThat(resourceWithoutAnnotation.isSkipPruning()).isFalse();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testYamlConstructorMessageExtraction() throws IOException {
    URL url = this.getClass().getResource("/secret-invalid-value.yaml");
    String fileContents = Resources.toString(url, StandardCharsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    assertThatThrownBy(resource::getK8sResource).matches(throwable -> {
      KubernetesYamlException exception = (KubernetesYamlException) throwable;
      String errorMessage = exception.getParams().get("reason").toString();
      assertThat(errorMessage).contains("Failed to load spec for resource kind: Secret, name: test-secret");
      return true;
    });
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testYamlConstructorMessageExtractionWithDeploymentErrors() throws IOException {
    URL url = this.getClass().getResource("/deployment-invalid-value.yaml");
    String fileContents = Resources.toString(url, StandardCharsets.UTF_8);
    String expectedError = "Failed to load spec for resource kind: Deployment, name: myapp-deployment-2 \n"
        + "Cannot create spec for V1Deployment  \n"
        + "  line 10, column 3:\n"
        + "      template:\n"
        + "      ^\n"
        + "  line 11, column 5:\n"
        + "        metadata:\n"
        + "        ^\n"
        + "  line 17, column 7:\n"
        + "          containers:\n"
        + "          ^\n"
        + "  line 18, column 11:\n"
        + "            - name: nginx-container\n"
        + "              ^\n"
        + "Unable to find property 'dummy' on class: io.kubernetes.client.openapi.models.V1Container\n"
        + "  line 19, column 18:\n"
        + "              dummy: nginx\n"
        + "                     ^\n"
        + "\n"
        + "  line 18, column 9:\n"
        + "            - name: nginx-container\n"
        + "            ^\n"
        + "\n"
        + "  line 17, column 7:\n"
        + "          containers:\n"
        + "          ^\n"
        + "\n"
        + "  line 11, column 5:\n"
        + "        metadata:\n"
        + "        ^\n";
    KubernetesResource resource = processYaml(fileContents).get(0);

    assertThatThrownBy(resource::getK8sResource).matches(throwable -> {
      KubernetesYamlException exception = (KubernetesYamlException) throwable;
      String errorMessage = exception.getParams().get("reason").toString();
      assertEquals(expectedError, errorMessage);
      return true;
    });
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testYamlConstructorMessageExtractionWithServiceErrors() throws IOException {
    URL url = this.getClass().getResource("/service-invalid-value.yaml");
    String fileContents = Resources.toString(url, StandardCharsets.UTF_8);
    String expectedError = "Failed to load spec for resource kind: Service, name: my-service \n"
        + "Cannot create spec for V1Service  \n"
        + "  line 6, column 3:\n"
        + "      selector:\n"
        + "      ^\n"
        + "  line 9, column 5:\n"
        + "      - protocol: TCP\n"
        + "        ^\n"
        + "Unable to find property 'dummyPort' on class: io.kubernetes.client.openapi.models.V1ServicePort\n"
        + "  line 10, column 16:\n"
        + "        dummyPort: 80\n"
        + "                   ^\n"
        + "\n"
        + "  line 9, column 3:\n"
        + "      - protocol: TCP\n"
        + "      ^\n";

    KubernetesResource resource = processYaml(fileContents).get(0);

    assertThatThrownBy(resource::getK8sResource).matches(throwable -> {
      KubernetesYamlException exception = (KubernetesYamlException) throwable;
      String errorMessage = exception.getParams().get("reason").toString();
      assertEquals(expectedError, errorMessage);
      return true;
    });
  }
}
