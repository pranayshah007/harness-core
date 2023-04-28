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
      + "    ${EXEC}";

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

  String API_VERSION = "client.authentication.k8s.io/v1beta1";
  String KUBECFG_EXEC = "exec";
  String KUBECFG_API_VERSION = "apiVersion";
  String KUBECFG_COMMAND = "command";
  String KUBECFG_ARGS = "args";
  String KUBECFG_ENV = "env";
  String KUBECFG_INTERACTIVE_MODE = "interactiveMode";
  String KUBECFG_CLUSTER_INFO = "provideClusterInfo";
  String KUBECFG_INSTALL_HINT = "installHint";
  String KUBECFG_NAME = "name";
  String KUBECFG_VALUE = "value";

  String AZURE_AUTH_PLUGIN_BINARY = "kubelogin";
  String GCP_AUTH_PLUGIN_BINARY = "gke-gcloud-auth-plugin";
  String GOOGLE_APPLICATION_CREDENTIALS_FLAG = "--use_application_default_credentials";
  String GCP_AUTH_PLUGIN_INSTALL_HINT = "gke-gcloud-auth-plugin is required to authenticate to the current cluster.\n"
      + "It can be installed on the delegate using following command from:\n"
      + "https://cloud.google.com/sdk/docs/install#rpm\n"
      + "\n"
      + "tee -a /etc/yum.repos.d/google-cloud-sdk.repo << EOM\n"
      + "[google-cloud-cli]\n"
      + "name=Google Cloud CLI\n"
      + "baseurl=https://packages.cloud.google.com/yum/repos/cloud-sdk-el8-x86_64\n"
      + "enabled=1\n"
      + "gpgcheck=1\n"
      + "repo_gpgcheck=0\n"
      + "gpgkey=https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg\n"
      + "EOM\n"
      + "\n"
      + "// Download gke-gcloud-auth-plugin\n"
      + "microdnf install google-cloud-cli\n"
      + "microdnf install google-cloud-cli-gke-gcloud-auth-plugin\n"
      + "\n"
      + "// USE_GKE_GCLOUD_AUTH_PLUGIN=True for kubernetes version <1.26\n"
      + "echo \"export USE_GKE_GCLOUD_AUTH_PLUGIN=True\" >> ~/.bashrc\n"
      + "source ~/.bashrc\n";
  String EKS_AUTH_PLUGIN_BINARY = "aws-iam-authenticator";
  String EKS_AUTH_PLUGIN_INSTALL_HINT = "aws-iam-authenticator is required to authenticate to the current cluster.\n"
      + "It can be installed on the delegate by creating an immutable delegate and updating the following commands in INIT_SCRIPT\n"
      + "Reference: https://docs.aws.amazon.com/eks/latest/userguide/install-aws-iam-authenticator.html\n"
      + "\n"
      + "// Download aws-iam-authenticator\n"
      + "curl -Lo aws-iam-authenticator https://github.com/kubernetes-sigs/aws-iam-authenticator/releases/download/v0.5.9/aws-iam-authenticator_0.5.9_linux_amd64\n"
      + "chmod +x ./aws-iam-authenticator\n"
      + "\n"
      + "// Add the binary to PATH\n"
      + "mv ./aws-iam-authenticator /usr/local/bin\n"
      + "\n"
      + "// Verify the binary\n"
      + "aws-iam-authenticator version";
}
