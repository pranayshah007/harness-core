/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.OwnedBy;

import java.util.concurrent.TimeUnit;

/**
 * Created by anubhaw on 3/23/18.
 */
@OwnedBy(CDP)
public final class HelmConstants {
  public static final String HELM_PATH_PLACEHOLDER = "${HELM_PATH}";
  public static final String HELM_NAMESPACE_PLACEHOLDER_REGEX = "\\$\\{NAMESPACE}";
  public static final String HELM_NAMESPACE_PLACEHOLDER = "${NAMESPACE}";
  public static final String HELM_DOCKER_IMAGE_NAME_PLACEHOLDER = "${DOCKER_IMAGE_NAME}";
  public static final String HELM_DOCKER_IMAGE_TAG_PLACEHOLDER = "${DOCKER_IMAGE_TAG}";
  public static final String HELM_COMMAND_FLAG_PLACEHOLDER = "${COMMAND_FLAGS}";
  public static final String ADD_COMMAND_FOR_REPOSITORY = "helm repo add command for repository ";
  public static final String REPO_NAME = "${REPO_NAME}";
  public static final String HELM_CACHE_HOME_PLACEHOLDER = "${HELM_CACHE_HOME}";
  public static final String REPO_URL = "${REPO_URL}";
  public static final String USERNAME = "${USERNAME}";
  public static final String PASSWORD = "${PASSWORD}";
  public static final String HELM_HOME_PATH_FLAG = "${HELM_HOME_PATH_FLAG}";
  public static final String HELM_FETCH_OLD_WORKING_DIR_BASE = "./repository/helm-values/" + REPO_NAME;
  public static final String WORKING_DIR_BASE = "./repository/helm/source/" + REPO_NAME;
  public static final String HELM_GCP_CREDS_PATH = "./repository/helm/gcpKeyFiles/${ACTIVITY_ID}";
  public static final String VALUES_YAML = "values.yaml";
  public static final String CHARTS_YAML_KEY = "Chart.yaml";
  public static final String CHART_VERSION = "${CHART_VERSION}";

  // Add more command types as needed
  enum CommandType { REPO_ADD, REPO_UPDATE }

  public static final class V2Commands {
    // The reason we are using ^ and $ before and after ${RELEASE_NAME} is because helm list doesn't take releaseName as
    // a param and release name becomes a regex
    public static final String HELM_LIST_RELEASE_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} list ${COMMAND_FLAGS} ^${RELEASE_NAME}$";

