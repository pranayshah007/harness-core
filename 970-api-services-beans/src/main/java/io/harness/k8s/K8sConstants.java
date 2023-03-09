/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public interface K8sConstants {
  String OIDC_CLIENT_ID = "${CLIENT_ID_DATA}";
  String OIDC_CLIENT_SECRET = "${CLIENT_SECRET_DATA}";
  String OIDC_ID_TOKEN = "${ID_TOKEN_DATA}";
  String OIDC_RERESH_TOKEN = "${REFRESH_TOKEN_DATA}";
  String OIDC_ISSUER_URL = "${ISSUER_URL_DATA}";
  String OIDC_AUTH_NAME = "${NAME_DATA}";
  String OIDC_AUTH_NAME_VAL = "oidc";
  String MASTER_URL = "${MASTER_URL}";
  String NAMESPACE = "${NAMESPACE}";
  String NAME = "name: ";

  String CLIENT_ID_KEY = "client-id: ";
  String CLIENT_SECRET_KEY = "client-secret: ";
  String ID_TOKEN_KEY = "id-token: ";
  String ISSUER_URL_KEY = "idp-issuer-url: ";
  String REFRESH_TOKEN = "refresh-token: ";
  String NAMESPACE_KEY = "namespace: ";

  String OPEN_ID = "openid";

  String KUBE_CONFIG_OIDC_TEMPLATE = "apiVersion: v1\n"
      + "clusters:\n"
      + "- cluster:\n"
      + "    server: ${MASTER_URL}\n"
      + "    insecure-skip-tls-verify: true\n"
      + "  name: CLUSTER_NAME\n"
      + "contexts:\n"
      + "- context:\n"
      + "    cluster: CLUSTER_NAME\n"
      + "    user: HARNESS_USER\n"
      + "    ${NAMESPACE}\n"
      + "  name: CURRENT_CONTEXT\n"
      + "current-context: CURRENT_CONTEXT\n"
      + "kind: Config\n"
      + "preferences: {}\n"
      + "users:\n"
      + "- name: HARNESS_USER\n"
      + "  user:\n"
      + "    auth-provider:\n"
      + "      config:\n"
      + "        " + OIDC_CLIENT_ID + "\n"
      + "        " + OIDC_CLIENT_SECRET + "\n"
      + "        " + OIDC_ID_TOKEN + "\n"
      + "        " + OIDC_RERESH_TOKEN + "\n"
      + "        " + OIDC_ISSUER_URL + "\n"
      + "      " + OIDC_AUTH_NAME + "\n";

  String KUBECONFIG_FILENAME = "config";
  String GCP_JSON_KEY_FILE_NAME = "google-application-credentials.json";

  String HARNESS_KUBERNETES_REVISION_LABEL_KEY = "harness.io/revision";
  String KUBE_CONFIG_TEMPLATE = "apiVersion: v1\n"
      + "clusters:\n"
      + "- cluster:\n"
      + "    server: ${MASTER_URL}\n"
      + "    ${INSECURE_SKIP_TLS_VERIFY}\n"
      + "    ${CERTIFICATE_AUTHORITY_DATA}\n"
      + "  name: CLUSTER_NAME\n"
      + "contexts:\n"
      + "- context:\n"
      + "    cluster: CLUSTER_NAME\n"
      + "    user: HARNESS_USER\n"
      + "    ${NAMESPACE}\n"
      + "  name: CURRENT_CONTEXT\n"
      + "current-context: CURRENT_CONTEXT\n"
      + "kind: Config\n"
      + "preferences: {}\n"
      + "users:\n"
      + "- name: HARNESS_USER\n"
      + "  user:\n"
      + "    ${CLIENT_CERT_DATA}\n"
      + "    ${CLIENT_KEY_DATA}\n"
      + "    ${PASSWORD}\n"
      + "    ${USER_NAME}\n"
      + "    ${SERVICE_ACCOUNT_TOKEN_DATA}";

  String GCP_KUBE_CONFIG_TEMPLATE = "apiVersion: v1\n"
      + "clusters:\n"
      + "- cluster:\n"
      + "    server: ${MASTER_URL}\n"
      + "    ${INSECURE_SKIP_TLS_VERIFY}\n"
      + "    ${CERTIFICATE_AUTHORITY_DATA}\n"
      + "  name: CLUSTER_NAME\n"
      + "contexts:\n"
      + "- context:\n"
      + "    cluster: CLUSTER_NAME\n"
      + "    user: HARNESS_USER\n"
      + "    ${NAMESPACE}\n"
      + "  name: CURRENT_CONTEXT\n"
      + "current-context: CURRENT_CONTEXT\n"
      + "kind: Config\n"
      + "preferences: {}\n"
      + "users:\n"
      + "- name: HARNESS_USER\n"
      + "  user:\n"
      + "    auth-provider:\n"
      + "      name: gcp\n";

  String AZURE_KUBE_CONFIG_TEMPLATE = "apiVersion: v1\n"
      + "clusters:\n"
      + "- cluster:\n"
      + "    server: ${MASTER_URL}\n"
      + "    ${INSECURE_SKIP_TLS_VERIFY}\n"
      + "    ${CERTIFICATE_AUTHORITY_DATA}\n"
      + "  name: ${CLUSTER_NAME}\n"
      + "contexts:\n"
      + "- context:\n"
      + "    cluster: ${CLUSTER_NAME}\n"
      + "    user: ${CLUSTER_USER}\n"
      + "    ${NAMESPACE}\n"
      + "  name: ${CURRENT_CONTEXT}\n"
      + "current-context: ${CURRENT_CONTEXT}\n"
      + "kind: Config\n"
      + "preferences: {}\n"
      + "users:\n"
      + "- name: ${CLUSTER_USER}\n"
      + "  user:\n"
      + "    token: ${TOKEN}\n"
      + "    auth-provider:\n"
      + "      name: azure\n"
      + "      config:\n"
      + "        apiserver-id: ${APISERVER_ID}\n"
      + "        client-id: ${CLIENT_ID}\n"
      + "        config-mode: \"${CONFIG_MODE}\"\n"
      + "        environment: ${ENVIRONMENT}\n"
      + "        tenant-id: ${TENANT_ID}\n";

  String KUBE_CONFIG_EXEC_TEMPLATE = "apiVersion: v1\n"
      + "clusters:\n"
      + "- cluster:\n"
      + "    server: ${MASTER_URL}\n"
      + "    ${INSECURE_SKIP_TLS_VERIFY}\n"
      + "    ${CERTIFICATE_AUTHORITY_DATA}\n"
      + "  name: ${CLUSTER_NAME}\n"
      + "contexts:\n"
      + "- context:\n"
      + "    cluster: ${CLUSTER_NAME}\n"
      + "    user: ${CLUSTER_USER}\n"
      + "    ${NAMESPACE}\n"
      + "  name: ${CURRENT_CONTEXT}\n"
      + "current-context: ${CURRENT_CONTEXT}\n"
      + "kind: Config\n"
      + "preferences: {}\n"
      + "users:\n"
      + "- name: ${CLUSTER_USER}\n"
      + "  user:\n"
      + "    exec:\n"
      + "      apiVersion: client.authentication.k8s.io/${API_VERSION}\n"
      + "      args: ${ARGS}\n"
      + "      command: ${KUBELOGIN_BINARY}\n"
      + "      installHint: ${INSTALL_HINT}"
      + "      env: ${ENV}\n"
      + "      interactiveMode: Never\n"
      + "      provideClusterInfo: false\n";

  String eventOutputFormat =
      "custom-columns=KIND:involvedObject.kind,NAME:.involvedObject.name,MESSAGE:.message,REASON:.reason";
  int FETCH_FILES_DISPLAY_LIMIT = 100;
  String eventWithNamespaceOutputFormat =
      "custom-columns=KIND:involvedObject.kind,NAME:.involvedObject.name,NAMESPACE:.involvedObject.namespace,MESSAGE:.message,REASON:.reason";
  String ocRolloutStatusCommand = "{OC_COMMAND_PREFIX} rollout status {RESOURCE_ID} {NAMESPACE}--watch=true";
  String ocRolloutHistoryCommand = "{OC_COMMAND_PREFIX} rollout history {RESOURCE_ID} {NAMESPACE}";
  String ocRolloutUndoCommand = "{OC_COMMAND_PREFIX} rollout undo {RESOURCE_ID} {NAMESPACE}{REVISION}";
  String SKIP_FILE_FOR_DEPLOY_PLACEHOLDER_TEXT = "harness.io/skip-file-for-deploy";

  String MANIFEST_FILES_DIR = "manifest-files";

  String KUBERNETES_CHANGE_CAUSE_ANNOTATION = "kubernetes.io/change-cause";
  /**
   * The cloudwatch metric url.
   */
  String HARNESS_KUBE_CONFIG_PATH = "HARNESS_KUBE_CONFIG_PATH";

  String CANARY_WORKLOAD_SUFFIX_NAME = "canary";
  String CANARY_WORKLOAD_SUFFIX_NAME_WITH_SEPARATOR = "-" + CANARY_WORKLOAD_SUFFIX_NAME;

  String KUBECFG_EXEC = "exec";
  String KUBECFG_API_VERSION = "apiVersion";
  String KUBECFG_COMMAND = "command";
  String KUBECFG_ARGS = "args";
  String KUBECFG_ENV = "env";
  String KUBECFG_INTERACTIVE_MODE = "interactiveMode";
  String KUBECFG_CLUSTER_INFO = "provideClusterInfo";
  String KUBECFG_INSTALL_HINT = "installHint";
}
