# ng-manager

![Version: 0.3.4](https://img.shields.io/badge/Version-0.3.4-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.0.1](https://img.shields.io/badge/AppVersion-0.0.1-informational?style=flat-square)

A Helm chart for Kubernetes

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| https://harness.github.io/helm-common | harness-common | 1.x.x |

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| affinity | object | `{}` |  |
| appLogLevel | string | `"INFO"` |  |
| autoscaling.enabled | bool | `true` |  |
| autoscaling.maxReplicas | int | `100` |  |
| autoscaling.minReplicas | int | `1` |  |
| autoscaling.targetCPUUtilizationPercentage | int | `80` |  |
| ceGcpSetupConfigGcpProjectId | string | `"placeHolderGcpProjectId"` |  |
| ceSecret.access_key.key | string | `"AWS_ACCESS_KEY"` |  |
| ceSecret.access_key.name | string | `"nextgen-ce"` |  |
| ceSecret.destination_bucket.key | string | `"AWS_DESTINATION_BUCKET"` |  |
| ceSecret.destination_bucket.name | string | `"nextgen-ce"` |  |
| ceSecret.secret_key.key | string | `"AWS_SECRET_KEY"` |  |
| ceSecret.secret_key.name | string | `"nextgen-ce"` |  |
| ceSecret.template_url.key | string | `"AWS_TEMPLATE_LINK"` |  |
| ceSecret.template_url.name | string | `"nextgen-ce"` |  |
| fullnameOverride | string | `""` |  |
| global.ccm.enabled | bool | `false` |  |
| global.chaos.enabled | bool | `false` |  |
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
| global.ingress.className | string | `"harness"` | set ingress object classname |
| global.ingress.enabled | bool | `false` | create ingress objects |
| global.ingress.hosts | list | `["my-host.example.org"]` | set host of ingressObjects |
| global.ingress.objects | object | `{"annotations":{}}` | add annotations to ingress objects |
| global.ingress.tls | object | `{"enabled":true,"secretName":""}` | set tls for ingress objects |
| global.istio.enabled | bool | `false` | create virtualServices objects |
| global.istio.gateway | object | `{"create":false}` | create gateway and use in virtualservice |
| global.istio.virtualService | object | `{"gateways":null,"hosts":null}` | if gateway not created, use specified gateway and host |
| global.license.cg | string | `""` |  |
| global.license.ng | string | `""` |  |
| global.loadbalancerURL | string | `""` |  |
| global.opa.enabled | bool | `false` |  |
| image.digest | string | `""` |  |
| image.imagePullSecrets | list | `[]` |  |
| image.pullPolicy | string | `"IfNotPresent"` |  |
| image.registry | string | `"docker.io"` |  |
| image.repository | string | `"harness/ng-manager-signed"` |  |
| image.tag | string | `"76019"` |  |
| imagePullSecrets | object | `{}` |  |
| initContainer.image.digest | string | `""` |  |
| initContainer.image.imagePullSecrets | list | `[]` |  |
| initContainer.image.registry | string | `"docker.io"` |  |
| initContainer.image.repository | string | `"busybox"` |  |
| initContainer.image.tag | string | `"1.35.0"` |  |
| java.memory | string | `"4096m"` |  |
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
| probes.livenessProbe.failureThreshold | int | `5` |  |
| probes.livenessProbe.httpGet.path | string | `"/health"` |  |
| probes.livenessProbe.httpGet.port | string | `"http-ng-manager"` |  |
| probes.livenessProbe.periodSeconds | int | `10` |  |
| probes.livenessProbe.timeoutSeconds | int | `2` |  |
| probes.readinessProbe.failureThreshold | int | `5` |  |
| probes.readinessProbe.httpGet.path | string | `"/health"` |  |
| probes.readinessProbe.httpGet.port | string | `"http-ng-manager"` |  |
| probes.readinessProbe.periodSeconds | int | `10` |  |
| probes.readinessProbe.timeoutSeconds | int | `2` |  |
| probes.startupProbe.failureThreshold | int | `25` |  |
| probes.startupProbe.httpGet.path | string | `"/health"` |  |
| probes.startupProbe.httpGet.port | string | `"http-ng-manager"` |  |
| probes.startupProbe.periodSeconds | int | `10` |  |
| probes.startupProbe.timeoutSeconds | int | `2` |  |
| replicaCount | int | `1` |  |
| resources.limits.cpu | int | `2` |  |
| resources.limits.memory | string | `"8192Mi"` |  |
| resources.requests.cpu | int | `2` |  |
| resources.requests.memory | string | `"200Mi"` |  |
| securityContext.runAsNonRoot | bool | `true` |  |
| securityContext.runAsUser | int | `65534` |  |
| service.gitsyncgrpcport | int | `13002` |  |
| service.grpcport | int | `9979` |  |
| service.port | int | `7090` |  |
| service.protocol | string | `"TCP"` |  |
| service.type | string | `"ClusterIP"` |  |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `false` |  |
| serviceAccount.name | string | `"harness-default"` |  |
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
