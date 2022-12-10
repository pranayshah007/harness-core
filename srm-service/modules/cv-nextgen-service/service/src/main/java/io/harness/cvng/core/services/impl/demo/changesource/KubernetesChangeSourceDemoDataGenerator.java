/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.demo.changesource;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata.Action;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata.KubernetesResourceType;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;
import io.harness.cvng.core.services.api.demo.ChangeSourceDemoDataGenerator;
import io.harness.cvng.core.utils.DateTimeUtils;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class KubernetesChangeSourceDemoDataGenerator implements ChangeSourceDemoDataGenerator<KubernetesChangeSource> {
  @Inject private Clock clock;
  @Override
  public List<ChangeEventDTO> generate(KubernetesChangeSource changeSource) {
    return generate(changeSource, "default", changeSource.getMonitoredServiceIdentifier());
  }

  public List<ChangeEventDTO> generate(KubernetesChangeSource changeSource, String namespace, String workload) {
    Instant time = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());
    return Arrays.asList(
        ChangeEventDTO.builder()
            .accountId(changeSource.getAccountId())
            .changeSourceIdentifier(changeSource.getIdentifier())
            .projectIdentifier(changeSource.getProjectIdentifier())
            .orgIdentifier(changeSource.getOrgIdentifier())
            .monitoredServiceIdentifier(changeSource.getMonitoredServiceIdentifier())
            .eventTime(time.toEpochMilli())
            .type(ChangeSourceType.KUBERNETES)
            .metadata(
                KubernetesChangeEventMetadata.builder()
                    .action(Action.Update)
                    .kind(null)
                    .oldYaml("apiVersion: apps/v1\n"
                        + "kind: ReplicaSet\n"
                        + "metadata:\n"
                        + "  annotations:\n"
                        + "    deployment.kubernetes.io/desired-replicas: '1'\n"
                        + "    deployment.kubernetes.io/max-replicas: '2'\n"
                        + "    deployment.kubernetes.io/revision: '1'\n"
                        + "    kubernetes.io/change-cause: kubectl apply --kubeconfig=config --filename=manifests.yaml\n"
                        + "      --record=true\n"
                        + "  labels:\n"
                        + "    app: dummypipeline-appd\n"
                        + "    harness.io/release-name: release-b0d61bddeaaab7f398036bfd4aa4fa9c5a0d5d02\n"
                        + "    harness.io/track: stable\n"
                        + "    pod-template-hash: 59d57d4bb8\n"
                        + "  name: dummypipeline-appd-deployment-59d57d4bb8\n"
                        + "  namespace: default\n"
                        + "  uid: 2a06a634-731e-4084-a951-3a15858b87f5\n"
                        + "spec:\n"
                        + "  replicas: 1\n"
                        + "  selector:\n"
                        + "    matchLabels:\n"
                        + "      app: dummypipeline-appd\n"
                        + "      harness.io/track: stable\n"
                        + "      pod-template-hash: 59d57d4bb8\n"
                        + "  template:\n"
                        + "    metadata:\n"
                        + "      labels:\n"
                        + "        app: dummypipeline-appd\n"
                        + "        harness.io/release-name: release-b0d61bddeaaab7f398036bfd4aa4fa9c5a0d5d02\n"
                        + "        harness.io/track: stable\n"
                        + "        pod-template-hash: 59d57d4bb8\n"
                        + "    spec:\n"
                        + "      containers:\n"
                        + "      - envFrom:\n"
                        + "        - configMapRef:\n"
                        + "            name: dummypipeline-appd-config-1\n"
                        + "        - secretRef:\n"
                        + "            name: dummypipeline-appd-secret-1\n"
                        + "        image: index.docker.io/harness/todolist:praveen-cv-test\n"
                        + "        imagePullPolicy: IfNotPresent\n"
                        + "        name: dummypipeline-appd\n"
                        + "        resources: {}\n"
                        + "        terminationMessagePath: /dev/termination-log\n"
                        + "        terminationMessagePolicy: File\n"
                        + "      dnsPolicy: ClusterFirst\n"
                        + "      imagePullSecrets:\n"
                        + "      - name: dummypipeline-appd-dockercfg\n"
                        + "      restartPolicy: Always\n"
                        + "      schedulerName: default-scheduler\n"
                        + "      securityContext: {}\n"
                        + "      terminationGracePeriodSeconds: 30\n"
                        + "status:\n"
                        + "  replicas: 3\n")
                    .newYaml("apiVersion: apps/v1\n"
                        + "kind: ReplicaSet\n"
                        + "metadata:\n"
                        + "  annotations:\n"
                        + "    deployment.kubernetes.io/desired-replicas: '1'\n"
                        + "    deployment.kubernetes.io/max-replicas: '2'\n"
                        + "    deployment.kubernetes.io/revision: '1'\n"
                        + "    kubernetes.io/change-cause: kubectl apply --kubeconfig=config --filename=manifests.yaml\n"
                        + "      --record=true\n"
                        + "  labels:\n"
                        + "    app: dummypipeline-appd\n"
                        + "    harness.io/release-name: release-b0d61bddeaaab7f398036bfd4aa4fa9c5a0d5d02\n"
                        + "    harness.io/track: stable\n"
                        + "    pod-template-hash: 59d57d4bb8\n"
                        + "  name: dummypipeline-appd-deployment-59d57d4bb8\n"
                        + "  namespace: default\n"
                        + "  uid: 2a06a634-731e-4084-a951-3a15858b87f5\n"
                        + "spec:\n"
                        + "  replicas: 1\n"
                        + "  selector:\n"
                        + "    matchLabels:\n"
                        + "      app: dummypipeline-appd\n"
                        + "      harness.io/track: stable\n"
                        + "      pod-template-hash: 59d57d4bb8\n"
                        + "  template:\n"
                        + "    metadata:\n"
                        + "      labels:\n"
                        + "        app: dummypipeline-appd\n"
                        + "        harness.io/release-name: release-b0d61bddeaaab7f398036bfd4aa4fa9c5a0d5d02\n"
                        + "        harness.io/track: stable\n"
                        + "        pod-template-hash: 59d57d4bb8\n"
                        + "    spec:\n"
                        + "      containers:\n"
                        + "      - envFrom:\n"
                        + "        - configMapRef:\n"
                        + "            name: dummypipeline-appd-config-1\n"
                        + "        - secretRef:\n"
                        + "            name: dummypipeline-appd-secret-1\n"
                        + "        image: index.docker.io/harness/todolist:praveen-cv-test\n"
                        + "        imagePullPolicy: IfNotPresent\n"
                        + "        name: dummypipeline-appd\n"
                        + "        resources: {}\n"
                        + "        terminationMessagePath: /dev/termination-log\n"
                        + "        terminationMessagePolicy: File\n"
                        + "      dnsPolicy: ClusterFirst\n"
                        + "      imagePullSecrets:\n"
                        + "      - name: dummypipeline-appd-dockercfg\n"
                        + "      restartPolicy: Always\n"
                        + "      schedulerName: default-scheduler\n"
                        + "      securityContext: {}\n"
                        + "      terminationGracePeriodSeconds: 30\n"
                        + "status:\n"
                        + "  observedGeneration: 1\n"
                        + "  replicas: 1\n")
                    .namespace(namespace)
                    .workload(workload)
                    .resourceType(KubernetesResourceType.ReplicaSet)
                    .timestamp(time)
                    .build())
            .build());
  }
}
