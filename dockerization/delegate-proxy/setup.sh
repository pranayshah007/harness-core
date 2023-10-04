#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -x
set -e

mkdir -p destination
cd destination
rm -rf *

JDK=openjdk-8u242

echo "installing gsutil"
tee -a /etc/yum.repos.d/google-cloud-sdk.repo << EOM
[google-cloud-cli]
name=Google Cloud CLI
baseurl=https://packages.cloud.google.com/yum/repos/cloud-sdk-el8-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=0
gpgkey=https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
EOM
echo "here2"
microdnf install google-cloud-cli
echo "here3"


DELEGATE_VERSION=$DELEGATE_BUILD
WATCHER_VERSION=$WATCHER_BUILD
DELEGATE_PATCH_VERSION=$DELEGATE_PATCH

DELEGATE_BUCKET_PATH="delegate-jars/artifacts/saas/${JDK}/delegate-${DELEGATE_VERSION}-${DELEGATE_PATCH_VERSION}.jar"
WATCHER_BUCKET_PATH="watcher-jars/artifacts/saas/${JDK}/watcher-${WATCHER_VERSION}.jar"
DELEGATE_HELM_CHART_PATH="delegate-helm-chart"

echo
echo DELEGATE_BUCKET_PATH=$DELEGATE_BUCKET_PATH
echo

status=1
for i in {1..20}
do
  gsutil cp gs://$DELEGATE_BUCKET_PATH ./delegate.jar
  status=$?
  if [ $status -eq 0 ]; then
  	echo "delegate jar copied";
  	break;
  fi
  echo "not able to copy, sleeping for 15 seconds"
  sleep 15
done

if [ $status != 0 ]; then
  echo "delegate jar copy failed";
  exit 1
fi

echo
echo WATCHER_BUCKET_PATH=$WATCHER_BUCKET_PATH
echo

status=1
for i in {1..20}
do
  gsutil cp gs://$WATCHER_BUCKET_PATH ./watcher.jar
  status=$?
  if [ $status -eq 0 ]; then
  	echo "watcher jar copied";
  	break;
  fi
  echo "not able to copy, sleeping for 15 seconds"
  sleep 15
done

if [ $status != 0 ]; then
  echo "watcher jar copy failed";
  exit 1
fi

echo
echo DELEGATE_HELM_CHART_PATH=$DELEGATE_HELM_CHART_PATH
echo

mkdir -p helm_charts


status=1
for i in {1..20}
do
  gsutil -m cp -r gs://$DELEGATE_HELM_CHART_PATH ./helm_charts
  status=$?
  if [ $status -eq 0 ]; then
  	echo "helm chart copied";
  	break;
  fi
  echo "not able to copy, sleeping for 15 seconds"
  sleep 15
done

if [ $status != 0 ]; then
  echo "helm chart copy failed";
  exit 1
fi

ls -la /destination

IMAGES_DIR="images"
STORAGE_DIR_LOCATION="storage"

JRE_SOURCE_URL_1=https://app.harness.io/public/shared/jre/openjdk-11.0.19_7
JRE_SOLARIS_1=OpenJDK11U-jre_x64_solaris_hotspot_11.0.19_7.tar.gz
JRE_MACOSX_1=OpenJDK11U-jre_x64_macosx_hotspot_11.0.19_7.tar.gz
JRE_LINUX_1=OpenJDK11U-jre_x64_linux_hotspot_11.0.19_7.tar.gz

JRE_SOURCE_URL_2=https://app.harness.io/public/shared/jre/openjdk-11.0.14_9
JRE_SOLARIS_2=OpenJDK11U-jre_x64_solaris_hotspot_11.0.14_9.tar.gz
JRE_MACOSX_2=OpenJDK11U-jre_x64_macosx_hotspot_11.0.14_9.tar.gz
JRE_LINUX_2=OpenJDK11U-jre_x64_linux_hotspot_11.0.14_9.tar.gz

JRE_SOURCE_URL_3=https://app.harness.io/public/shared/jre/openjdk-11.0.19+7
JRE_SOLARIS_3=OpenJDK11U-jre_x64_solaris_hotspot_11.0.19+7.tar.gz
JRE_MACOSX_3=OpenJDK11U-jre_x64_macosx_hotspot_11.0.19+7.tar.gz
JRE_LINUX_3=OpenJDK11U-jre_x64_linux_hotspot_11.0.19+7.tar.gz


ALPN_BOOT_JAR_URL=https://app.harness.io/public/shared/tools/alpn/release/8.1.13.v20181017
ALPN_BOOT_JAR=alpn-boot-8.1.13.v20181017.jar

