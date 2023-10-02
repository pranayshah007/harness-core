# sto-manager

![Version: 0.8.0](https://img.shields.io/badge/Version-0.8.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.0.79900](https://img.shields.io/badge/AppVersion-0.0.79900-informational?style=flat-square)

A Helm chart for Kubernetes

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| https://harness.github.io/helm-common | harness-common | 1.x.x |

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| addOnImage.image.digest | string | `""` |  |
| addOnImage.image.imagePullSecrets | list | `[]` |  |
| addOnImage.image.pullPolicy | string | `"IfNotPresent"` |  |
| addOnImage.image.registry | string | `"docker.io"` |  |
| addOnImage.image.repository | string | `"harness/ci-addon"` |  |
| addOnImage.image.tag | string | `"1.16.19"` |  |
| additionalConfigs | object | `{}` |  |
| affinity | object | `{}` |  |
| autoscaling.enabled | bool | `false` |  |
| autoscaling.maxReplicas | int | `100` |  |
| autoscaling.minReplicas | int | `1` |  |
| autoscaling.targetCPU | string | `""` |  |
| autoscaling.targetMemory | string | `""` |  |
| defaultInternalImageConnector | string | `"account.harnessImage"` |  |
| extraVolumeMounts | list | `[]` |  |
| extraVolumes | list | `[]` |  |
| fullnameOverride | string | `""` |  |
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
| global.database.redis.extraArgs | string | `""` |  |
| global.database.redis.hosts | list | `["redis:6379"]` | provide default values if redis.installed is set to false |
| global.database.redis.installed | bool | `true` |  |
| global.database.redis.passwordKey | string | `"redis-password"` |  |
| global.database.redis.protocol | string | `"redis"` |  |
| global.database.redis.secretName | string | `"redis-secret"` |  |
| global.database.redis.userKey | string | `"redis-user"` |  |
| global.database.timescaledb.certKey | string | `""` |  |
| global.database.timescaledb.certName | string | `""` |  |
| global.database.timescaledb.extraArgs | string | `""` |  |
| global.database.timescaledb.hosts | list | `["timescaledb-single-chart:5432"]` | provide default values if mongo.installed is set to false |
| global.database.timescaledb.installed | bool | `true` |  |
| global.database.timescaledb.passwordKey | string | `""` |  |
| global.database.timescaledb.protocol | string | `"jdbc:postgresql"` |  |
| global.database.timescaledb.secretName | string | `""` |  |
| global.database.timescaledb.userKey | string | `""` |  |
| global.delegate.airgapped | bool | `false` |  |
| global.imagePullSecrets | list | `[]` |  |
| global.ingress.enabled | bool | `false` |  |
| global.ingress.hosts[0] | string | `"my-host.example.org"` |  |
| global.ingress.objects.annotations | object | `{}` |  |
| global.ingress.tls.enabled | bool | `false` |  |
| global.ingress.tls.secretName | string | `"harness-cert"` |  |
| global.istio.enabled | bool | `false` |  |
| global.istio.gateway.create | bool | `true` |  |
| global.istio.gateway.port | int | `443` |  |
| global.istio.gateway.protocol | string | `"HTTPS"` |  |
| global.istio.hosts[0] | string | `"*"` |  |
| global.istio.strict | bool | `true` |  |
| global.istio.tls.credentialName | string | `""` |  |
| global.istio.virtualService.gateways | object | `{}` |  |
| global.istio.virtualService.hosts[0] | string | `""` |  |
| global.loadbalancerURL | string | `"https://test"` |  |
| global.opa.enabled | bool | `false` |  |
| global.stackDriverLoggingEnabled | bool | `false` |  |
| image.digest | string | `""` |  |
| image.imagePullSecrets | list | `[]` |  |
| image.pullPolicy | string | `"IfNotPresent"` |  |
| image.registry | string | `"docker.io"` |  |
| image.repository | string | `"harness/stomanager-signed"` |  |
| image.tag | string | `"79900-000"` |  |
| ingress.annotations | object | `{}` |  |
| ingress.className | string | `""` |  |
| ingress.enabled | bool | `false` |  |
| ingress.hosts[0].host | string | `"chart-example.local"` |  |
| ingress.hosts[0].paths[0].path | string | `"/"` |  |
| ingress.hosts[0].paths[0].pathType | string | `"ImplementationSpecific"` |  |
| ingress.tls | list | `[]` |  |
| java.memory | int | `2500` |  |
| java.memoryLimit | int | `600` |  |
| leImage.image.digest | string | `""` |  |
| leImage.image.imagePullSecrets | list | `[]` |  |
| leImage.image.pullPolicy | string | `"IfNotPresent"` |  |
| leImage.image.registry | string | `"docker.io"` |  |
| leImage.image.repository | string | `"harness/ci-lite-engine"` |  |
| leImage.image.tag | string | `"1.16.19"` |  |
| lifecycleHooks | object | `{}` |  |
| maxSurge | string | `"100%"` |  |
| maxUnavailable | int | `0` |  |
| mongoSecrets.password.key | string | `"mongodb-root-password"` |  |
| mongoSecrets.password.name | string | `"mongodb-replicaset-chart"` |  |
| mongoSecrets.userName.key | string | `"mongodbUsername"` |  |
| mongoSecrets.userName.name | string | `"harness-secrets"` |  |
| nameOverride | string | `""` |  |
| ngServiceAccount | string | `"test"` |  |
| nodeSelector | object | `{}` |  |
| podAnnotations | object | `{}` |  |
| podSecurityContext | object | `{}` |  |
| probes.livenessProbe.failureThreshold | int | `5` |  |
| probes.livenessProbe.httpGet.path | string | `"/health/liveness"` |  |
| probes.livenessProbe.httpGet.port | string | `"http"` |  |
| probes.livenessProbe.periodSeconds | int | `5` |  |
| probes.livenessProbe.timeoutSeconds | int | `2` |  |
| probes.readinessProbe.failureThreshold | int | `5` |  |
| probes.readinessProbe.httpGet.path | string | `"/health"` |  |
| probes.readinessProbe.httpGet.port | string | `"http"` |  |
| probes.readinessProbe.periodSeconds | int | `10` |  |
| probes.readinessProbe.timeoutSeconds | int | `2` |  |
| probes.startupProbe.failureThreshold | int | `25` |  |
| probes.startupProbe.httpGet.path | string | `"/health"` |  |
| probes.startupProbe.httpGet.port | string | `"http"` |  |
| probes.startupProbe.periodSeconds | int | `10` |  |
| probes.startupProbe.timeoutSeconds | int | `2` |  |
| redislabsCATruststore | string | `"test"` |  |
| replicaCount | int | `1` |  |
| resources.requests.cpu | int | `1` |  |
| resources.requests.memory | string | `"3Gi"` |  |
| s3UploadImage.image.digest | string | `""` |  |
| s3UploadImage.image.imagePullSecrets | list | `[]` |  |
| s3UploadImage.image.pullPolicy | string | `"IfNotPresent"` |  |
| s3UploadImage.image.registry | string | `"docker.io"` |  |
| s3UploadImage.image.repository | string | `"plugins/s3"` |  |
| s3UploadImage.image.tag | string | `"1.2.3"` |  |
| securityContext | object | `{}` |  |
| securityImage.image.digest | string | `""` |  |
| securityImage.image.imagePullSecrets | list | `[]` |  |
| securityImage.image.pullPolicy | string | `"IfNotPresent"` |  |
| securityImage.image.registry | string | `"docker.io"` |  |
| securityImage.image.repository | string | `"harness/sto-plugin"` |  |
| securityImage.image.tag | string | `"1.13.0"` |  |
| service.grpcport | int | `9979` |  |
| service.port | int | `7090` |  |
| service.type | string | `"ClusterIP"` |  |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `false` |  |
| serviceAccount.name | string | `"harness-default"` |  |
| stoServiceGlobalToken.key | string | `"stoAppHarnessToken"` |  |
| stoServiceGlobalToken.name | string | `"harness-secrets"` |  |
| timescaleSecret.password.key | string | `"timescaledbPostgresPassword"` |  |
| timescaleSecret.password.name | string | `"harness-secrets"` |  |
| tolerations | list | `[]` |  |
| waitForInitContainer.image.digest | string | `""` |  |
| waitForInitContainer.image.imagePullSecrets | list | `[]` |  |
| waitForInitContainer.image.pullPolicy | string | `"IfNotPresent"` |  |
| waitForInitContainer.image.registry | string | `"docker.io"` |  |
| waitForInitContainer.image.repository | string | `"harness/helm-init-container"` |  |
| waitForInitContainer.image.tag | string | `"latest"` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
