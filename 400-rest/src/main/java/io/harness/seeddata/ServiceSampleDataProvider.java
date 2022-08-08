/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.seeddata;

import static io.harness.seeddata.SampleDataProviderConstants.K8S_SERVICE_DESC;
import static io.harness.seeddata.SampleDataProviderConstants.K8S_SERVICE_NAME;

import static java.util.Arrays.asList;

import software.wings.api.DeploymentType;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.container.ContainerDefinition;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.beans.container.KubernetesPayload;
import software.wings.beans.container.LogConfiguration;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ServiceSampleDataProvider {
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ApplicationManifestService applicationManifestService;

  public Service createKubeService(String appId) {
    Service service = serviceResourceService.save(Service.builder()
                                                      .appId(appId)
                                                      .artifactType(ArtifactType.DOCKER)
                                                      .name(K8S_SERVICE_NAME)
                                                      .description(K8S_SERVICE_DESC)
                                                      .sample(true)
                                                      .build());

    // Create container task
    KubernetesContainerTask containerTask = new KubernetesContainerTask();
    containerTask.setAppId(appId);
    containerTask.setServiceId(service.getUuid());
    containerTask.setContainerDefinitions(
        asList(ContainerDefinition.builder()
                   .name("DEFAULT_NAME")
                   .commands(asList("")) // UI mandates it .. do not remove it
                   .storageConfigurations(asList()) // UI looking for empty list
                   .logConfiguration(LogConfiguration.builder().logDriver("").build()) // UI looking for empty list
                   .build()));

    final ContainerTask savedContainerTask = serviceResourceService.createContainerTask(containerTask, true);

    // Update the YAML Spec for BusyBox
    String advancedConfig = "# Enter your Controller YAML spec below.\n"
        + "#\n"
        + "# Supported Controllers:\n"
        + "#   ReplicationController\n"
        + "#   Deployment\n"
        + "#   ReplicaSet\n"
        + "#   StatefulSet\n"
        + "#   DaemonSet\n"
        + "#\n"
        + "# Placeholders:\n"
        + "#\n"
        + "# Required: ${DOCKER_IMAGE_NAME}\n"
        + "#   - Replaced with the Docker image name and tag\n"
        + "#\n"
        + "# Optional: ${CONTAINER_NAME}\n"
        + "#   - Replaced with a container name based on the image name\n"
        + "#\n"
        + "# Optional: ${CONFIG_MAP_NAME}\n"
        + "#   - Replaced with the ConfigMap name (same as controller name)\n"
        + "#     Config map contains all unencrypted service variables and\n"
        + "#     all unencrypted config files, unless a custom\n"
        + "#     config map is provided\n"
        + "#\n"
        + "# Optional: ${SECRET_MAP_NAME}\n"
        + "#   - Replaced with the Secret name (same as controller name)\n"
        + "#     Secret map contains all encrypted service variables and\n"
        + "#     all encrypted config files\n"
        + "#\n"
        + "# Optional: ${SECRET_NAME}\n"
        + "#   - Replaced with the name of the generated image pull\n"
        + "#     secret when pulling from a private Docker registry\n"
        + "#\n"
        + "# Harness will set the controller name, namespace,\n"
        + "# selector labels, and number of replicas.\n"
        + "#\n"
        + "# Service variables will be merged into environment\n"
        + "# variables for all containers, overriding values if\n"
        + "# the name is the same.\n"
        + "#\n"
        + "# ---\n"
        + "apiVersion: \"extensions/v1beta1\"\n"
        + "kind: \"Deployment\"\n"
        + "metadata:\n"
        + "  annotations: {}\n"
        + "  finalizers: []\n"
        + "  labels: {}\n"
        + "  ownerReferences: []\n"
        + "spec:\n"
        + "  replicas: 0\n"
        + "  template:\n"
        + "    metadata:\n"
        + "      annotations: {}\n"
        + "      finalizers: []\n"
        + "      labels: {}\n"
        + "      ownerReferences: []\n"
        + "    spec:\n"
        + "      containers:\n"
        + "      - args: []\n"
        + "        command: []\n"
        + "        env: []\n"
        + "        envFrom: []\n"
        + "        image: \"${DOCKER_IMAGE_NAME}\"\n"
        + "        name: \"${CONTAINER_NAME}\"\n"
        + "        ports: []\n"
        + "        resources:\n"
        + "          limits:\n"
        + "            memory: \"750Mi\"\n"
        + "            cpu: \"1\"\n"
        + "          requests:\n"
        + "            memory: \"100Mi\"\n"
        + "            cpu: \"50m\"\n"
        + "        volumeMounts: []\n"
        + "      hostAliases: []\n"
        + "      initContainers: []\n"
        + "      nodeSelector: {}\n"
        + "      tolerations: []\n"
        + "      volumes: []";

    KubernetesPayload kubernetesPayload = KubernetesPayload.builder().advancedConfig(advancedConfig).build();
    serviceResourceService.updateContainerTaskAdvanced(
        appId, savedContainerTask.getServiceId(), savedContainerTask.getUuid(), kubernetesPayload, false);

    return service;
  }

  public Service createK8sV2Service(String appId) {
    Service service = serviceResourceService.save(Service.builder()
                                                      .appId(appId)
                                                      .artifactType(ArtifactType.DOCKER)
                                                      .deploymentType(DeploymentType.KUBERNETES)
                                                      .isK8sV2(true)
                                                      .name(K8S_SERVICE_NAME)
                                                      .description(K8S_SERVICE_DESC)
                                                      .sample(true)
                                                      .build());

    ApplicationManifest appManifest = applicationManifestService.getManifestByServiceId(appId, service.getUuid());

    ManifestFile valuesFile =
        applicationManifestService.getManifestFileByFileName(appManifest.getUuid(), "values.yaml");
    valuesFile.setFileContent("name: harness-example-${env.name}\n"
        + "replicas: 1\n"
        + "\n"
        + "image: ${artifact.metadata.image}\n"
        + "dockercfg: ${artifact.source.dockerconfig}\n"
        + "\n"
        + "createImagePullSecret: false\n"
        + "\n"
        + "createNamespace: true\n"
        + "namespace: ${infra.kubernetes.namespace}\n"
        + "\n"
        + "# Service Type allow you to specify what kind of service you want.\n"
        + "# Possible values for ServiceVariableType are:\n"
        + "# ClusterIP | NodePort | LoadBalancer | ExternalName\n"
        + "serviceType: LoadBalancer\n"
        + "\n"
        + "# A Service can map an incoming port to any targetPort.\n"
        + "# targetPort is where application is listening on inside the container.\n"
        + "servicePort: 80\n"
        + "serviceTargetPort: 8080\n"
        + "\n"
        + "# Specify all environment variables to be added to the container.\n"
        + "# The following two maps, config and secrets, are put into a ConfigMap\n"
        + "# and a Secret, respectively.\n"
        + "# Both are added to the container environment in podSpec as envFrom source.\n"
        + "env:\n"
        + "  config:\n"
        + "    key1: value1\n"
        + "  secrets:\n"
        + "    key2: value2");

    applicationManifestService.updateManifestFileByServiceId(valuesFile, service.getUuid());

    return service;
  }
}
