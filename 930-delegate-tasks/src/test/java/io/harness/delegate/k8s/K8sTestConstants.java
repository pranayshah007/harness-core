/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

public final class K8sTestConstants {
  public static String DEPLOYMENT_DIRECT_APPLY_YAML = "apiVersion: apps/v1\n"
      + "kind: Deployment\n"
      + "metadata:\n"
      + "  name: deployment\n"
      + "  annotations:\n"
      + "    harness.io/direct-apply: true\n"
      + "  labels:\n"
      + "    app: nginx\n"
      + "spec:\n"
      + "  replicas: 3\n"
      + "  selector:\n"
      + "    matchLabels:\n"
      + "      app: nginx\n"
      + "  template:\n"
      + "    metadata:\n"
      + "      labels:\n"
      + "        app: nginx\n"
      + "    spec:\n"
      + "      containers:\n"
      + "      - name: nginx\n"
      + "        image: nginx:1.7.9\n"
      + "        ports:\n"
      + "        - containerPort: 80";

  public static String DEPLOYMENT_YAML = "apiVersion: apps/v1\n"
      + "kind: Deployment\n"
      + "metadata:\n"
      + "  name: deployment\n"
      + "  labels:\n"
      + "    app: nginx\n"
      + "spec:\n"
      + "  replicas: 3\n"
      + "  selector:\n"
      + "    matchLabels:\n"
      + "      app: nginx\n"
      + "  template:\n"
      + "    metadata:\n"
      + "      labels:\n"
      + "        app: nginx\n"
      + "    spec:\n"
      + "      containers:\n"
      + "      - name: nginx\n"
      + "        image: nginx:1.7.9\n"
      + "        ports:\n"
      + "        - containerPort: 80";

  public static String DAEMON_SET_YAML = "apiVersion: apps/v1\n"
      + "kind: DaemonSet\n"
      + "metadata:\n"
      + "  name: daemonSet\n"
      + "spec:\n"
      + "  replicas: 1";

  public static String STATEFUL_SET_YAML = "apiVersion: apps/v1\n"
      + "kind: StatefulSet\n"
      + "metadata:\n"
      + "  name: statefulSet\n"
      + "spec:\n"
      + "  serviceName: \"nginx\"\n"
      + "  replicas: 2\n"
      + "  selector:\n"
      + "    matchLabels:\n"
      + "      app: nginx\n"
      + "  template:\n"
      + "    metadata:\n"
      + "      labels:\n"
      + "        app: nginx\n"
      + "    spec:\n"
      + "      containers:\n"
      + "      - name: nginx\n"
      + "        image: k8s.gcr.io/nginx-slim:0.8\n"
      + "        ports:\n"
      + "        - containerPort: 80\n"
      + "          name: web\n"
      + "        volumeMounts:\n"
      + "        - name: www\n"
      + "          mountPath: /usr/share/nginx/html\n"
      + "  volumeClaimTemplates:\n"
      + "  - metadata:\n"
      + "      name: www\n"
      + "    spec:\n"
      + "      accessModes: [ \"ReadWriteOnce\" ]\n"
      + "      resources:\n"
      + "        requests:\n"
      + "          storage: 1Gi\n";

  public static String SERVICE_YAML = "apiVersion: v1\n"
      + "kind: Service\n"
      + "metadata:\n"
      + "  name: servicename\n"
      + "spec:\n"
      + "  type: ClusterIp\n"
      + "  ports:\n"
      + "  - port: 80\n"
      + "    targetPort: 8080\n"
      + "    protocol: TCP\n"
      + "  selector:\n"
      + "    app: test";

  public static String PRIMARY_SERVICE_YAML = "apiVersion: v1\n"
      + "kind: Service\n"
      + "metadata:\n"
      + "  name: primary-service\n"
      + "  annotations:\n"
      + "    harness.io/primary-service: true\n"
      + "spec:\n"
      + "  type: ClusterIp\n"
      + "  ports:\n"
      + "  - port: 80\n"
      + "    targetPort: 8080\n"
      + "    protocol: TCP\n"
      + "  selector:\n"
      + "    app: test";

  public static String STAGE_SERVICE_YAML = "apiVersion: v1\n"
      + "kind: Service\n"
      + "metadata:\n"
      + "  name: primary-service\n"
      + "  annotations:\n"
      + "    harness.io/stage-service: true\n"
      + "spec:\n"
      + "  type: ClusterIp\n"
      + "  ports:\n"
      + "  - port: 80\n"
      + "    targetPort: 8080\n"
      + "    protocol: TCP\n"
      + "  selector:\n"
      + "    app: test";

  public static String CONFIG_MAP_YAML = "apiVersion: v1\n"
      + "kind: ConfigMap\n"
      + "metadata:\n"
      + "  name: mycm\n"
      + "data:\n"
      + "  hello: world";

  public static String SKIP_VERSIONING_CONFIG_MAP_YAML = "apiVersion: v1\n"
      + "kind: ConfigMap\n"
      + "metadata:\n"
      + "  name: mycm-sv\n"
      + "  annotations:\n"
      + "    harness.io/skip-versioning: true\n"
      + "data:\n"
      + "  hello: world";

  public static String SECRET_YAML = "apiVersion: v1\n"
      + "kind: Secret\n"
      + "metadata:\n"
      + "  name: mysecret\n"
      + "type: Opaque\n"
      + "stringData:\n"
      + "  a: b\n";

  public static String SKIP_VERSIONING_SECRET_YAML = "apiVersion: v1\n"
      + "kind: Secret\n"
      + "metadata:\n"
      + "  name: mysecret-sv\n"
      + "  annotations:\n"
      + "    harness.io/skip-versioning: true\n"
      + "type: Opaque\n"
      + "stringData:\n"
      + "  a: b\n";

  private K8sTestConstants() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }
}
