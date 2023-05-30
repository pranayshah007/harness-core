/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class HelmClientUtilsTest extends CategoryTest {
  private static final String GET_MANIFEST_CLEAN_OUTPUT = "---\n"
      + "# Source: hello/templates/service.yaml\n"
      + "apiVersion: v1\n"
      + "kind: Service\n"
      + "metadata:\n"
      + "  name: my-hello\n"
      + "  labels:\n"
      + "    helm.sh/chart: hello-0.1.1\n"
      + "    app.kubernetes.io/name: hello\n"
      + "    app.kubernetes.io/instance: my-hello\n"
      + "    app.kubernetes.io/version: \"0.1.0\"\n"
      + "    app.kubernetes.io/managed-by: Helm\n"
      + "spec:\n"
      + "  type: ClusterIP\n"
      + "  ports:\n"
      + "    - port: 8080\n"
      + "      targetPort: http\n"
      + "      protocol: TCP\n"
      + "      name: http\n"
      + "  selector:\n"
      + "    app.kubernetes.io/name: hello\n"
      + "    app.kubernetes.io/instance: my-hello\n"
      + "---\n"
      + "# Source: hello/templates/deployment.yaml\n"
      + "apiVersion: apps/v1\n"
      + "kind: Deployment\n"
      + "metadata:\n"
      + "  name: my-hello\n"
      + "  labels:\n"
      + "    helm.sh/chart: hello-0.1.1\n"
      + "    app.kubernetes.io/name: hello\n"
      + "    app.kubernetes.io/instance: my-hello\n"
      + "    app.kubernetes.io/version: \"0.1.0\"\n"
      + "    app.kubernetes.io/managed-by: Helm\n"
      + "spec:\n"
      + "  replicas: 1\n"
      + "  selector:\n"
      + "    matchLabels:\n"
      + "      app.kubernetes.io/name: hello\n"
      + "      app.kubernetes.io/instance: my-hello\n"
      + "  template:\n"
      + "    metadata:\n"
      + "      labels:\n"
      + "        app.kubernetes.io/name: hello\n"
      + "        app.kubernetes.io/instance: my-hello\n"
      + "    spec:\n"
      + "      serviceAccountName: default\n"
      + "      securityContext:\n"
      + "        {}\n"
      + "      containers:\n"
      + "        - name: hello\n"
      + "          securityContext:\n"
      + "            {}\n"
      + "          image: \"cloudecho/hello:0.1.0\"\n"
      + "          imagePullPolicy: IfNotPresent\n"
      + "          ports:\n"
      + "            - name: http\n"
      + "              containerPort: 8080\n"
      + "              protocol: TCP\n"
      + "          livenessProbe:\n"
      + "            httpGet:\n"
      + "              path: /\n"
      + "              port: http\n"
      + "          readinessProbe:\n"
      + "            httpGet:\n"
      + "              path: /\n"
      + "              port: http\n"
      + "          resources:\n"
      + "            {}";

  private static final String GET_MANIFEST_WARNING_OUTPUT = "WARNING: Helm client warning output\n"
      + "\n"
      + "WARNING: Helm client warning output\n"
      + "\n"
      + "---\n"
      + "# Source: hello/templates/service.yaml\n"
      + "apiVersion: v1\n"
      + "kind: Service\n"
      + "metadata:\n"
      + "  name: my-hello\n"
      + "  labels:\n"
      + "    helm.sh/chart: hello-0.1.1\n"
      + "    app.kubernetes.io/name: hello\n"
      + "    app.kubernetes.io/instance: my-hello\n"
      + "    app.kubernetes.io/version: \"0.1.0\"\n"
      + "    app.kubernetes.io/managed-by: Helm\n"
      + "spec:\n"
      + "  type: ClusterIP\n"
      + "  ports:\n"
      + "    - port: 8080\n"
      + "      targetPort: http\n"
      + "      protocol: TCP\n"
      + "      name: http\n"
      + "  selector:\n"
      + "    app.kubernetes.io/name: hello\n"
      + "    app.kubernetes.io/instance: my-hello\n"
      + "---\n"
      + "# Source: hello/templates/deployment.yaml\n"
      + "apiVersion: apps/v1\n"
      + "kind: Deployment\n"
      + "metadata:\n"
      + "  name: my-hello\n"
      + "  labels:\n"
      + "    helm.sh/chart: hello-0.1.1\n"
      + "    app.kubernetes.io/name: hello\n"
      + "    app.kubernetes.io/instance: my-hello\n"
      + "    app.kubernetes.io/version: \"0.1.0\"\n"
      + "    app.kubernetes.io/managed-by: Helm\n"
      + "spec:\n"
      + "  replicas: 1\n"
      + "  selector:\n"
      + "    matchLabels:\n"
      + "      app.kubernetes.io/name: hello\n"
      + "      app.kubernetes.io/instance: my-hello\n"
      + "  template:\n"
      + "    metadata:\n"
      + "      labels:\n"
      + "        app.kubernetes.io/name: hello\n"
      + "        app.kubernetes.io/instance: my-hello\n"
      + "    spec:\n"
      + "      serviceAccountName: default\n"
      + "      securityContext:\n"
      + "        {}\n"
      + "      containers:\n"
      + "        - name: hello\n"
      + "          securityContext:\n"
      + "            {}\n"
      + "          image: \"cloudecho/hello:0.1.0\"\n"
      + "          imagePullPolicy: IfNotPresent\n"
      + "          ports:\n"
      + "            - name: http\n"
      + "              containerPort: 8080\n"
      + "              protocol: TCP\n"
      + "          livenessProbe:\n"
      + "            httpGet:\n"
      + "              path: /\n"
      + "              port: http\n"
      + "          readinessProbe:\n"
      + "            httpGet:\n"
      + "              path: /\n"
      + "              port: http\n"
      + "          resources:\n"
      + "            {}";

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReadManifestFromHelmOutputDefault() {
    List<KubernetesResource> resources = HelmClientUtils.readManifestFromHelmOutput(GET_MANIFEST_CLEAN_OUTPUT);
    assertThat(resources.stream().map(KubernetesResource::getResourceId).map(KubernetesResourceId::kindNameRef))
        .containsExactlyInAnyOrder("Service/my-hello", "Deployment/my-hello");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReadManifestFromHelmOutputDirty() {
    List<KubernetesResource> resources = HelmClientUtils.readManifestFromHelmOutput(GET_MANIFEST_WARNING_OUTPUT);
    assertThat(resources.stream().map(KubernetesResource::getResourceId).map(KubernetesResourceId::kindNameRef))
        .containsExactlyInAnyOrder("Service/my-hello", "Deployment/my-hello");
  }
}