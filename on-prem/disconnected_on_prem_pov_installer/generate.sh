#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -e

INSTALLER_DIR=harness_installer
INSTALLER_COMPRESSED_FILE=harness_installer.tar.gz
INSTALLER_TEMPLATE_DIR=harness_disconnected_on_prem_pov_final
SCRIPTS_DIR=scripts
CONFIG_PROPERTIES_FILE="${INSTALLER_DIR}/config.properties"
FIRST_TIME_INSTALL_SCRIPT_FILE=first_time_only_install_harness.sh
UPGRADE_SCRIPT_FILE=upgrade_harness.sh
VERSION_PROPERTIES_FILE=version.properties

IMAGES_DIR="${INSTALLER_DIR}/images"
MANAGER_IMAGE="harness/manager-signed:${MANAGER_VERSION}"
VERIFICATION_SERVICE_IMAGE="harness/verification-service-signed:${VERIFICATION_SERVICE_VERSION}"
LEARNING_ENGINE_IMAGE="harness/learning-engine-onprem-signed:${LEARNING_ENGINE_VERSION}"
UI_IMAGE="harness/ui-signed:${UI_VERSION}"
PROXY_IMAGE="harness/proxy-signed:${PROXY_VERSION}"
MONGO_IMAGE="harness/mongo:${MONGO_VERSION}"
TIMESCALE_IMAGE="timescale/timescaledb:${TIMESCALE_VERSION}"
PROM_IMAGE="prom/prometheus:${PROM_VERSION}"
GRAFANA_IMAGE="grafana/grafana:${GRAFANA_VERSION}"
CADVISOR_IMAGE="google/cadvisor:${CADVISOR_VERSION}"
ALERT_MANAGER_IMAGE="prom/alertmanager:${ALERT_MAN_VERSION}"

MANAGER_IMAGE_TAR="${IMAGES_DIR}/manager.tar"
VERIFICATION_SERVICE_IMAGE_TAR="${IMAGES_DIR}/verification_service.tar"
LEARNING_ENGINE_IMAGE_TAR="${IMAGES_DIR}/learning_engine.tar"
UI_IMAGE_TAR="${IMAGES_DIR}/ui.tar"
PROXY_IMAGE_TAR="${IMAGES_DIR}/proxy.tar"
MONGO_IMAGE_TAR="${IMAGES_DIR}/mongo.tar"
TIMESCALE_IMAGE_TAR="${IMAGES_DIR}/timescale.tar"
PROM_IMAGE_TAR="${IMAGES_DIR}/prom.tar"
GRAFANA_TAR="${IMAGES_DIR}/grafana.tar"
CADVISOR_TAR="${IMAGES_DIR}/cadvisor.tar"
ALERT_MANAGER_TAR="${IMAGES_DIR}/alertmanager.tar"

JRE_SOURCE_URL_1=https://app.harness.io/storage/wingsdelegates/jre/8u191
JRE_SOLARIS_1=jre-8u191-solaris-x64.tar.gz
JRE_MACOSX_1=jre-8u191-macosx-x64.tar.gz
JRE_LINUX_1=jre-8u191-linux-x64.tar.gz

JRE_SOURCE_URL_2=https://app.harness.io/storage/wingsdelegates/jre/openjdk-8u242
JRE_SOLARIS_2=jre_x64_solaris_8u242b08.tar.gz
JRE_MACOSX_2=jre_x64_macosx_8u242b08.tar.gz
JRE_LINUX_2=jre_x64_linux_8u242b08.tar.gz

ALPN_BOOT_JAR_URL=https://app.harness.io/public/shared/tools/alpn/release/8.1.13.v20181017
ALPN_BOOT_JAR=alpn-boot-8.1.13.v20181017.jar

OC_VERSION=v4.2.16
OC_LINUX_DIR="${IMAGES_DIR}/oc/linux/$OC_VERSION/"
OC_MAC_DIR="${IMAGES_DIR}/oc/darwin/$OC_VERSION/"

