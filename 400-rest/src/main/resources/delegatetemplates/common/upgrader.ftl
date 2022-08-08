<#import "upgrader-role.ftl" as upgraderRole>
<#import "mtls.ftl" as mtls>
<#macro cronjob base64Secret fullDelegateName=delegateName>
<#assign upgraderSaName = "upgrader-cronjob-sa">
<@upgraderRole.cronJobRole upgraderSaName />

---

apiVersion: v1
kind: Secret
metadata:
  name: ${fullDelegateName}-upgrader-token
  namespace: ${delegateNamespace}
type: Opaque
data:
  UPGRADER_TOKEN: "${base64Secret}"

---

apiVersion: v1
kind: ConfigMap
metadata:
  name: ${fullDelegateName}-upgrader-config
  namespace: ${delegateNamespace}
data:
  config.yaml: |
    mode: Delegate
    dryRun: false
    workloadName: ${fullDelegateName}
    namespace: ${delegateNamespace}
    containerName: delegate
<#if mtlsEnabled == "true">
    <@mtls.upgraderCfg />
</#if>
    delegateConfig:
      accountId: ${accountId}
      managerHost: ${managerHostAndPort}

---

apiVersion: batch/v1beta1
kind: CronJob
metadata:
  labels:
    harness.io/name: ${fullDelegateName}-upgrader-job
  name: ${fullDelegateName}-upgrader-job
  namespace: ${delegateNamespace}
spec:
  schedule: "0 */1 * * *"
  concurrencyPolicy: Forbid
  startingDeadlineSeconds: 20
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: ${upgraderSaName}
          restartPolicy: Never
          containers:
          - image: ${upgraderDockerImage}
            name: upgrader
            imagePullPolicy: Always
            envFrom:
            - secretRef:
                name: ${fullDelegateName}-upgrader-token
            volumeMounts:
              - name: config-volume
                mountPath: /etc/config
<#if mtlsEnabled == "true">
              <@mtls.upgraderVolumeMount />
</#if>
          volumes:
            - name: config-volume
              configMap:
                name: ${fullDelegateName}-upgrader-config
<#if mtlsEnabled == "true">
            <@mtls.upgraderVolume fullDelegateName=fullDelegateName />
</#if>
</#macro>