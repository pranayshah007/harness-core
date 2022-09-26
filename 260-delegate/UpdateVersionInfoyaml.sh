# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -x

if [[ "$BUILD_TAG" == *pr-portal-* || "${PLATFORM}" != jenkins ]]
then
  exit 0
fi

function getProperty () {
   FILENAME=$1
   PROP_KEY=$2
   PROP_VALUE=`head -4 "$FILENAME" | grep "$PROP_KEY" | cut -d'=' -f2`
   echo $PROP_VALUE
}

buildNo=$1
buildPropertiesFile=${2:-build.properties}
buildMajorVersion=$(date +%y)
buildMinorVersion=$(date +%m)
patch=$(getProperty "${buildPropertiesFile}" "build.patch")
timestamp=$( date +'%y%m%d-%H%M')

echo "----------------------------------------------------------------------------"
echo $buildNo $GIT_COMMIT $GIT_BRANCH $timestamp $patch $buildMajorVersion $buildMinorVersion
echo "----------------------------------------------------------------------------"

sed -i.bak "s|\${build.number}|${buildNo}|g"  "980-commons/src/main/resources-filtered/versionInfo.yaml"
sed -i.bak "s|\${gitCommitId}|${GIT_COMMIT}|g"  "980-commons/src/main/resources-filtered/versionInfo.yaml"
sed -i.bak "s|\${gitBranch}|${GIT_BRANCH}|g"  "980-commons/src/main/resources-filtered/versionInfo.yaml"
sed -i.bak "s|\${buildTimeStamp}|${timestamp}|g"  "980-commons/src/main/resources-filtered/versionInfo.yaml"
sed -i.bak "s|\${build.patch}|${patch}|g"  "980-commons/src/main/resources-filtered/versionInfo.yaml"
sed -i.bak "s|\${build.fullVersionWithPatch}|${buildMajorVersion}.${buildMinorVersion}.${buildNo}-${patch}|g"  "980-commons/src/main/resources-filtered/versionInfo.yaml"

sed -i.bak "s|\${build.fullVersion}|${buildMajorVersion}.${buildMinorVersion}.${buildNo}|g"  "980-commons/src/main/resources-filtered/versionInfo.yaml"