echo "$OC_MAC_DIR"
echo "$OC_LINUX_DIR"

OC_LINUX_URL=https://app.harness.io/storage/harness-download/harness-oc/release/"$OC_VERSION"/bin/linux/amd64/oc
OC_MAC_URL=https://app.harness.io/storage/harness-download/harness-oc/release/"$OC_VERSION"/bin/darwin/amd64/oc

rm -f "${INSTALLER_COMPRESSED_FILE}"

rm -rf "${INSTALLER_DIR}"
mkdir -p "${INSTALLER_DIR}"
mkdir -p "${IMAGES_DIR}"
cp README.txt "${INSTALLER_DIR}"

echo "Manager version is ${MANAGER_VERSION}"
echo "Mongo version is ${MONGO_VERSION}"
echo "Timescale version is ${TIMESCALE_VERSION}"
echo "Verification Service version is ${VERIFICATION_SERVICE_VERSION}"
echo "Delegate version is ${DELEGATE_VERSION}"
echo "Watcher version is ${WATCHER_VERSION}"
echo "Proxy version is ${PROXY_VERSION}"
echo "UI version is ${UI_VERSION}"
echo "Learning Engine version is ${LEARNING_ENGINE_VERSION}"
echo "oc version is ${OC_VERSION}"
echo "Prometheus version is ${PROM_VERSION}"
echo "Grafana version is ${GRAFANA_VERSION}"
echo "Cadvisor version is ${CADVISOR_VERSION}"
echo "Alert manager version is ${ALERT_MAN_VERSION}"

