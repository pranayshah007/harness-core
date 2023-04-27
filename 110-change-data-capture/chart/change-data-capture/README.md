# change-data-capture

![Version: 0.3.1](https://img.shields.io/badge/Version-0.3.1-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.0.1](https://img.shields.io/badge/AppVersion-0.0.1-informational?style=flat-square)

A Helm chart for Kubernetes

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| https://harness.github.io/helm-common | harness-common | 1.x.x |

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| affinity | object | `{}` | affinity Affinity for pod assignment. Evaluated as a template. # ref: https://kubernetes.io/docs/concepts/configuration/assign-pod-node/#affinity-and-anti-affinity # Note: podAffinityPreset, podAntiAffinityPreset, and nodeAffinityPreset will be ignored when it's set # |
| autoscaling | object | `{"enabled":false,"maxReplicas":100,"minReplicas":1,"targetCPUUtilizationPercentage":80}` | autoscaling.targetMemory Target Memory utilization percentage # |
| config | object | `{"DEPLOY_MODE":"KUBERNETES","MEMORY":2048,"MONGO_TAG_NAME":"none","MONGO_TAG_VALUE":"none"}` | Configurations for Harness application |
| containerSecurityContext | object | `{"runAsNonRoot":true,"runAsUser":65534}` | containerSecurityContext # ref: https://kubernetes.io/docs/tasks/configure-pod-container/security-context/#set-the-security-context-for-a-container # @param provisioning.containerSecurityContext.enabled Enable container Security Context # @param provisioning.containerSecurityContext.runAsUser User ID for the container # @param provisioning.containerSecurityContext.runAsNonRoot Avoid running as root User # |
| extraEnvVars | list | `[]` | extraEnvVars Extra environment variables to be set on container # e.g: # extraEnvVars: #   - name: FOO #     value: "bar" # |
| extraEnvVarsCM | string | `""` | extraEnvVarsCM ConfigMap with extra environment variables # |
| extraEnvVarsSecret | string | `""` | extraEnvVarsSecret Secret with extra environment variables # |
| extraVolumeMounts | list | `[]` | extraVolumeMounts Optionally specify extra list of additional volumeMounts for ; container(s) # e.g. extraVolumeMounts: - name: service-account   mountPath: /opt/harness/svc - name: stackdriver   mountPath: /opt/harness/monitoring - name: dumps   mountPath: /opt/harness/dumps |
| extraVolumes | list | `[]` | extraVolumes Optionally specify extra list of additional volumes for ; pods # e.g. extraVolumes: - name: service-account   secret:     secretName: redis-ca     items:     - key: redis-labs-ca-truststore       path: redis_labs_ca_truststore - name: stackdriver   secret:     secretName: stackdriver-creds     items:     - key: stackdriver-key-file       path: stackdriver.json - name: dumps   hostPath:     path: /var/dumps     type: DirectoryOrCreate |
| fullnameOverride | string | `""` | fullnameOverride String to fully override common.names.fullname template # |
| global | object | `{"database":{"mongo":{"extraArgs":"","hosts":[],"installed":true,"passwordKey":"","protocol":"mongodb","secretName":"","userKey":""},"postgres":{"extraArgs":"","hosts":["postgres:5432"],"installed":true,"passwordKey":"","protocol":"postgres","secretName":"","userKey":""},"timescaledb":{"extraArgs":"","hosts":["timescaledb-single-chart:5432"],"installed":true,"passwordKey":"","protocol":"jdbc:postgresql","secretName":"","userKey":""}},"ha":false,"imagePullSecrets":[],"loadbalancerURL":""}` | global.storageClass Global StorageClass for Persistent Volume(s) |
| global.database.mongo.hosts | list | `[]` | provide default values if mongo.installed is set to false |
| global.database.timescaledb.hosts | list | `["timescaledb-single-chart:5432"]` | provide default values if mongo.installed is set to false |
| image | object | `{"digest":"","imagePullSecrets":[],"pullPolicy":"IfNotPresent","registry":"docker.io","repository":"harness/cdcdata-signed","tag":"78900"}` | image.imagePullSecrets Specify docker-registry secret names as an array |
| initContainers | list | `[]` | initContainers Add additional init containers to the ; pods # e.g: # initContainers: #   - name: your-image-name #     image: your-image #     imagePullPolicy: Always #     ports: #       - name: portname #         containerPort: 1234 # |
| livenessProbe | object | `{"enabled":true,"failureThreshold":5,"initialDelaySeconds":60,"periodSeconds":2,"successThreshold":1,"timeoutSeconds":10}` | livenessProbe.successThreshold Success threshold for livenessProbe # |
| mongoSecrets.password.key | string | `"mongodb-root-password"` |  |
| mongoSecrets.password.name | string | `"mongodb-replicaset-chart"` |  |
| mongoSecrets.userName.key | string | `"mongodbUsername"` |  |
| mongoSecrets.userName.name | string | `"harness-secrets"` |  |
| nameOverride | string | `""` | nameOverride String to partially override common.names.fullname template (will maintain the release name) # |
| nodeSelector | object | `{}` | nodeSelector Node labels for pod assignment. Evaluated as a template. # ref: https://kubernetes.io/docs/user-guide/node-selection/ # |
| podAnnotations | object | `{}` | provisioning.podAnnotations Provisioning Pod annotations. # |
| podSecurityContext | object | `{}` |  |
| readinessProbe | object | `{"enabled":true,"failureThreshold":5,"initialDelaySeconds":60,"periodSeconds":2,"successThreshold":1,"timeoutSeconds":10}` | readinessProbe.successThreshold Success threshold for readinessProbe # |
| replicaCount | int | `1` | replicaCount Number of pods # |
| resources | object | `{"limits":{"cpu":1,"memory":"2880Mi"},"requests":{"cpu":1,"memory":"2880Mi"}}` | resources.requests The requested resources for the containers # |
| schedulerName | string | `""` | schedulerName Specifies the schedulerName, if it's nil uses kube-scheduler # https://kubernetes.io/docs/tasks/administer-cluster/configure-multiple-schedulers/ # |
| service.port | int | `8190` |  |
| service.type | string | `"ClusterIP"` | service type # |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `false` |  |
| serviceAccount.name | string | `"harness-default"` |  |
| sidecars | list | `[]` | sidecars Add additional sidecar containers to the ; pods # e.g: # sidecars: #   - name: your-image-name #     image: your-image #     imagePullPolicy: Always #     ports: #       - name: portname #         containerPort: 1234 # |
| startupProbe | object | `{"enabled":true,"failureThreshold":5,"initialDelaySeconds":0,"periodSeconds":2,"successThreshold":1,"timeoutSeconds":10}` | startupProbe.successThreshold Success threshold for startupProbe # |
| terminationGracePeriodSeconds | string | `"30"` | terminationGracePeriodSeconds In seconds, time the given to the pod needs to terminate gracefully # ref: https://kubernetes.io/docs/concepts/workloads/pods/pod/#termination-of-pods # |
| timescaleSecret.password.key | string | `"timescaledbPostgresPassword"` |  |
| timescaleSecret.password.name | string | `"harness-secrets"` |  |
| tolerations | list | `[]` | tolerations Tolerations for pod assignment. Evaluated as a template. # ref: https://kubernetes.io/docs/concepts/configuration/taint-and-toleration/ # |
| updateStrategy | object | `{"rollingUpdate":{"maxSurge":"25%","maxUnavailable":"25%"},"type":"RollingUpdate"}` | deployment.updateStrategy.type Deployment strategy type # ref: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#update-strategies # e.g: |
| waitForInitContainer | object | `{"containerSecurityContext":{"runAsNonRoot":true,"runAsUser":65534},"image":{"digest":"","imagePullSecrets":[],"pullPolicy":"IfNotPresent","registry":"docker.io","repository":"harness/helm-init-container","tag":"latest"},"resources":{"limits":{"cpu":"128m","memory":"128Mi"},"requests":{"cpu":"128m","memory":"128Mi"}}}` | Wait-For-App initContainers details |
| waitForInitContainer.image | object | `{"digest":"","imagePullSecrets":[],"pullPolicy":"IfNotPresent","registry":"docker.io","repository":"harness/helm-init-container","tag":"latest"}` | image.imagePullSecrets Specify docker-registry secret names as an array |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