OC_VERSION=v4.2.16
OC_LINUX_URL=https://app.harness.io/storage/harness-download/harness-oc/release/"$OC_VERSION"/bin/linux/amd64/oc
OC_MAC_URL=https://app.harness.io/storage/harness-download/harness-oc/release/"$OC_VERSION"/bin/darwin/amd64/oc

OC_LINUX_DIR="${IMAGES_DIR}/oc/linux/$OC_VERSION/"
OC_MAC_DIR="${IMAGES_DIR}/oc/darwin/$OC_VERSION/"

mkdir -p $IMAGES_DIR

cp -f /destination/delegate.jar ${IMAGES_DIR}/delegate.jar
cp -f /destination/watcher.jar ${IMAGES_DIR}/watcher.jar

curl "${JRE_SOURCE_URL_1}/${JRE_SOLARIS_1}" >"${JRE_SOLARIS_1}"
curl "${JRE_SOURCE_URL_1}/${JRE_MACOSX_1}" >"${JRE_MACOSX_1}"
curl "${JRE_SOURCE_URL_1}/${JRE_LINUX_1}" >"${JRE_LINUX_1}"

curl "${JRE_SOURCE_URL_2}/${JRE_SOLARIS_2}" >"${JRE_SOLARIS_2}"
curl "${JRE_SOURCE_URL_2}/${JRE_MACOSX_2}" >"${JRE_MACOSX_2}"
curl "${JRE_SOURCE_URL_2}/${JRE_LINUX_2}" >"${JRE_LINUX_2}"

curl "${JRE_SOURCE_URL_3}/${JRE_SOLARIS_3}" >"${JRE_SOLARIS_3}"
curl "${JRE_SOURCE_URL_3}/${JRE_MACOSX_3}" >"${JRE_MACOSX_3}"
curl "${JRE_SOURCE_URL_3}/${JRE_LINUX_3}" >"${JRE_LINUX_3}"

curl "${ALPN_BOOT_JAR_URL}/${ALPN_BOOT_JAR}" >"${ALPN_BOOT_JAR}"

mkdir -p $OC_LINUX_DIR
mkdir -p $OC_MAC_DIR

curl -L -o "${OC_MAC_DIR}oc" "${OC_MAC_URL}"
curl -L -o "${OC_LINUX_DIR}oc" "${OC_LINUX_URL}"

mv "${JRE_SOLARIS_1}" "${IMAGES_DIR}/"
mv "${JRE_MACOSX_1}" "${IMAGES_DIR}/"
mv "${JRE_LINUX_1}" "${IMAGES_DIR}/"

mv "${JRE_SOLARIS_2}" "${IMAGES_DIR}/"
mv "${JRE_MACOSX_2}" "${IMAGES_DIR}/"
mv "${JRE_LINUX_2}" "${IMAGES_DIR}/"

mv "${JRE_SOLARIS_3}" "${IMAGES_DIR}/"
mv "${JRE_MACOSX_3}" "${IMAGES_DIR}/"
mv "${JRE_LINUX_3}" "${IMAGES_DIR}/"

mv "${ALPN_BOOT_JAR}" "${IMAGES_DIR}/"

for kubectlVersion in v1.13.2 v1.19.2; do
  echo "Adding kubectl $kubectlVersion"

  KUBECTL_LINUX_DIR="${IMAGES_DIR}/kubectl/linux/$kubectlVersion/"
  KUBECTL_MAC_DIR="${IMAGES_DIR}/kubectl/darwin/$kubectlVersion/"

  KUBECTL_LINUX_URL=https://app.harness.io/storage/harness-download/kubernetes-release/release/"$kubectlVersion"/bin/linux/amd64/kubectl
  KUBECTL_MAC_URL=https://app.harness.io/storage/harness-download/kubernetes-release/release/"$kubectlVersion"/bin/darwin/amd64/kubectl

  echo "$KUBECTL_MAC_DIR"
  echo "$KUBECTL_LINUX_DIR"

  mkdir -p $KUBECTL_LINUX_DIR
  mkdir -p $KUBECTL_MAC_DIR

  curl -L -o "${KUBECTL_MAC_DIR}kubectl" "${KUBECTL_MAC_URL}"
  curl -L -o "${KUBECTL_LINUX_DIR}kubectl" "${KUBECTL_LINUX_URL}"

done

for goversion in v0.2 v0.3 v0.4 v0.4.2 v0.4.4; do
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

