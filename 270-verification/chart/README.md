# verification-svc

![Version: 0.8.0](https://img.shields.io/badge/Version-0.8.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.0.79819](https://img.shields.io/badge/AppVersion-0.0.79819-informational?style=flat-square)

A Helm chart for Kubernetes

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| https://harness.github.io/helm-common | harness-common | 1.x.x |

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| additionalConfigs | object | `{}` |  |
| affinity | object | `{}` |  |
| appLogInfo | string | `"INFO"` | appLogInfo can be INFO DEBUG WARN ERROR |
| autoscaling.enabled | bool | `false` |  |
| autoscaling.maxReplicas | int | `100` |  |
| autoscaling.minReplicas | int | `1` |  |
| autoscaling.targetCPU | string | `""` |  |
| autoscaling.targetMemory | string | `""` |  |
| fullnameOverride | string | `""` |  |
| global.airgap | bool | `false` |  |
| global.database.mongo.extraArgs | string | `""` |  |
| global.database.mongo.hosts | list | `[]` | provide default values if mongo.installed is set to false |
| global.database.mongo.installed | bool | `true` |  |
| global.database.mongo.passwordKey | string | `""` |  |
| global.database.mongo.protocol | string | `"mongodb"` |  |
| global.database.mongo.secretName | string | `""` |  |
| global.database.mongo.userKey | string | `""` |  |
| global.database.postgres.extraArgs | string | `""` |  |
| global.database.postgres.hosts[0] | string | `"postgres:5432"` |  |
| global.database.postgres.installed | bool | `true` |  |
| global.database.postgres.passwordKey | string | `""` |  |
| global.database.postgres.protocol | string | `"postgres"` |  |
| global.database.postgres.secretName | string | `""` |  |
| global.database.postgres.userKey | string | `""` |  |
| global.database.timescaledb.extraArgs | string | `""` |  |
| global.database.timescaledb.hosts | list | `["timescaledb-single-chart:5432"]` | provide default values if mongo.installed is set to false |
| global.database.timescaledb.installed | bool | `true` |  |
| global.database.timescaledb.passwordKey | string | `""` |  |
| global.database.timescaledb.protocol | string | `"jdbc:postgresql"` |  |
| global.database.timescaledb.secretName | string | `""` |  |
| global.database.timescaledb.userKey | string | `""` |  |
| global.ha | bool | `false` |  |
| global.imagePullSecrets | list | `[]` |  |
| global.ingress.additionalPaths | string | `nil` |  |
| global.ingress.className | string | `"harness"` |  |
| global.ingress.enabled | bool | `false` |  |
| global.ingress.hosts[0] | string | `"*"` |  |
| global.ingress.objects.annotations | object | `{}` |  |
| global.ingress.tls.enabled | bool | `false` |  |
| global.ingress.tls.secretName | string | `"harness-cert"` |  |
| global.istio.enabled | bool | `false` |  |
| global.istio.gateway.create | bool | `false` |  |
| global.istio.virtualService.gateways | string | `nil` |  |
| global.istio.virtualService.hosts | string | `nil` |  |
| global.kubeVersion | string | `""` |  |
| global.loadbalancerURL | string | `""` |  |
| image.digest | string | `""` |  |
| image.imagePullSecrets | list | `[]` |  |
| image.pullPolicy | string | `"IfNotPresent"` |  |
| image.registry | string | `"docker.io"` |  |
| image.repository | string | `"harness/verification-service-signed"` |  |
| image.tag | string | `"79819"` |  |
| ingress.annotations | object | `{}` |  |
| ingress.hosts[0].host | string | `""` |  |
| ingress.hosts[0].paths[0].path | string | `"/verification"` |  |
| ingress.hosts[0].paths[0].pathType | string | `"ImplementationSpecific"` |  |
| ingress.tls | list | `[]` |  |
| java | object | `{"memory":2048}` | Specify Java memory to be used |
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
| resources.limits.cpu | string | `"500m"` |  |
| resources.limits.memory | string | `"3000Mi"` |  |
| resources.requests.cpu | string | `"500m"` |  |
| resources.requests.memory | string | `"3000Mi"` |  |
| securityContext.runAsNonRoot | bool | `true` |  |
| service.port | int | `7070` |  |
| service.type | string | `"ClusterIP"` |  |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `false` |  |
| serviceAccount.name | string | `"harness-default"` |  |
| tolerations | list | `[]` |  |
| waitForInitContainer.image.digest | string | `""` |  |
| waitForInitContainer.image.imagePullSecrets | list | `[]` |  |
| waitForInitContainer.image.pullPolicy | string | `"IfNotPresent"` |  |
| waitForInitContainer.image.registry | string | `"docker.io"` |  |
| waitForInitContainer.image.repository | string | `"harness/helm-init-container"` |  |
| waitForInitContainer.image.tag | string | `"latest"` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
