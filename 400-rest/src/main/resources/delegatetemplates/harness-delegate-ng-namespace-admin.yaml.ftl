<#import "common/delegate-environment.ftl" as delegateEnvironment>
<#import "common/delegate-service.ftl" as delegateService>
apiVersion: v1
kind: Namespace
metadata:
  name: ${delegateNamespace}

---

apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: harness-namespace-admin
  namespace: ${delegateNamespace}
rules:
  - apiGroups:
    - '*'
    resources:
    - '*'
    verbs:
    - '*'

---

apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: ${delegateNamespace}-harness-namespace-admin
  namespace: ${delegateNamespace}
subjects:
  - kind: ServiceAccount
    name: default
    namespace: ${delegateNamespace}
roleRef:
  kind: Role
  name: harness-namespace-admin
  apiGroup: rbac.authorization.k8s.io

---

apiVersion: v1
kind: Secret
metadata:
  name: ${delegateName}-proxy
  namespace: ${delegateNamespace}
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
    harness.io/name: ${delegateName}
  name: ${delegateName}
  namespace: ${delegateNamespace}
spec:
  replicas: ${delegateReplicas}
  podManagementPolicy: Parallel
  selector:
    matchLabels:
      harness.io/name: ${delegateName}
  serviceName: ""
  template:
    metadata:
      labels:
        harness.io/name: ${delegateName}
    spec:
      containers:
      - image: ${delegateDockerImage}
        imagePullPolicy: Always
        name: harness-delegate-instance
        <#if ciEnabled == "true">
        ports:
          - containerPort: ${delegateGrpcServicePort}
        </#if>
        resources:
          limits:
            memory: "${delegateRam}Mi"
          requests:
            cpu: "${delegateRequestsCpu}"
            memory: "${delegateRequestsRam}Mi"
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
<@delegateEnvironment.ngSpecific />
<@delegateEnvironment.mutable />
      restartPolicy: Always

<#if ciEnabled == "true">
---

    <@delegateService.ng />
</#if>