# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

if [ "$1" == "" ]; then
  echo Missing namespace
  exit 1
fi

echo "Using cluster: \c"
if ! kubectl config current-context
then
  exit
fi

SERVICE=idp-service
NAMESPACE=$1
DEPLOY_FILE=/tmp/$SERVICE.yaml

kubectl get deploy $SERVICE --namespace $NAMESPACE -o yaml > $DEPLOY_FILE
kubectl get pod --namespace $NAMESPACE -l app=$SERVICE -o yaml > /tmp/$SERVICE-pod.yml
POD_NAME=$(yq ".items[0].metadata.name" /tmp/$SERVICE-pod.yml)
lsof -ti tcp:7890 | xargs kill | kubectl --namespace $NAMESPACE port-forward $POD_NAME 7890:5005 &
kubectl exec -it --namespace $NAMESPACE $POD_NAME -- /opt/harness/run.sh