for kustomizeVersion in v3.5.4 v4.0.0; do
  echo "Adding kustomizeversion $kustomizeVersion"
  KUSTOMIZE_LINUX_DIR="${IMAGES_DIR}/kustomize/linux/$kustomizeVersion/"
  KUSTOMIZE_MAC_DIR="${IMAGES_DIR}/kustomize/darwin/$kustomizeVersion/"

  KUSTOMIZE_LINUX_URL=https://app.harness.io/storage/harness-download/harness-kustomize/release/"$kustomizeVersion"/bin/linux/amd64/kustomize
  KUSTOMIZE_MAC_URL=https://app.harness.io/storage/harness-download/harness-kustomize/release/"$kustomizeVersion"/bin/darwin/amd64/kustomize

  echo $KUSTOMIZE_LINUX_DIR
  echo $KUSTOMIZE_MAC_DIR

  mkdir -p $KUSTOMIZE_LINUX_DIR
  mkdir -p $KUSTOMIZE_MAC_DIR

  curl -L -o "${KUSTOMIZE_MAC_DIR}kustomize" "${KUSTOMIZE_MAC_URL}"
  curl -L -o "${KUSTOMIZE_LINUX_DIR}kustomize" "${KUSTOMIZE_LINUX_URL}"
done


for tfConfigInspectVersion in v1.0 v1.1 v1.2 ; do
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

for scmVersion in 93b3c9f1; do
  echo "Adding scm" $scmVersion

  SCM_LINUX_DIR="${IMAGES_DIR}/scm/linux/$scmVersion/"
  SCM_MAC_DIR="${IMAGES_DIR}/scm/darwin/$scmVersion/"

  SCM_LINUX_URL=https://app.harness.io/storage/harness-download/harness-scm/release/"$scmVersion"/bin/linux/amd64/scm
  SCM_MAC_URL=https://app.harness.io/storage/harness-download/harness-scm/release/"$scmVersion"/bin/darwin/amd64/scm

  echo "$SCM_LINUX_DIR"
  echo "$SCM_MAC_DIR"

  mkdir -p "$SCM_LINUX_DIR"
  mkdir -p "$SCM_MAC_DIR"

  curl -L -o "${SCM_LINUX_DIR}scm" "$SCM_LINUX_URL"
  curl -L -o "${SCM_MAC_DIR}scm" "$SCM_MAC_URL"

done

