/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.delegate.task.gcp.helpers.GcpHelperService.LOCATION_DELIMITER;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import io.harness.CategoryTest;
import io.harness.k8s.KubernetesContainerServiceImpl;
import io.harness.k8s.model.KubernetesConfig;

import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.gke.GkeClusterServiceImpl;
import software.wings.rules.Integration;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Integration
@Slf4j
public abstract class KubernetesIntegrationTestBase extends CategoryTest {
  private static final SettingAttribute COMPUTE_PROVIDER_SETTING =
      aSettingAttribute()
          .withUuid("GCP_ID")
          .withValue(GcpConfig.builder().serviceAccountKeyFileContent("GCP_CONFIG_CONTENT".toCharArray()).build())
          .build();

  private static final String ZONE_CLUSTER = "us-central1-a" + LOCATION_DELIMITER + "brett-test";
  private static final String NAMESPACE = "default";

  public static void main(String[] args) throws InterruptedException {
    GkeClusterServiceImpl gkeClusterService = new GkeClusterServiceImpl();
    KubernetesContainerServiceImpl kubernetesService = new KubernetesContainerServiceImpl();

    List<String> clusters = gkeClusterService.listClusters(COMPUTE_PROVIDER_SETTING.toDTO(), Collections.emptyList());
    log.info("Available clusters: {}", clusters);

    //    KubernetesConfig config = gkeClusterService.createCluster(COMPUTE_PROVIDER_SETTING, ZONE_CLUSTER,
    //        ImmutableMap.<String, String>builder()
    //            .put("nodeCount", "1")
    //            .put("machineType", "n1-highcpu-4")
    //            .put("masterUser", "master")
    //            .put("masterPwd", "foo!!bar$$")
    //            .build());

    KubernetesConfig config = gkeClusterService.getCluster(
        COMPUTE_PROVIDER_SETTING.toDTO(), Collections.emptyList(), ZONE_CLUSTER, NAMESPACE, false);

    kubernetesService.createOrReplaceController(config,
        new ReplicationControllerBuilder()
            .withApiVersion("v1")
            .withNewMetadata()
            .withName("backend-ctrl")
            .addToLabels("app", "testApp")
            .addToLabels("tier", "backend")
            .endMetadata()
            .withNewSpec()
            .withReplicas(0)
            .withNewTemplate()
            .withNewMetadata()
            .addToLabels("app", "testApp")
            .addToLabels("tier", "backend")
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("server")
            .withImage("gcr.io/exploration-161417/todolist")
            .withArgs("8080")
            .withNewResources()
            .withRequests(ImmutableMap.of("cpu", new Quantity("10m"), "memory", new Quantity("10Mi")))
            .withLimits(ImmutableMap.of("cpu", new Quantity("100m"), "memory", new Quantity("100Mi")))
            .endResources()
            .addNewPort()
            .withContainerPort(8080)
            .endPort()
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build());

    kubernetesService.createOrReplaceServiceFabric8(config,
        new ServiceBuilder()
            .withApiVersion("v1")
            .withNewMetadata()
            .withName("backend-service")
            .addToLabels("app", "testApp")
            .addToLabels("tier", "backend")
            .endMetadata()
            .withNewSpec()
            .addNewPort()
            .withPort(80)
            .withNewTargetPort()
            .withValue(8080)
            .endTargetPort()
            .endPort()
            .addToSelector("app", "testApp")
            .addToSelector("tier", "backend")
            .endSpec()
            .build());

    kubernetesService.createOrReplaceController(config,
        new ReplicationControllerBuilder()
            .withApiVersion("v1")
            .withNewMetadata()
            .withName("frontend-ctrl")
            .addToLabels("app", "testApp")
            .addToLabels("tier", "frontend")
            .endMetadata()
            .withNewSpec()
            .withReplicas(0)
            .withNewTemplate()
            .withNewMetadata()
            .addToLabels("app", "testApp")
            .addToLabels("tier", "frontend")
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("webapp")
            .withImage("gcr.io/exploration-161417/todolist:latest")
            .addNewEnv()
            .withName("GET_HOSTS_FROM")
            .withValue("dns")
            .endEnv()
            .withNewResources()
            .withLimits(ImmutableMap.of("cpu", new Quantity("100m"), "memory", new Quantity("100Mi")))
            .endResources()
            .addNewPort()
            .withContainerPort(8080)
            .endPort()
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build());

    kubernetesService.createOrReplaceServiceFabric8(config,
        new ServiceBuilder()
            .withApiVersion("v1")
            .withNewMetadata()
            .withName("frontend-service")
            .addToLabels("app", "testApp")
            .addToLabels("tier", "frontend")
            .endMetadata()
            .withNewSpec()
            .addNewPort()
            .withPort(80)
            .withNewTargetPort()
            .withValue(8080)
            .endTargetPort()
            .endPort()
            .withType("LoadBalancer")
            .addToSelector("app", "testApp")
            .addToSelector("tier", "backend")
            .endSpec()
            .build());

    kubernetesService.setControllerPodCount(
        config, ZONE_CLUSTER, "frontend-ctrl", 0, 2, 10, new ExecutionLogCallback());

    Optional<Integer> backendCount = kubernetesService.getControllerPodCount(config, "backend-ctrl");
    Optional<Integer> frontendCount = kubernetesService.getControllerPodCount(config, "frontend-ctrl");
    log.info("Controller backend-ctrl has {} instances", backendCount.get());
    log.info("Controller frontend-ctrl has {} instances", frontendCount.get());

    kubernetesService.checkStatus(config, "backend-ctrl", "backend-service");
    kubernetesService.checkStatus(config, "frontend-ctrl", "frontend-service");

    kubernetesService.deleteService(config, "frontend-service");
    kubernetesService.deleteService(config, "backend-service");

    kubernetesService.deleteController(config, "frontend-ctrl");
    kubernetesService.deleteController(config, "backend-ctrl");

    kubernetesService.checkStatus(config, "backend-ctrl", "backend-service");
    kubernetesService.checkStatus(config, "frontend-ctrl", "frontend-service");
  }
}
