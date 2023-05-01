<#import "common/delegate-environment.ftl" as delegateEnvironment>
<#import "common/delegate-role.ftl" as delegateRole>
<#import "common/delegate-service.ftl" as delegateService>
<#import "common/mtls.ftl" as mtls>
<#import "common/upgrader.ftl" as upgrader>
<#import "common/secret.ftl" as secret>
<#global accountTokenName=delegateName + "-account-token">
apiVersion: v1
kind: Namespace
metadata:
  name: ${delegateNamespace}

---

<#if enableCE == "true">
    <@delegateRole.cgCe />
<#else>
    <@delegateRole.cg />
</#if>

---

<@secret.accountToken base64Secret/>

---
<#if mtlsEnabled == "true">

   <@mtls.secret fullDelegateName=delegateName + "-" + kubernetesAccountLabel />

---
</#if>

# If delegate needs to use a proxy, please follow instructions available in the documentation
# https://docs.harness.io/article/pfim3oig7o-configure-delegate-proxy-settings

apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    harness.io/name: ${delegateName}
  # Name must contain the six letter account identifier: ${kubernetesAccountLabel}
  name: ${delegateName}-${kubernetesAccountLabel}
  namespace: ${delegateNamespace}
spec:
  replicas: 1
  minReadySeconds: 120
  selector:
    matchLabels:
      harness.io/name: ${delegateName}
  template:
    metadata:
      labels:
        harness.io/name: ${delegateName}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "3460"
        prometheus.io/path: "/api/metrics"
    spec:
      terminationGracePeriodSeconds: 600
      restartPolicy: Always
      containers:
      - image: ${delegateDockerImage}
        imagePullPolicy: Always
        name: delegate
        <#if runAsRoot == "true">
        securityContext:
          allowPrivilegeEscalation: false
          runAsUser: 0
        <#else>
        #uncomment below lines to run delegate as root.
        #securityContext:
        #  allowPrivilegeEscalation: false
        #  runAsUser: 0
        </#if>
        <#if ciEnabled == "true">
        ports:
          - containerPort: ${delegateGrpcServicePort}
        </#if>
        resources:
          limits:
            memory: "${delegateRam}Gi"
          requests:
            cpu: "${delegateCpu}"
        livenessProbe:
          httpGet:
            path: /api/health
            port: 3460
            scheme: HTTP
          initialDelaySeconds: 10
          periodSeconds: 10
          failureThreshold: 2
        startupProbe:
          httpGet:
            path: /api/health
            port: 3460
            scheme: HTTP
          initialDelaySeconds: 30
          periodSeconds: 10
          failureThreshold: 15
        envFrom:
        - secretRef:
            name: ${accountTokenName}
        env:
        <@delegateEnvironment.common />
        <@delegateEnvironment.cgSpecific />
        <@delegateEnvironment.cgImmutableSpecific />
        <@delegateEnvironment.immutable />
<#if mtlsEnabled == "true">
        <@mtls.delegateEnv />
        volumeMounts:
        <@mtls.delegateVolumeMount />
      volumes:
      <@mtls.delegateVolume fullDelegateName=delegateName + "-" + kubernetesAccountLabel />
</#if>

<#if ciEnabled == "true">
---

    <@delegateService.ng />
</#if>

---

<@upgrader.cronjob base64Secret=base64Secret fullDelegateName=delegateName + "-" + kubernetesAccountLabel />