cp -r ../${INSTALLER_TEMPLATE_DIR}/* ${INSTALLER_DIR}/
cp "${VERSION_PROPERTIES_FILE}" "${INSTALLER_DIR}/"

mkdir -p $OC_LINUX_DIR
mkdir -p $OC_MAC_DIR

if [[ -z $1 ]]; then
  echo "No license file supplied, skipping setting the license file in the installer"
else
  echo "License file supplied, generating installer with license file $1"
  sed -i "s|harness_license|$1|g" "${CONFIG_PROPERTIES_FILE}"
fi

docker login -u ${DOCKERHUB_USERNAME} -p ${DOCKERHUB_PASSWORD}
docker pull "${MANAGER_IMAGE}"
docker pull "${VERIFICATION_SERVICE_IMAGE}"
docker pull "${LEARNING_ENGINE_IMAGE}"
docker pull "${UI_IMAGE}"
docker pull "${PROXY_IMAGE}"
docker pull "${MONGO_IMAGE}"
docker pull "${TIMESCALE_IMAGE}"
docker pull "${PROM_IMAGE}"
docker pull "${CADVISOR_IMAGE}"
docker pull "${GRAFANA_IMAGE}"
docker pull "${ALERT_MANAGER_IMAGE}"

docker save "${MANAGER_IMAGE}" >"${MANAGER_IMAGE_TAR}"
docker save "${VERIFICATION_SERVICE_IMAGE}" >"${VERIFICATION_SERVICE_IMAGE_TAR}"
docker save "${LEARNING_ENGINE_IMAGE}" >"${LEARNING_ENGINE_IMAGE_TAR}"
docker save "${UI_IMAGE}" >"${UI_IMAGE_TAR}"
docker save "${PROXY_IMAGE}" >"${PROXY_IMAGE_TAR}"
docker save "${MONGO_IMAGE}" >"${MONGO_IMAGE_TAR}"
docker save "${TIMESCALE_IMAGE}" >"${TIMESCALE_IMAGE_TAR}"
docker save "${PROM_IMAGE}" >"${PROM_IMAGE_TAR}"
docker save "${GRAFANA_IMAGE}" >"${GRAFANA_TAR}"
docker save "${CADVISOR_IMAGE}" >"${CADVISOR_TAR}"
docker save "${ALERT_MANAGER_IMAGE}" >"${ALERT_MANAGER_TAR}"

curl "${JRE_SOURCE_URL_1}/${JRE_SOLARIS_1}" >"${JRE_SOLARIS_1}"
curl "${JRE_SOURCE_URL_1}/${JRE_MACOSX_1}" >"${JRE_MACOSX_1}"
curl "${JRE_SOURCE_URL_1}/${JRE_LINUX_1}" >"${JRE_LINUX_1}"

curl "${JRE_SOURCE_URL_2}/${JRE_SOLARIS_2}" >"${JRE_SOLARIS_2}"
curl "${JRE_SOURCE_URL_2}/${JRE_MACOSX_2}" >"${JRE_MACOSX_2}"
curl "${JRE_SOURCE_URL_2}/${JRE_LINUX_2}" >"${JRE_LINUX_2}"

curl "${ALPN_BOOT_JAR_URL}/${ALPN_BOOT_JAR}" >"${ALPN_BOOT_JAR}"

curl -L -o "${OC_MAC_DIR}oc" "${OC_MAC_URL}"
curl -L -o "${OC_LINUX_DIR}oc" "${OC_LINUX_URL}"

for kubectlVersion in v1.13.2 v1.19.2; do
  echo "Adding kubectl $kubectlVersion"

  KUBECTL_LINUX_DIR="${IMAGES_DIR}/kubectl/linux/$kubectlVersion/"
  KUBECTL_MAC_DIR="${IMAGES_DIR}/kubectl/darwin/$kubectlVersion/"

  KUBECTL_LINUX_URL=https://app.harness.io/storage/harness-download/kubernetes-release/release/"$kubectlVersion"/bin/linux/amd64/kubectl
  KUBECTL_MAC_URL=https://app.harness.io/storage/harness-download/kubernetes-release/release/"$kubectlVersion"/bin/darwin/amd64/kubectl

  echo "$KUBECTL_MAC_DIR"
  echo "$KUBECTL_LINUX_DIR"

  mkdir -p "$KUBECTL_MAC_DIR"
  mkdir -p "$KUBECTL_LINUX_DIR"

  curl -L -o "${KUBECTL_MAC_DIR}kubectl" "${KUBECTL_MAC_URL}"
  curl -L -o "${KUBECTL_LINUX_DIR}kubectl" "${KUBECTL_LINUX_URL}"

done

for goversion in v0.2 v0.3 v0.4; do
  echo "Adding goversion $goversion"
  GOTEMPLATE_LINUX_DIR="${IMAGES_DIR}/go-template/linux/$goversion/"
  GOTEMPLATE_MAC_DIR="${IMAGES_DIR}/go-template/darwin/$goversion/"

  GOTEMPLATE_LINUX_URL=https://app.harness.io/storage/harness-download/snapshot-go-template/release/"$goversion"/bin/linux/amd64/go-template
  GOTEMPLATE_MAC_URL=https://app.harness.io/storage/harness-download/snapshot-go-template/release/"$goversion"/bin/darwin/amd64/go-template

  echo "$GOTEMPLATE_MAC_DIR"
  echo "$GOTEMPLATE_LINUX_DIR"

  mkdir -p $GOTEMPLATE_LINUX_DIR
  mkdir -p $GOTEMPLATE_MAC_DIR

  curl -L -o "${GOTEMPLATE_LINUX_DIR}go-template" "${GOTEMPLATE_LINUX_URL}"
  curl -L -o "${GOTEMPLATE_MAC_DIR}go-template" "${GOTEMPLATE_MAC_URL}"
done

for harnesspywinrm in v0.1-dev v0.2-dev v0.3-dev v0.4-dev; do
  echo "Adding harness-pywinrm $harnesspywinrm"
  HARNESSPYWINRM_LINUX_DIR="${IMAGES_DIR}/harness-pywinrm/linux/$harnesspywinrm/"
  HARNESSPYWINRM_MAC_DIR="${IMAGES_DIR}/harness-pywinrm/darwin/$harnesspywinrm/"

  HARNESSPYWINRM_LINUX_URL=https://app.harness.io/storage/harness-download/snapshot-harness-pywinrm/release/"$harnesspywinrm"/bin/linux/amd64/harness-pywinrm
  HARNESSPYWINRM_MAC_URL=https://app.harness.io/storage/harness-download/snapshot-harness-pywinrm/release/"$harnesspywinrm"/bin/darwin/amd64/harness-pywinrm

  echo "$HARNESSPYWINRM_MAC_DIR"
  echo "$HARNESSPYWINRM_LINUX_DIR"

  mkdir -p $HARNESSPYWINRM_LINUX_DIR
  mkdir -p $HARNESSPYWINRM_MAC_DIR

  curl -L -o "${HARNESSPYWINRM_LINUX_DIR}harness-pywinrm" "${HARNESSPYWINRM_LINUX_URL}"
  curl -L -o "${HARNESSPYWINRM_MAC_DIR}harness-pywinrm" "${HARNESSPYWINRM_MAC_URL}"
done

for helmversion in v2.13.1 v3.1.2 v3.8.0; do
  echo "Adding helmversion $helmversion"
  HELM_LINUX_DIR="${IMAGES_DIR}/helm/linux/$helmversion/"
  HELM_MAC_DIR="${IMAGES_DIR}/helm/darwin/$helmversion/"

  HELM_LINUX_URL=https://app.harness.io/storage/harness-download/harness-helm/release/"$helmversion"/bin/linux/amd64/helm
  HELM_MAC_URL=https://app.harness.io/storage/harness-download/harness-helm/release/"$helmversion"/bin/darwin/amd64/helm

  echo "$HELM_MAC_DIR"
  echo "$HELM_LINUX_DIR"

  mkdir -p $HELM_LINUX_DIR
  mkdir -p $HELM_MAC_DIR

  curl -L -o "${HELM_LINUX_DIR}helm" "${HELM_LINUX_URL}"
  curl -L -o "${HELM_MAC_DIR}helm" "${HELM_MAC_URL}"
done

for chartmuseumversion in v0.8.2 v0.12.0; do
  echo "Adding chartmuseumversion $chartmuseumversion"
  CHARTMUSEUM_LINUX_DIR="${IMAGES_DIR}/chartmuseum/linux/$chartmuseumversion/"
  CHARTMUSEUM_MAC_DIR="${IMAGES_DIR}/chartmuseum/darwin/$chartmuseumversion/"

  CHARTMUSEUM_LINUX_URL=https://app.harness.io/storage/harness-download/harness-chartmuseum/release/"$chartmuseumversion"/bin/linux/amd64/chartmuseum
  CHARTMUSEUM_MAC_URL=https://app.harness.io/storage/harness-download/harness-chartmuseum/release/"$chartmuseumversion"/bin/darwin/amd64/chartmuseum

  echo "$CHARTMUSEUM_MAC_DIR"
  echo "$CHARTMUSEUM_LINUX_DIR"

  mkdir -p $CHARTMUSEUM_LINUX_DIR
  mkdir -p $CHARTMUSEUM_MAC_DIR

  curl -L -o "${CHARTMUSEUM_LINUX_DIR}chartmuseum" "${CHARTMUSEUM_LINUX_URL}"
  curl -L -o "${CHARTMUSEUM_MAC_DIR}chartmuseum" "${CHARTMUSEUM_MAC_URL}"
done

for tfConfigInspectVersion in v1.0 v1.1 v1.2; do
  echo "Adding terraform-config-inspect" $tfConfigInspectVersion

  TF_CONFIG_INSPECT_LINUX_DIR="${IMAGES_DIR}/tf-config-inspect/linux/$tfConfigInspectVersion/"
  TF_CONFIG_INSPECT_MAC_DIR="${IMAGES_DIR}/tf-config-inspect/darwin/$tfConfigInspectVersion/"

  TF_CONFIG_INSPECT_LINUX_URL=https://app.harness.io/storage/harness-download/harness-terraform-config-inspect/"$tfConfigInspectVersion"/linux/amd64/terraform-config-inspect
  TF_CONFIG_INSPECT_MAC_URL=https://app.harness.io/storage/harness-download/harness-terraform-config-inspect/"$tfConfigInspectVersion"/darwin/amd64/terraform-config-inspect

  echo "$TF_CONFIG_INSPECT_LINUX_DIR"
  echo "$TF_CONFIG_INSPECT_MAC_DIR"

  mkdir -p "$TF_CONFIG_INSPECT_LINUX_DIR"
  mkdir -p "$TF_CONFIG_INSPECT_MAC_DIR"

  curl -L -o "${TF_CONFIG_INSPECT_LINUX_DIR}terraform-config-inspect" "$TF_CONFIG_INSPECT_LINUX_URL"
  curl -L -o "${TF_CONFIG_INSPECT_MAC_DIR}terraform-config-inspect" "$TF_CONFIG_INSPECT_MAC_URL"

done

for kustomizeVersion in v3.5.4 v4.0.0; do
  echo "Adding kustomize" $kustomizeVersion

  KUSTOMIZE_LINUX_DIR="${IMAGES_DIR}/kustomize/linux/$kustomizeVersion/"
  KUSTOMIZE_MAC_DIR="${IMAGES_DIR}/kustomize/darwin/$kustomizeVersion/"

  KUSTOMIZE_LINUX_URL=https://app.harness.io/storage/harness-download/harness-kustomize/release/"$kustomizeVersion"/bin/linux/amd64/kustomize
  KUSTOMIZE_MAC_URL=https://app.harness.io/storage/harness-download/harness-kustomize/release/"$kustomizeVersion"/bin/darwin/amd64/kustomize

  echo "$KUSTOMIZE_LINUX_DIR"
  echo "$KUSTOMIZE_MAC_DIR"

  mkdir -p "$KUSTOMIZE_LINUX_DIR"
  mkdir -p "$KUSTOMIZE_MAC_DIR"

  curl -L -o "${KUSTOMIZE_LINUX_DIR}kustomize" "$KUSTOMIZE_LINUX_URL"
  curl -L -o "${KUSTOMIZE_MAC_DIR}kustomize" "$KUSTOMIZE_MAC_URL"

done

for scmVersion in 91e2a39c; do
  echo "Adding scm" $scmVersion

  SCM_LINUX_DIR="${IMAGES_DIR}/scm/linux/$scmVersion/"
  SCM_MAC_DIR="${IMAGES_DIR}/scm/darwin/$scmVersion/"

  echo "$SCM_MAC_DIR"
  echo "$SCM_LINUX_DIR"

  SCM_LINUX_URL=https://app.harness.io/storage/harness-download/harness-scm/release/"$scmVersion"/bin/linux/amd64/scm
  SCM_MAC_URL=https://app.harness.io/storage/harness-download/harness-scm/release/"$scmVersion"/bin/darwin/amd64/scm

  echo "$SCM_LINUX_DIR"
  echo "$SCM_MAC_DIR"

  mkdir -p $SCM_LINUX_DIR
  mkdir -p $SCM_MAC_DIR

  curl -L -o "${SCM_MAC_DIR}scm" "${SCM_MAC_URL}"
  curl -L -o "${SCM_LINUX_DIR}scm" "${SCM_LINUX_URL}"

done

cp delegate.jar "${IMAGES_DIR}/"
cp watcher.jar "${IMAGES_DIR}/"
mv "${JRE_SOLARIS_1}" "${IMAGES_DIR}/"
mv "${JRE_MACOSX_1}" "${IMAGES_DIR}/"
mv "${JRE_LINUX_1}" "${IMAGES_DIR}/"

mv "${JRE_SOLARIS_2}" "${IMAGES_DIR}/"
mv "${JRE_MACOSX_2}" "${IMAGES_DIR}/"
mv "${JRE_LINUX_2}" "${IMAGES_DIR}/"

mv "${ALPN_BOOT_JAR}" "${IMAGES_DIR}/"

tar -cvzf "${INSTALLER_COMPRESSED_FILE}" "${INSTALLER_DIR}"
#rm -rf "${INSTALLER_DIR}"
