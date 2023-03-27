#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

source ./utils.sh

INSTALLER_DIR=harness_installer
MANAGER_DIR=$INSTALLER_DIR/manager
VERIFICATION_DIR=$INSTALLER_DIR/verification
UI_DIR=$INSTALLER_DIR/ui
LE_DIR=$INSTALLER_DIR/le
STORAGE_DIR=$INSTALLER_DIR/storage

rm -rf $INSTALLER_DIR

mkdir -p $INSTALLER_DIR

mkdir $MANAGER_DIR
mkdir $VERIFICATION_DIR
mkdir $UI_DIR
mkdir $STORAGE_DIR
mkdir $LE_DIR

cp -R scripts $INSTALLER_DIR/
cp -R config $INSTALLER_DIR/
cp *.properties $INSTALLER_DIR
cp install_harness.sh $INSTALLER_DIR
cp utils.sh $INSTALLER_DIR
cp stop.sh $INSTALLER_DIR
chmod +x $INSTALLER_DIR/*.sh
CONFIG_PROPERTIES_FILE=$INSTALLER_DIR/config.properties
cp -R splunk_pyml $LE_DIR/

if [[ -z $1 ]]; then
  echo "No license file supplied, skipping setting the license file in the installer"
else
  echo "License file supplied, generating installer with license file $1"
  replace harness_license $1 ${CONFIG_PROPERTIES_FILE} $INSTALLER_DIR
fi

JRE_SOURCE_URL_1=https://app.harness.io/storage/wingsdelegates/jre/8u191
JRE_SOLARIS_1=jre-8u191-solaris-x64.tar.gz
JRE_MACOSX_1=jre-8u191-macosx-x64.tar.gz
JRE_LINUX_1=jre-8u191-linux-x64.tar.gz
JRE_LINUX_DIR_1=jre1.8.0_191/

JRE_SOURCE_URL_2=https://app.harness.io/storage/wingsdelegates/jre/openjdk-8u242
JRE_SOLARIS_2=jre_x64_solaris_8u242b08.tar.gz
JRE_MACOSX_2=jre_x64_macosx_8u242b08.tar.gz
JRE_LINUX_2=jre_x64_linux_8u242b08.tar.gz
JRE_LINUX_DIR_2=jdk8u242-b08-jre/

curl "${JRE_SOURCE_URL_1}/${JRE_SOLARIS_1}" >"${JRE_SOLARIS_1}"
curl "${JRE_SOURCE_URL_1}/${JRE_MACOSX_1}" >"${JRE_MACOSX_1}"
curl "${JRE_SOURCE_URL_1}/${JRE_LINUX_1}" >"${JRE_LINUX_1}"

curl "${JRE_SOURCE_URL_2}/${JRE_SOLARIS_2}" >"${JRE_SOLARIS_2}"
curl "${JRE_SOURCE_URL_2}/${JRE_MACOSX_2}" >"${JRE_MACOSX_2}"
curl "${JRE_SOURCE_URL_2}/${JRE_LINUX_2}" >"${JRE_LINUX_2}"

function setupDelegateJars() {
  echo "################################Setting up Delegate Jars ################################"

  DELEGATE_VERSION=$(getProperty "version.properties" "DELEGATE_VERSION")
  WATCHER_VERSION=$(getProperty "version.properties" "WATCHER_VERSION")

  mkdir -p $STORAGE_DIR/wingsdelegates/jre/8u191/
  mkdir -p $STORAGE_DIR/wingsdelegates/jre/openjdk-8u242/

  cp jre-8u191-solaris-x64.tar.gz $STORAGE_DIR/wingsdelegates/jre/8u191/
  cp jre-8u191-macosx-x64.tar.gz $STORAGE_DIR/wingsdelegates/jre/8u191/
  cp jre-8u191-linux-x64.tar.gz $STORAGE_DIR/wingsdelegates/jre/8u191/

  cp ${JRE_SOLARIS_2} $STORAGE_DIR/wingsdelegates/jre/openjdk-8u242/
  cp ${JRE_MACOSX_2} $STORAGE_DIR/wingsdelegates/jre/openjdk-8u242/
  cp ${JRE_LINUX_2} $STORAGE_DIR/wingsdelegates/jre/openjdk-8u242/

  rm -rf ${STORAGE_DIR}/wingsdelegates/jobs/deploy-prod-delegate/*
  mkdir -p ${STORAGE_DIR}/wingsdelegates/jobs/deploy-prod-delegate/${DELEGATE_VERSION}
  cp delegate.jar ${STORAGE_DIR}/wingsdelegates/jobs/deploy-prod-delegate/${DELEGATE_VERSION}/

  echo "1.0.${DELEGATE_VERSION} jobs/deploy-prod-delegate/${DELEGATE_VERSION}/delegate.jar" >delegateprod.txt

  mv delegateprod.txt ${STORAGE_DIR}/wingsdelegates

  rm -rf ${STORAGE_DIR}/wingswatchers/jobs/deploy-prod-watcher/*
  mkdir -p ${STORAGE_DIR}/wingswatchers/jobs/deploy-prod-watcher/${WATCHER_VERSION}
  cp watcher.jar ${STORAGE_DIR}/wingswatchers/jobs/deploy-prod-watcher/${WATCHER_VERSION}/
  echo "1.0.${WATCHER_VERSION} jobs/deploy-prod-watcher/${WATCHER_VERSION}/watcher.jar" >watcherprod.txt
  mv watcherprod.txt ${STORAGE_DIR}/wingswatchers

  for platform in linux darwin; do

    for version in v1.13.2 v1.19.2; do

      echo "Copying kubectl ${version} binaries for ${platform}"

      sudo mkdir -p ${STORAGE_DIR}/harness-download/kubernetes-release/release/${version}/bin/${platform}/amd64/

      curl -s -L -o kubectl https://app.harness.io/storage/harness-download/kubernetes-release/release/${version}/bin/${platform}/amd64/kubectl

      echo $(ls -sh kubectl | cut -d ' ' -f1)

      sudo cp kubectl ${STORAGE_DIR}/harness-download/kubernetes-release/release/${version}/bin/${platform}/amd64/

    done

    for version in v0.2 v0.3 v0.4; do

      echo "Copying go-template  ${version} binaries for ${platform}"

      sudo mkdir -p ${STORAGE_DIR}/harness-download/snapshot-go-template/release/${version}/bin/${platform}/amd64/

      curl -s -L -o go-template https://app.harness.io/storage/harness-download/snapshot-go-template/release/${version}/bin/${platform}/amd64/go-template

      echo $(ls -sh go-template | cut -d ' ' -f1)

      sudo cp go-template ${STORAGE_DIR}/harness-download/snapshot-go-template/release/${version}/bin/${platform}/amd64/

    done

    for version in v0.1-dev v0.2-dev v0.3-dev v0.4-dev; do

      echo "Copying harness-pywinrm  ${version} binaries for ${platform}"

      sudo mkdir -p ${STORAGE_DIR}/harness-download/snapshot-harness-pywinrm/release/${version}/bin/${platform}/amd64/

      curl -s -L -o harness-pywinrm https://app.harness.io/storage/harness-download/snapshot-harness-pywinrm/release/${version}/bin/${platform}/amd64/harness-pywinrm

      echo $(ls -sh harness-pywinrm | cut -d ' ' -f1)

      sudo cp harness-pywinrm ${STORAGE_DIR}/harness-download/snapshot-harness-pywinrm/release/${version}/bin/${platform}/amd64/

    done

    for version in v2.13.1 v3.1.2 v3.8.0; do

      echo "Copying helm ${version} binaries for ${platform}"

      sudo mkdir -p ${STORAGE_DIR}/harness-download/harness-helm/release/${version}/bin/${platform}/amd64/

      curl -s -L -o helm https://app.harness.io/storage/harness-download/harness-helm/release/${version}/bin/${platform}/amd64/helm

      echo $(ls -sh helm | cut -d ' ' -f1)

      sudo cp helm ${STORAGE_DIR}/harness-download/harness-helm/release/${version}/bin/${platform}/amd64/

    done

    for version in v0.8.2 v0.12.0; do

      echo "Copying chartmuseum ${version} binaries for ${platform}"

      sudo mkdir -p ${STORAGE_DIR}/harness-download/harness-chartmuseum/release/${version}/bin/${platform}/amd64/

      curl -s -L -o chartmuseum https://app.harness.io/storage/harness-download/harness-chartmuseum/release/${version}/bin/${platform}/amd64/chartmuseum

      echo $(ls -sh chartmuseum | cut -d ' ' -f1)

      sudo cp chartmuseum ${STORAGE_DIR}/harness-download/harness-chartmuseum/release/${version}/bin/${platform}/amd64/

    done

    for version in v3.5.4 v4.0.0; do

      echo "Copying kustomize ${version} binaries for ${platform}"

      sudo mkdir -p ${STORAGE_DIR}/harness-download/harness-kustomize/release/${version}/bin/${platform}/amd64/

      curl -s -L -o kustomize https://app.harness.io/storage/harness-download/harness-kustomize/release/${version}/bin/${platform}/amd64/kustomize

      echo $(ls -sh kustomize | cut -d ' ' -f1)

      sudo cp kustomize ${STORAGE_DIR}/harness-download/harness-kustomize/release/${version}/bin/${platform}/amd64/

    done

    for version in v1.0 v1.1 v1.2; do

      echo "Copying terraform-config-inspect v${version} binaries for ${platform}"

      sudo mkdir -p ${STORAGE_DIR}/harness-download/harness-terraform-config-inspect/${version}/${platform}/amd64/

      curl -s -L -o terraform-config-inspect https://app.harness.io/storage/harness-download/harness-terraform-config-inspect/${version}/${platform}/amd64/terraform-config-inspect

      echo $(ls -sh terraform-config-inspect | cut -d ' ' -f1)

      sudo cp terraform-config-inspect ${STORAGE_DIR}/harness-download/harness-terraform-config-inspect/${version}/${platform}/amd64/

    done

    for version in v4.2.16; do

      echo "Copying oc ${version} binaries for ${platform}"

      sudo mkdir -p ${STORAGE_DIR}/harness-download/harness-oc/release/${version}/bin/${platform}/amd64/

      curl -s -L -o oc https://app.harness.io/storage/harness-download/harness-oc/release/${version}/bin/${platform}/amd64/oc

      echo $(ls -sh oc | cut -d ' ' -f1)

      sudo cp oc ${STORAGE_DIR}/harness-download/harness-oc/release/${version}/bin/${platform}/amd64/

    done

    for version in 91e2a39c; do

      echo "Copying scm ${version} binaries for ${platform}"

      sudo mkdir -p ${STORAGE_DIR}/harness-download/harness-scm/release/${version}/bin/${platform}/amd64/

      curl -s -L -o scm https://app.harness.io/storage/harness-download/harness-scm/release/${version}/bin/${platform}/amd64/scm

      echo $(ls -sh scm | cut -d ' ' -f1)

      sudo cp scm ${STORAGE_DIR}/harness-download/harness-scm/release/${version}/bin/${platform}/amd64/

    done

  done
}

function setUpArtifacts() {
  echo "################################ Preparing artifacts in their respective folders  ################################"
  cp rest-capsule.jar $MANAGER_DIR
  cp config.yml $MANAGER_DIR

  cp verification-capsule.jar $VERIFICATION_DIR
  cp verification-config.yml $VERIFICATION_DIR

  cp -R static $UI_DIR/

  setupDelegateJars
}

function setUpJRE() {
  cp $JRE_LINUX_1 $INSTALLER_DIR
  cd $INSTALLER_DIR
  tar -xvf $JRE_LINUX_1
  rm -f $JRE_LINUX_1
  ln -s $JRE_LINUX_DIR_1 jre
  cd -
}

setUpJRE
setUpArtifacts
tar -cvzf harness_installer.tar.gz $INSTALLER_DIR
