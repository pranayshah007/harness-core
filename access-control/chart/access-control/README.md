# access-control

![Version: 0.3.2](https://img.shields.io/badge/Version-0.3.2-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.0.1](https://img.shields.io/badge/AppVersion-0.0.1-informational?style=flat-square)

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
| config | object | `{"ACCESS_CONTROL_PREFERENCE_ENABLED":true,"ACCESS_CONTROL_SERVICE_BASE_URL":null,"ACCOUNT_CLIENT_BASE_URL":null,"AGGREGATOR_ENABLED":true,"AUDIT_CLIENT_BASE_URL":null,"DEPLOY_MODE":"KUBERNETES_ONPREM","DISTRIBUTED_LOCK_IMPLEMENTATION":"REDIS","ENABLE_ACCESS_CONTROL":false,"ENABLE_AUDIT":true,"ENABLE_AUTH":true,"EVENTS_CONFIG_REDIS_SENTINELS":null,"EVENTS_CONFIG_SENTINEL_MASTER_NAME":"harness-redis","EVENTS_CONFIG_USE_SENTINEL":true,"FEATURE_FLAG_CLIENT_BASE_URL":null,"GOOGLE_APPLICATION_CREDENTIALS":"/opt/harness/monitoring/stackdriver.json","LOCK_CONFIG_REDIS_SENTINELS":null,"LOCK_CONFIG_SENTINEL_MASTER_NAME":"harness-redis","LOCK_CONFIG_USE_SENTINEL":true,"LOGGING_LEVEL":"INFO","MEMORY":512,"MONGODB_SSL_ENABLED":false,"NOTIFICATION_ENVIRONMENT":"ONPREM","NOTIFICATION_SLACK_WEBHOOK_URL":null,"OFFSET_FLUSH_INTERVAL_MS":10000,"PROJECT_CLIENT_BASE_URL":null,"RESOURCE_GROUP_CLIENT_BASE_URL":null,"RESOURCE_GROUP_ITERATOR_ENABLED":true,"RESOURCE_GROUP_ITERATOR_INTERVAL":3600,"SCOPE_ITERATOR_ENABLED":true,"SCOPE_ITERATOR_INTERVAL":3600,"SERVICEACCOUNT_CLIENT_BASE_URL":null,"SERVICEACCOUNT_ITERATOR_ENABLED":true,"SERVICEACCOUNT_ITERATOR_INTERVAL":3600,"SUPPORTPREFERENCE_ITERATOR_ENABLED":true,"SUPPORTPREFERENCE_ITERATOR_INTERVAL":600,"USER_CLIENT_BASE_URL":null,"USER_GROUP_CLIENT_BASE_URL":null,"USER_GROUP_ITERATOR_ENABLED":true,"USER_GROUP_ITERATOR_INTERVAL":3600,"USER_ITERATOR_ENABLED":true,"USER_ITERATOR_INTERVAL":3600}` | Configurations for Harness application |
| containerSecurityContext.runAsNonRoot | bool | `true` |  |
| containerSecurityContext.runAsUser | int | `65534` |  |
| extraEnvVars | list | `[]` | extraEnvVars Extra environment variables to be set on container # e.g: # extraEnvVars: #   - name: FOO #     value: "bar" # |
| extraEnvVarsCM | string | `""` | extraEnvVarsCM ConfigMap with extra environment variables # |
| extraEnvVarsSecret | string | `""` | extraEnvVarsSecret Secret with extra environment variables # |
| extraVolumeMounts | list | `[]` | extraVolumeMounts Optionally specify extra list of additional volumeMounts for ; container(s) # e.g. extraVolumeMounts: - name: service-account   mountPath: /opt/harness/svc - name: stackdriver   mountPath: /opt/harness/monitoring - name: dumps   mountPath: /opt/harness/dumps |
| extraVolumes | list | `[]` | extraVolumes Optionally specify extra list of additional volumes for ; pods # e.g. extraVolumes: - name: service-account   secret:     secretName: redis-ca     items:     - key: redis-labs-ca-truststore       path: redis_labs_ca_truststore - name: stackdriver   secret:     secretName: stackdriver-creds     items:     - key: stackdriver-key-file       path: stackdriver.json - name: dumps   hostPath:     path: /var/dumps     type: DirectoryOrCreate |
| fullnameOverride | string | `""` | fullnameOverride String to fully override common.names.fullname template # |
| global | object | `{"database":{"mongo":{"extraArgs":"","hosts":[],"installed":true,"passwordKey":"","protocol":"mongodb","secretName":"","userKey":""},"postgres":{"extraArgs":"","hosts":["postgres:5432"],"installed":true,"passwordKey":"","protocol":"postgres","secretName":"","userKey":""},"timescaledb":{"extraArgs":"","hosts":["timescaledb-single-chart:5432"],"installed":true,"passwordKey":"","protocol":"jdbc:postgresql","secretName":"","userKey":""}},"ha":false,"imagePullSecrets":[],"ingress":{"className":"harness","enabled":false,"hosts":["my-host.example.org"],"objects":{"annotations":{}},"tls":{"enabled":true,"secretName":""}},"istio":{"enabled":false,"gateway":{"create":false},"virtualService":{"gateways":null,"hosts":null}}}` | global.storageClass Global StorageClass for Persistent Volume(s) |
| global.database.mongo.hosts | list | `[]` | provide default values if mongo.installed is set to false |
| global.database.timescaledb.hosts | list | `["timescaledb-single-chart:5432"]` | provide default values if mongo.installed is set to false |
| global.ingress.className | string | `"harness"` | set ingress object classname |
| global.ingress.enabled | bool | `false` | create ingress objects |
| global.ingress.hosts | list | `["my-host.example.org"]` | set host of ingressObjects |
| global.ingress.objects | object | `{"annotations":{}}` | add annotations to ingress objects |
| global.ingress.tls | object | `{"enabled":true,"secretName":""}` | set tls for ingress objects |
| global.istio.enabled | bool | `false` | create virtualServices objects |
| global.istio.gateway | object | `{"create":false}` | create gateway and use in virtualservice |
| global.istio.virtualService | object | `{"gateways":null,"hosts":null}` | if gateway not created, use specified gateway and host |
| image | object | `{"digest":"","imagePullSecrets":[],"pullPolicy":"IfNotPresent","registry":"docker.io","repository":"harness/accesscontrol-service-signed","tag":"78900"}` | image.imagePullSecrets Specify docker-registry secret names as an array |
| initContainers | list | `[]` | initContainers Add additional init containers to the ; pods # e.g: # initContainers: #   - name: your-image-name #     image: your-image #     imagePullPolicy: Always #     ports: #       - name: portname #         containerPort: 1234 # |
| lifecycleHooks | object | `{}` |  |
| livenessProbe | object | `{"enabled":true,"failureThreshold":null,"initialDelaySeconds":60,"periodSeconds":10,"successThreshold":1,"timeoutSeconds":10}` | livenessProbe.successThreshold Success threshold for livenessProbe # |
| mongoSecrets.password.key | string | `"mongodb-root-password"` |  |
| mongoSecrets.password.name | string | `"mongodb-replicaset-chart"` |  |
| mongoSecrets.userName.key | string | `"mongodbUsername"` |  |
| mongoSecrets.userName.name | string | `"harness-secrets"` |  |
| nameOverride | string | `""` | nameOverride String to partially override common.names.fullname template (will maintain the release name) # |
| nodeSelector | object | `{}` | nodeSelector Node labels for pod assignment. Evaluated as a template. # ref: https://kubernetes.io/docs/user-guide/node-selection/ # |
| pdb.create | bool | `true` | pdb.create Enable/disable a Pod Disruption Budget creation # |
| pdb.maxUnavailable | string | `""` | pdb.maxUnavailable Maximum number/percentage of pods that may be made unavailable after the eviction # |
| pdb.minAvailable | string | `"50%"` | pdb.minAvailable Minimum number/percentage of pods that must still be available after the eviction # |
| podAnnotations | object | `{}` |  |
| podSecurityContext | object | `{}` |  |
| progressDeadlineSeconds | int | `720` | set progressDealineSeconds in seconds, number of seconds the Deployment controller waits before indicating failure # ref: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/ |
| readinessProbe | object | `{"enabled":true,"failureThreshold":10,"initialDelaySeconds":60,"periodSeconds":10,"successThreshold":1,"timeoutSeconds":10}` | readinessProbe.successThreshold Success threshold for readinessProbe # |
| replicaCount | int | `1` | replicaCount Number of pods # |
| resources | object | `{"limits":{"cpu":0.5,"memory":"8192Mi"},"requests":{"cpu":0.5,"memory":"512Mi"}}` | resources.requests The requested resources for the containers # |
| schedulerName | string | `""` | schedulerName Specifies the schedulerName, if it's nil uses kube-scheduler # https://kubernetes.io/docs/tasks/administer-cluster/configure-multiple-schedulers/ # |
| secrets.IDENTITY_SERVICE_SECRET | string | `"HVSKUYqD4e5Rxu12hFDdCJKGM64sxgEynvdDhaOHaTHhwwn0K4Ttr0uoOxSsEVYNrUU="` |  |
| secrets.MONGODB_USER | string | `"admin"` |  |
| service.port | int | `9006` |  |
| service.type | string | `"ClusterIP"` |  |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `false` |  |
| serviceAccount.name | string | `"harness-default"` |  |
| sidecars | list | `[]` | sidecars Add additional sidecar containers to the ; pods # e.g: # sidecars: #   - name: your-image-name #     image: your-image #     imagePullPolicy: Always #     ports: #       - name: portname #         containerPort: 1234 # |
| startupProbe | object | `{"enabled":false,"failureThreshold":60,"initialDelaySeconds":0,"periodSeconds":10,"successThreshold":1,"timeoutSeconds":5}` | startupProbe.successThreshold Success threshold for startupProbe # |
| terminationGracePeriodSeconds | string | `"30"` | terminationGracePeriodSeconds In seconds, time the given to the pod needs to terminate gracefully # ref: https://kubernetes.io/docs/concepts/workloads/pods/pod/#termination-of-pods # |
| tolerations | list | `[]` | tolerations Tolerations for pod assignment. Evaluated as a template. # ref: https://kubernetes.io/docs/concepts/configuration/taint-and-toleration/ # |
| updateStrategy | object | `{"rollingUpdate":{"maxSurge":"25%","maxUnavailable":"25%"},"type":"RollingUpdate"}` | deployment.updateStrategy.type Deployment strategy type # ref: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#update-strategies # e.g: |
| waitForInitContainer | object | `{"containerSecurityContext":{"runAsNonRoot":true,"runAsUser":65534},"image":{"digest":"","imagePullSecrets":[],"pullPolicy":"IfNotPresent","registry":"docker.io","repository":"harness/helm-init-container","tag":"latest"},"resources":{"limits":{"cpu":"128m","memory":"128Mi"},"requests":{"cpu":"128m","memory":"128Mi"}}}` | Wait-For-App initContainers details |
| waitForInitContainer.image | object | `{"digest":"","imagePullSecrets":[],"pullPolicy":"IfNotPresent","registry":"docker.io","repository":"harness/helm-init-container","tag":"latest"}` | image.imagePullSecrets Specify docker-registry secret names as an array |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
