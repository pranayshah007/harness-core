#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.

# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function read_inputs() {
   SERVICE=$1
   CONFIG_PATH=$2
   [ -z "$SERVICE" ] || [ -z "$CONFIG_PATH" ] && echo "Invalid input" && exit 1
   return $#
}

function check_context() {
  echo "Using cluster: \c"
    if ! kubectl config current-context
    then
      exit
    fi
}
function request_variables() {
  EXPECTED_SERVICE=$(yq ."$SERVICE".SERVICE "$CONFIG_PATH"/config.yaml)
  [ "$EXPECTED_SERVICE" != "$SERVICE" ]  && echo "Input service name is invalid" && exit 1

  NAMESPACE=$(yq ."$SERVICE".NAMESPACE "$CONFIG_PATH"/config.yaml)
  SERVICE=$(yq ."$SERVICE".SERVICE "$CONFIG_PATH"/config.yaml)
  HOSTPORT=$(yq ."$SERVICE".HOSTPORT "$CONFIG_PATH"/config.yaml)
  TARGETPORT=$(yq ."$SERVICE".TARGETPORT "$CONFIG_PATH"/config.yaml)
  MODULE=$(yq ."$SERVICE".MODULE "$CONFIG_PATH"/config.yaml)
  SERVICE_CAPSULE=$(yq ."$SERVICE".SERVICE_CAPSULE "$CONFIG_PATH"/config.yaml)
  SCRIPT_PATH=$(yq ."$SERVICE".SCRIPT_PATH "$CONFIG_PATH"/config.yaml)

  [ -z "$NAMESPACE" ] || [ -z "$HOSTPORT" ] || [ -z "$TARGETPORT" ] || [ -z "$MODULE" ] || [ -z "$SERVICE_CAPSULE" ] || [ -z "$SCRIPT_PATH" ] && echo "Either one of the vars in config.yaml is empty" && exit 1

}

function main() {
  read_inputs "$1" "$2"
  request_variables
  check_context

  DEPLOY_FILE=/tmp/$SERVICE.yaml

  kubectl get deploy "$SERVICE" --namespace "$NAMESPACE" -o yaml > "$DEPLOY_FILE"
  yq "$DEPLOY_FILE" > "$DEPLOY_FILE".swap
  yq -i '.spec.template.spec.containers[0].command |= ["/bin/sh", "-ec", "sleep infinity"]' "$DEPLOY_FILE"

  ## CHANGES TO DISABLE HEALTH CHECK
  yq -i 'del(.spec.template.spec.containers[0].livenessProbe.httpGet)' "$DEPLOY_FILE"
  yq -i '.spec.template.spec.containers[0].livenessProbe.exec.command |= ["ls"]' "$DEPLOY_FILE"
  yq -i 'del(.spec.template.spec.containers[0].readinessProbe.httpGet)' "$DEPLOY_FILE"
  yq -i '.spec.template.spec.containers[0].readinessProbe.exec.command |= ["ls"]' "$DEPLOY_FILE"

  ## ENV VARIABLE TO ENABLE REMOTE DEBUG INSIDE THE CONTAINER
  yq -i '.spec.template.spec.containers[0].env |= [{"name": "ENABLE_REMOTE_DEBUG","value": "true"}]' "$DEPLOY_FILE"

  # TODO: Add a dirty label to the deployment so it is easy to rollback to the last clean state

  ## ONLY APPLY CHANGES IF NEEDED
  if ! diff "$DEPLOY_FILE" "$DEPLOY_FILE".swap
  then
    kubectl apply -f "$DEPLOY_FILE"
  fi

  kubectl get pod --namespace "$NAMESPACE" -l app="$SERVICE" -o yaml > /tmp/"$SERVICE"-pod.yml
  POD_NAME=$(yq ".items[0].metadata.name" /tmp/"$SERVICE"-pod.yml)

  ## BUILD
  echo "Building"
  cd ../../ || exit
  sh "$SCRIPT_PATH"/build_jar.sh

  ## DEPLOY
  echo "Deploying (it really takes time)"
  kubectl cp "$MODULE" "$NAMESPACE"/"$POD_NAME":"$SERVICE"-CAPSULE
  lsof -ti tcp:"$HOSTPORT" | xargs kill | kubectl --namespace "$NAMESPACE" port-forward "$POD_NAME" "$HOSTPORT":"$TARGETPORT" &

  ## RUN
  kubectl exec -it --namespace "$NAMESPACE" "$POD_NAME" -- /opt/harness/run.sh
}
