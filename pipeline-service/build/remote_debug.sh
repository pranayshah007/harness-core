# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

kubectl config use-context gke_pie-play_us-central1-c_pie-play-cluster-pr

SERVICE=pipeline-service
NAMESPACE=$1
DEPLOY_FILE=/tmp/$SERVICE.yaml

kubectl get deploy $SERVICE --namespace $NAMESPACE -o yaml > $DEPLOY_FILE
yq $DEPLOY_FILE > $DEPLOY_FILE.swap
yq -i '.spec.template.spec.containers[0].command |= ["/bin/sh", "-ec", "sleep infinity"]' $DEPLOY_FILE

yq -i 'del(.spec.template.spec.containers[0].livenessProbe.httpGet)' $DEPLOY_FILE
yq -i '.spec.template.spec.containers[0].livenessProbe.exec.command |= ["ls"]' $DEPLOY_FILE
yq -i 'del(.spec.template.spec.containers[0].readinessProbe.httpGet)' $DEPLOY_FILE
yq -i '.spec.template.spec.containers[0].readinessProbe.exec.command |= ["ls"]' $DEPLOY_FILE

# TODO: Add environment variable to deployment to add debug flags
# TODO: Add a dirty label to the deployment so it is easy to rollback to the last clean state

diff $DEPLOY_FILE $DEPLOY_FILE.swap
diff_detected=$?
if [ $diff_detected != 0 ]
then
    kubectl apply -f $DEPLOY_FILE
fi
kubectl get pod --namespace $NAMESPACE -l app=$SERVICE -o yaml > /tmp/$SERVICE-pod.yml
POD_NAME=$(yq ".items[0].metadata.name" /tmp/$SERVICE-pod.yml)
sh ./pipeline-service/build/build_jar.sh
kubectl cp ~/.bazel-dirs/bin/pipeline-service/service/module_deploy.jar $NAMESPACE/$POD_NAME:/opt/harness/pipeline-service-capsule.jar
lsof -ti tcp:7890 | xargs kill | kubectl --namespace $NAMESPACE port-forward $POD_NAME 7890:5005 &
kubectl exec -it --namespace $NAMESPACE $POD_NAME -- /opt/harness/run.sh
