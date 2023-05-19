# Copyright 2020 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.



export VERSION=`cat destination/dist/${SERVICE}/version.txt`
export JDK=`cat destination/dist/${SERVICE}/jdk.txt 2>/dev/null`


export PURPOSE=`cat destination/dist/${SERVICE}/purpose.txt 2>/dev/null`
if [ ! -z "${PURPOSE}" ]
then
    export PURPOSE=/${PURPOSE}-${JDK}
    export SERVICEDIR=${SERVICE}
else
    export SERVICEDIR=${SERVICE}-${JDK}
fi

export IMAGE_TAG=`cat destination/dist/${SERVICE}/image_tag.txt 2>/dev/null`
if [ -z "${IMAGE_TAG}" ]
then
    export IMAGE_TAG=${VERSION}
fi

docker login -u _json_key --password-stdin https://us.gcr.io < $GCR_CREDENTIALS

export IMAGE_REPO="us.gcr.io/platform-205701/harness${PURPOSE}/${SNAPSHOT_PREFIX}${SERVICEDIR}:${IMAGE_TAG}"
docker build -t ${IMAGE_REPO} destination/dist/${SERVICE} -f destination/dist/${SERVICE}/Dockerfile-gcr
docker push ${IMAGE_REPO}

if [ "${PURPOSE}" = "/on-prem-${JDK}" ]
then
    docker login -u $DOCKER_USERNAME -p $DOCKER_PASSWORD

    docker build -t harness/${SERVICE}:${IMAGE_TAG} destination/dist/${SERVICE}
    docker push harness/${SERVICE}:${IMAGE_TAG}
fi


if [ "${PURPOSE}" = "/on-prem-${JDK}" ] && [ ${SIGNED_REPO} = true ]
then
    echo "Singed Repo - On Prem"
else
    exit 0
fi

# docker signing flow

echo ${HARNESS_SIGNING_KEY}  | base64 -di > key.pem

chmod 600 key.pem

(cat <<END
${HARNESS_SIGNING_KEY_PASSPHRASE}
${HARNESS_SIGNING_KEY_PASSPHRASE}
END
) |  docker trust key load key.pem --name harness

docker pull harness/${SERVICE}:${IMAGE_TAG}
docker tag harness/${SERVICE}:${IMAGE_TAG} harness/${SERVICE}-signed:${IMAGE_TAG}
(cat <<END
$HARNESS_SIGNING_KEY_PASSPHRASE
$HARNESS_SIGNING_KEY_PASSPHRASE
END
) | docker trust sign harness/${SERVICE}-signed:${IMAGE_TAG}

rm key.pem
rm  ~/.docker/trust/private/ae13b87*
