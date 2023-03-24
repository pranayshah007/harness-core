# access-control

![Version: 0.3.0](https://img.shields.io/badge/Version-0.3.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 1.0.78900](https://img.shields.io/badge/AppVersion-1.0.78900-informational?style=flat-square)

A Helm chart for Kubernetes

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| https://harness.github.io/helm-common | harness-common | 1.x.x |

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| affinity | object | `{}` |  |
| autoscaling.enabled | bool | `true` |  |
| autoscaling.maxReplicas | int | `100` |  |
| autoscaling.minReplicas | int | `1` |  |
| autoscaling.targetCPUUtilizationPercentage | int | `80` |  |
| config.ACCESS_CONTROL_PREFERENCE_ENABLED | bool | `true` |  |
| config.ACCESS_CONTROL_SERVICE_BASE_URL | string | `nil` |  |
| config.ACCOUNT_CLIENT_BASE_URL | string | `nil` |  |
| config.AGGREGATOR_ENABLED | bool | `true` |  |
| config.AUDIT_CLIENT_BASE_URL | string | `nil` |  |
| config.DEPLOY_MODE | string | `"KUBERNETES_ONPREM"` |  |
| config.DISTRIBUTED_LOCK_IMPLEMENTATION | string | `"REDIS"` |  |
| config.ENABLE_ACCESS_CONTROL | bool | `false` |  |
| config.ENABLE_AUDIT | bool | `true` |  |
| config.ENABLE_AUTH | bool | `true` |  |
| config.EVENTS_CONFIG_REDIS_SENTINELS | string | `nil` |  |
| config.EVENTS_CONFIG_SENTINEL_MASTER_NAME | string | `"harness-redis"` |  |
| config.EVENTS_CONFIG_USE_SENTINEL | bool | `true` |  |
| config.FEATURE_FLAG_CLIENT_BASE_URL | string | `nil` |  |
| config.GOOGLE_APPLICATION_CREDENTIALS | string | `"/opt/harness/monitoring/stackdriver.json"` |  |
| config.LOCK_CONFIG_REDIS_SENTINELS | string | `nil` |  |
| config.LOCK_CONFIG_SENTINEL_MASTER_NAME | string | `"harness-redis"` |  |
| config.LOCK_CONFIG_USE_SENTINEL | bool | `true` |  |
| config.LOGGING_LEVEL | string | `"INFO"` |  |
| config.MEMORY | string | `nil` |  |
| config.MONGODB_SSL_ENABLED | bool | `false` |  |
| config.NOTIFICATION_ENVIRONMENT | string | `"ONPREM"` |  |
| config.NOTIFICATION_SLACK_WEBHOOK_URL | string | `nil` |  |
| config.OFFSET_FLUSH_INTERVAL_MS | int | `10000` |  |
| config.PROJECT_CLIENT_BASE_URL | string | `nil` |  |
| config.RESOURCE_GROUP_CLIENT_BASE_URL | string | `nil` |  |
| config.RESOURCE_GROUP_ITERATOR_ENABLED | bool | `true` |  |
| config.RESOURCE_GROUP_ITERATOR_INTERVAL | int | `3600` |  |
| config.SCOPE_ITERATOR_ENABLED | bool | `true` |  |
| config.SCOPE_ITERATOR_INTERVAL | int | `3600` |  |
| config.SERVICEACCOUNT_CLIENT_BASE_URL | string | `nil` |  |
| config.SERVICEACCOUNT_ITERATOR_ENABLED | bool | `true` |  |
| config.SERVICEACCOUNT_ITERATOR_INTERVAL | int | `3600` |  |
| config.SUPPORTPREFERENCE_ITERATOR_ENABLED | bool | `true` |  |
| config.SUPPORTPREFERENCE_ITERATOR_INTERVAL | int | `600` |  |
| config.USER_CLIENT_BASE_URL | string | `nil` |  |
| config.USER_GROUP_CLIENT_BASE_URL | string | `nil` |  |
| config.USER_GROUP_ITERATOR_ENABLED | bool | `true` |  |
| config.USER_GROUP_ITERATOR_INTERVAL | int | `3600` |  |
| config.USER_ITERATOR_ENABLED | bool | `true` |  |
| config.USER_ITERATOR_INTERVAL | int | `3600` |  |
| fullnameOverride | string | `""` |  |
| global.ha | bool | `false` |  |
| global.imagePullSecrets | list | `[]` |  |
| global.loadbalancerURL | string | `""` |  |
| image.digest | string | `""` |  |
| image.imagePullSecrets | list | `[]` |  |
| image.pullPolicy | string | `"IfNotPresent"` |  |
| image.registry | string | `"docker.io"` |  |
| image.repository | string | `"harness/accesscontrol-service-signed"` |  |
| image.tag | string | `"76000"` |  |
| maxSurge | int | `1` |  |
| maxUnavailable | int | `0` |  |
| mongoSecrets.password.key | string | `"mongodb-root-password"` |  |
| mongoSecrets.password.name | string | `"mongodb-replicaset-chart"` |  |
| mongoSecrets.userName.key | string | `"mongodbUsername"` |  |
| mongoSecrets.userName.name | string | `"harness-secrets"` |  |
| nameOverride | string | `""` |  |
| nodeSelector | object | `{}` |  |
| podAnnotations | object | `{}` |  |
| podSecurityContext | object | `{}` |  |
| replicaCount | int | `1` |  |
| resources.limits.cpu | float | `0.5` |  |
| resources.limits.memory | string | `"8192Mi"` |  |
| resources.requests.cpu | float | `0.5` |  |
| resources.requests.memory | string | `"512Mi"` |  |
| secrets.IDENTITY_SERVICE_SECRET | string | `"HVSKUYqD4e5Rxu12hFDdCJKGM64sxgEynvdDhaOHaTHhwwn0K4Ttr0uoOxSsEVYNrUU="` |  |
| secrets.MONGODB_USER | string | `"admin"` |  |
| securityContext.runAsNonRoot | bool | `true` |  |
| securityContext.runAsUser | int | `65534` |  |
| service.port | int | `9006` |  |
| service.type | string | `"ClusterIP"` |  |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `false` |  |
| serviceAccount.name | string | `"harness-default"` |  |
| slackWebhookURL | string | `""` |  |
| tolerations | list | `[]` |  |
| waitForInitContainer.image.digest | string | `""` |  |
| waitForInitContainer.image.imagePullSecrets | list | `[]` |  |
| waitForInitContainer.image.pullPolicy | string | `"IfNotPresent"` |  |
| waitForInitContainer.image.registry | string | `"docker.io"` |  |
| waitForInitContainer.image.repository | string | `"harness/helm-init-container"` |  |
| waitForInitContainer.image.tag | string | `"latest"` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