    public static final String HELM_ROLLBACK_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} rollback ${COMMAND_FLAGS} ${RELEASE} ${REVISION}";
    public static final String HELM_INSTALL_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} install ${COMMAND_FLAGS} ${CHART_REFERENCE} ${OVERRIDE_VALUES} --name ${RELEASE_NAME} ${NAMESPACE}";
    public static final String HELM_UPGRADE_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} upgrade ${COMMAND_FLAGS} ${RELEASE_NAME} ${CHART_REFERENCE} ${OVERRIDE_VALUES}";
    public static final String HELM_RELEASE_HIST_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} hist ${COMMAND_FLAGS} ${RELEASE_NAME} ${FLAGS}";
    public static final String HELM_ADD_REPO_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL}";
    public static final String HELM_REPO_UPDATE_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} repo update ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_REPO_LIST_COMMAND_TEMPLATE = "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} repo list";
    public static final String HELM_DELETE_RELEASE_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} delete ${COMMAND_FLAGS} ${FLAGS} ${RELEASE_NAME}";
    public static final String HELM_TEMPLATE_COMMAND_FOR_KUBERNETES_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} template ${CHART_LOCATION} ${COMMAND_FLAGS} --name ${RELEASE_NAME} --namespace ${NAMESPACE} ${OVERRIDE_VALUES}";
    public static final String HELM_SEARCH_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} search ${CHART_INFO}";
    public static final String HELM_REPO_ADD_COMMAND_FOR_CHART_MUSEUM =
        "${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL} ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_REPO_ADD_COMMAND_FOR_HTTP =
        "${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL} ${USERNAME} ${PASSWORD} ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_FETCH_COMMAND =
        "${HELM_PATH} fetch ${REPO_NAME}/${CHART_NAME} --untar ${CHART_VERSION} ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_REPO_REMOVE_COMMAND =
        "${HELM_PATH} repo remove ${REPO_NAME} ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_INIT_COMMAND = "${HELM_PATH} init -c --skip-refresh ${HELM_HOME_PATH_FLAG}";
    public static final String HELM_RENDER_SPECIFIC_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} template ${CHART_LOCATION} ${COMMAND_FLAGS} -x ${CHART_FILE} --name ${RELEASE_NAME} --namespace ${NAMESPACE} ${OVERRIDE_VALUES}";
    public static final String HELM_VERSION_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} version --short ${COMMAND_FLAGS}";
    public static final String HELM_FETCH_ALL_VERSIONS_COMMAND_TEMPLATE =
        "${HELM_PATH} search ${REPO_NAME}/${CHART_NAME} -l ${HELM_HOME_PATH_FLAG} --col-width 300";
    public static final String HELM_GET_MANIFEST =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} get manifest ${RELEASE_NAME}";
  }

  public static final class V3Commands {
    public static final String HELM_LIST_RELEASE_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} list ${COMMAND_FLAGS} --filter ^${RELEASE_NAME}$";

    public static final String HELM_ROLLBACK_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} rollback  ${RELEASE} ${REVISION} ${COMMAND_FLAGS}";
    public static final String HELM_INSTALL_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} install  ${RELEASE_NAME} ${CHART_REFERENCE}  ${COMMAND_FLAGS} ${OVERRIDE_VALUES} ${NAMESPACE}";
    public static final String HELM_UPGRADE_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} upgrade ${COMMAND_FLAGS} ${RELEASE_NAME} ${CHART_REFERENCE} ${OVERRIDE_VALUES}";
    public static final String HELM_RELEASE_HIST_COMMAND_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} hist ${RELEASE_NAME} ${COMMAND_FLAGS}  ${FLAGS}";
    public static final String HELM_ADD_REPO_COMMAND_TEMPLATE = "${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL}";
    public static final String HELM_REPO_UPDATE_COMMAND_TEMPLATE = "${HELM_PATH} repo update ${COMMAND_FLAGS}";
    public static final String HELM_REPO_LIST_COMMAND_TEMPLATE = "${HELM_PATH} repo list";
    public static final String HELM_DELETE_RELEASE_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} uninstall ${RELEASE_NAME} ${COMMAND_FLAGS}";
    public static final String HELM_TEMPLATE_COMMAND_FOR_KUBERNETES_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} template ${RELEASE_NAME} ${CHART_LOCATION} ${COMMAND_FLAGS} --namespace ${NAMESPACE} ${OVERRIDE_VALUES}";
    public static final String HELM_SEARCH_COMMAND_TEMPLATE = "${HELM_PATH} search repo ${CHART_INFO}";
    public static final String HELM_REPO_ADD_COMMAND_FOR_CHART_MUSEUM =
        "${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL} ${COMMAND_FLAGS}";
    public static final String HELM_REPO_ADD_COMMAND_FOR_HTTP =
        "${HELM_PATH} repo add ${REPO_NAME} ${REPO_URL} ${USERNAME} ${PASSWORD} ${COMMAND_FLAGS}";
    public static final String HELM_FETCH_COMMAND =
        "${HELM_PATH} pull ${REPO_NAME}/${CHART_NAME} ${COMMAND_FLAGS} --untar ${CHART_VERSION}";
    public static final String HELM_REPO_REMOVE_COMMAND = "${HELM_PATH} repo remove ${REPO_NAME}";
    public static final String HELM_INIT_COMMAND = EMPTY;
    public static final String HELM_RENDER_SPECIFIC_TEMPLATE =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} template ${RELEASE_NAME} ${CHART_LOCATION} ${COMMAND_FLAGS} -s ${CHART_FILE} --namespace ${NAMESPACE} ${OVERRIDE_VALUES}";
    public static final String HELM_VERSION_COMMAND_TEMPLATE = "${HELM_PATH} version --short ${COMMAND_FLAGS}";
    public static final String HELM_OCI_REGISTRY_LOGIN_COMMAND_TEMPLATE =
        "${HELM_PATH} registry login ${REGISTRY_URL} ${USERNAME} ${PASSWORD}";
    public static final String HELM_FETCH_ALL_VERSIONS_COMMAND_TEMPLATE =
        "${HELM_PATH} search repo ${REPO_NAME}/${CHART_NAME} -l --devel --max-col-width 300";
    public static final String HELM_GET_MANIFEST =
        "KUBECONFIG=${KUBECONFIG_PATH} ${HELM_PATH} get manifest ${RELEASE_NAME} --namespace=${NAMESPACE}";

    public static final String HELM_REPO_FLAGS = " --repository-config ${HELM_CACHE_HOME}/repo-${REPO_NAME}.yaml";
    public static final String HELM_CACHE_HOME = "XDG_CACHE_HOME";
    public static final String HELM_CACHE_HOME_PATH = "${HELM_CACHE_HOME}/repo-${REPO_NAME}";
    public static final String HELM_CHART_VERSION_FLAG = " --version ${CHART_VERSION}";
    public static final String HELM_REPO_ADD_FORCE_UPDATE = " --force-update";
    public static final String REGISTRY_CONFIG_SUFFIX = "--registry-config ${REGISTRY_CONFIG}";
    public static final String REGISTRY_CONFIG = "${REGISTRY_CONFIG}";

    private V3Commands() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
  }

  public static final long DEFAULT_TILLER_CONNECTION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);
  public static final String HELM_RELEASE_LABEL = "release";
  public static final String HELM_HOOK_ANNOTATION = "helm.sh/hook";

  public static final String DEFAULT_HELM_VALUE_YAML = "# Enter your Helm value YAML\n"
      + "#\n"
      + "# Placeholders:\n"
      + "#\n"
      + "# Optional: ${NAMESPACE}\n"
      + "#   - Replaced with the namespace\n"
      + "#     Harness will set the namespace from infrastructure\n"
      + "#     mapping namespace\n"
      + "#\n"
      + "# Optional: ${DOCKER_IMAGE_NAME}\n"
      + "#   - Replaced with the Docker image name\n"
      + "#\n"
      + "# Optional: ${DOCKER_IMAGE_TAG}\n"
      + "#   - Replaced with the Docker image tag\n"
      + "#\n"
      + "# ---\n"
      + "namespace : ${NAMESPACE}\n";

  public class ReleaseRecordConstants {
    public static final String NAME = "NAME";
    public static final String REVISION = "REVISION";
    public static final String STATUS = "STATUS";
    public static final String CHART = "CHART";
    public static final String NAMESPACE = "NAMESPACE";
  }
}