function setupDelegateJars() {
  echo "################################ Setting up Delegate Jars ################################"


  mkdir -p $STORAGE_DIR_LOCATION/wingsdelegates/jre/openjdk-11.0.14_9/
  cp images/OpenJDK11U-jre*11.0.14_9.tar.gz $STORAGE_DIR_LOCATION/wingsdelegates/jre/openjdk-11.0.14_9/

  mkdir -p $STORAGE_DIR_LOCATION/wingsdelegates/jre/openjdk-11.0.19_7/
  cp images/OpenJDK11U-jre*11.0.19_7.tar.gz $STORAGE_DIR_LOCATION/wingsdelegates/jre/openjdk-11.0.19_7/

  mkdir -p $STORAGE_DIR_LOCATION/wingsdelegates/jre/openjdk-11.0.19+7/
  cp images/OpenJDK11U-jre*11.0.19+7.tar.gz $STORAGE_DIR_LOCATION/wingsdelegates/jre/openjdk-11.0.19+7/

  mkdir -p $STORAGE_DIR_LOCATION/wingsdelegates/tools/alpn/release/8.1.13.v20181017/
  cp images/alpn-boot-8.1.13.v20181017.jar $STORAGE_DIR_LOCATION/wingsdelegates/tools/alpn/release/8.1.13.v20181017/

  mkdir -p $STORAGE_DIR_LOCATION/harness-download
  cp -r /destination/helm_charts/delegate-helm-chart $STORAGE_DIR_LOCATION/harness-download

  rm -rf ${STORAGE_DIR_LOCATION}/wingsdelegates/jobs/deploy-prod-delegate/*
  mkdir -p ${STORAGE_DIR_LOCATION}/wingsdelegates/jobs/deploy-prod-delegate/${DELEGATE_VERSION}
  cp images/delegate.jar ${STORAGE_DIR_LOCATION}/wingsdelegates/jobs/deploy-prod-delegate/${DELEGATE_VERSION}/

  echo "1.0.${DELEGATE_VERSION} jobs/deploy-prod-delegate/${DELEGATE_VERSION}/delegate.jar" >delegateprod.txt

  mv delegateprod.txt ${STORAGE_DIR_LOCATION}/wingsdelegates

  rm -rf ${STORAGE_DIR_LOCATION}/wingswatchers/jobs/deploy-prod-watcher/*
  mkdir -p ${STORAGE_DIR_LOCATION}/wingswatchers/jobs/deploy-prod-watcher/${WATCHER_VERSION}
  cp images/watcher.jar ${STORAGE_DIR_LOCATION}/wingswatchers/jobs/deploy-prod-watcher/${WATCHER_VERSION}/
  echo "1.0.${WATCHER_VERSION} jobs/deploy-prod-watcher/${WATCHER_VERSION}/watcher.jar" >watcherprod.txt
  mv watcherprod.txt ${STORAGE_DIR_LOCATION}/wingswatchers

}

function setupClientUtils() {
  echo "################################ Setting up Client Utils ################################"

  echo "Copying kubectl go-template helm chartmuseum tf-config-inspect oc kustomize and scm"

  for platform in linux darwin; do
    for kubectlversion in v1.13.2 v1.19.2; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/kubernetes-release/release/$kubectlversion/bin/${platform}/amd64/
      cp images/kubectl/${platform}/$kubectlversion/kubectl ${STORAGE_DIR_LOCATION}/harness-download/kubernetes-release/release/$kubectlversion/bin/${platform}/amd64/
    done

    for kustomizeversion in v3.5.4 v4.0.0; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-kustomize/release/$kustomizeversion/bin/${platform}/amd64/
      cp images/kustomize/${platform}/$kustomizeversion/kustomize ${STORAGE_DIR_LOCATION}/harness-download/harness-kustomize/release/$kustomizeversion/bin/${platform}/amd64/
    done

    for gotemplateversion in v0.2 v0.3 v0.4 v0.4.2 v0.4.4; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/snapshot-go-template/release/$gotemplateversion/bin/${platform}/amd64/
      cp images/go-template/${platform}/$gotemplateversion/go-template ${STORAGE_DIR_LOCATION}/harness-download/snapshot-go-template/release/$gotemplateversion/bin/${platform}/amd64/
    done

    for harnesspywinrmversion in v0.1-dev v0.2-dev v0.3-dev v0.4-dev; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/snapshot-harness-pywinrm/release/$harnesspywinrmversion/bin/${platform}/amd64/
      cp images/harness-pywinrm/${platform}/$harnesspywinrmversion/harness-pywinrm ${STORAGE_DIR_LOCATION}/harness-download/snapshot-harness-pywinrm/release/$harnesspywinrmversion/bin/${platform}/amd64/
    done

    for helmversion in v2.13.1 v3.1.2 v3.8.0; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-helm/release/$helmversion/bin/${platform}/amd64/
      cp images/helm/${platform}/$helmversion/helm ${STORAGE_DIR_LOCATION}/harness-download/harness-helm/release/$helmversion/bin/${platform}/amd64/
    done

    for chartmuseumversion in v0.8.2 v0.12.0; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-chartmuseum/release/$chartmuseumversion/bin/${platform}/amd64/
      cp images/chartmuseum/${platform}/$chartmuseumversion/chartmuseum ${STORAGE_DIR_LOCATION}/harness-download/harness-chartmuseum/release/$chartmuseumversion/bin/${platform}/amd64/
    done

    for tfConfigInspectVersion in v1.0 v1.1 v1.2; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-terraform-config-inspect/"$tfConfigInspectVersion"/${platform}/amd64/
      cp images/tf-config-inspect/${platform}/"$tfConfigInspectVersion"/terraform-config-inspect ${STORAGE_DIR_LOCATION}/harness-download/harness-terraform-config-inspect/"$tfConfigInspectVersion"/${platform}/amd64/
    done

    for ocversion in v4.2.16; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-oc/release/$ocversion/bin/${platform}/amd64/
      cp images/oc/${platform}/$ocversion/oc ${STORAGE_DIR_LOCATION}/harness-download/harness-oc/release/$ocversion/bin/${platform}/amd64/
    done

    for scmVersion in <+execution.steps.GetScmVersion.output.outputVariables.scmVersion>; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-scm/release/$scmVersion/bin/${platform}/amd64/
      cp images/scm/${platform}/$scmVersion/scm ${STORAGE_DIR_LOCATION}/harness-download/harness-scm/release/$scmVersion/bin/${platform}/amd64/
    done
  done
}

setupDelegateJars
setupClientUtils

ls -ltr

ls -la $STORAGE_DIR_LOCATION

echo "uninstalling gsutil"
microdnf remove google-cloud-cli
