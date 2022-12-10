<#import "common/delegate-environment.ftl" as delegateEnvironment>
# Be sure you have kubectl installed and credentials to access your
# kubernetes cluster.
#
# Edit harness-delegate.yaml to change namespace, set proxy settings, or to
# enter a delegate description.
#
# Install or replace the Harness Delegate:
#
#   kubectl apply -f harness-delegate.yaml
#
# Get pod names:
#
#   kubectl get pods -n harness-delegate
#
# See startup logs:
#
#   kubectl logs <pod-name> -n harness-delegate -f
#
# Run a shell in a pod:
#
#    kubectl exec <pod-name> -n harness-delegate -it -- bash
#
# Note: If you're installing more than one Kubernetes delegate then make sure the
# name is unique, keeping the 6 letter account identifier as part of the name. You
# can download again with a new name from the Harness > Setup > Installations
# page.



apiVersion: v1
kind: Namespace
metadata:
  name: harness-delegate

---

apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: ce-clusterrole
rules:
- apiGroups:
  - ""
  resources:
  - pods
  - nodes
  - nodes/proxy
  - events
  - namespaces
  - persistentvolumes
  - persistentvolumeclaims
  verbs:
  - get
  - list
  - watch
- apiGroups:
  - apps
  - extensions
  resources:
  - statefulsets
  - deployments
  - daemonsets
  - replicasets
  verbs:
  - get
  - list
  - watch
- apiGroups:
  - batch
  resources:
  - jobs
  - cronjobs
  verbs:
  - get
  - list
  - watch
- apiGroups:
  - metrics.k8s.io
  resources:
  - pods
  - nodes
  verbs:
  - get
  - list
- apiGroups:
  - storage.k8s.io
  resources:
  - storageclasses
  verbs:
  - get
  - list
  - watch

---

apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: ce-clusterrolebinding
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: ce-clusterrole
subjects:
- kind: ServiceAccount
  name: default
  namespace: harness-delegate

---

apiVersion: v1
kind: Secret
metadata:
  name: ${delegateName}-proxy
  namespace: harness-delegate
type: Opaque
data:
  # Enter base64 encoded username and password, if needed
  PROXY_USER: ""
  PROXY_PASSWORD: ""

---

apiVersion: apps/v1
kind: StatefulSet
metadata:
  labels:
    harness.io/app: harness-delegate
    harness.io/account: ${kubernetesAccountLabel}
    harness.io/name: ${delegateName}
  # Name must contain the six letter account identifier: ${kubernetesAccountLabel}
  name: ${delegateName}-${kubernetesAccountLabel}
  namespace: harness-delegate
spec:
  replicas: 1
  selector:
    matchLabels:
      harness.io/app: harness-delegate
      harness.io/account: ${kubernetesAccountLabel}
      harness.io/name: ${delegateName}
  serviceName: ""
  template:
    metadata:
      labels:
        harness.io/app: harness-delegate
        harness.io/account: ${kubernetesAccountLabel}
        harness.io/name: ${delegateName}
    spec:
      containers:
      - image: ${delegateDockerImage}
        imagePullPolicy: Always
        name: harness-delegate-instance
        resources:
          limits:
            memory: "4Gi"
          requests:
            cpu: "${delegateCpu}"
        readinessProbe:
          exec:
            command:
              - test
              - -s
              - delegate.log
          initialDelaySeconds: 20
          periodSeconds: 10
        livenessProbe:
          exec:
            command:
              - bash
              - -c
              - '[[ -e /opt/harness-delegate/msg/data/watcher-data && $(($(date +%s000) - $(grep heartbeat /opt/harness-delegate/msg/data/watcher-data | cut -d ":" -f 2 | cut -d "," -f 1))) -lt 300000 ]]'
          initialDelaySeconds: 240
          periodSeconds: 10
          failureThreshold: 2
        env:
<@delegateEnvironment.common />
<@delegateEnvironment.cgSpecific />
<#if isImmutable == "true">
    <@delegateEnvironment.immutable />
<#else>
    <@delegateEnvironment.mutable />
</#if>
      restartPolicy: Always
