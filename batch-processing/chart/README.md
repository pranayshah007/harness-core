# batch-processing

![Version: 0.8.0](https://img.shields.io/badge/Version-0.8.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.0.80002](https://img.shields.io/badge/AppVersion-0.0.80002-informational?style=flat-square)

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
| autoscaling.enabled | bool | `false` |  |
| autoscaling.maxReplicas | int | `100` |  |
| autoscaling.minReplicas | int | `1` |  |
| autoscaling.targetCPU | string | `""` |  |
| autoscaling.targetMemory | string | `""` |  |
| awsAccountTagsCollectionJobConfig.enabled | bool | `true` |  |
| awsSecret.S3_SYNC_CONFIG_ACCESSKEY | string | `""` |  |
| awsSecret.S3_SYNC_CONFIG_SECRETKEY | string | `""` |  |
| ceBatchGCPCredentials | string | `""` |  |
| ceGCPHomeProjectCreds | string | `""` |  |
| clickhouse.password.key | string | `"admin-password"` |  |
| clickhouse.password.name | string | `"clickhouse"` |  |
| clickhouse.username | string | `"default"` |  |
| cloudProviderConfig.CLUSTER_DATA_GCS_BACKUP_BUCKET | string | `"clusterdata-onprem-backup"` |  |
| cloudProviderConfig.CLUSTER_DATA_GCS_BUCKET | string | `"clusterdata-onprem"` |  |
| cloudProviderConfig.DATA_PIPELINE_CONFIG_GCS_BASE_PATH | string | `"gs://awscustomerbillingdata-onprem"` |  |
| cloudProviderConfig.S3_SYNC_CONFIG_BUCKET_NAME | string | `"ccm-service-data-bucket"` |  |
| cloudProviderConfig.S3_SYNC_CONFIG_REGION | string | `"us-east-1"` |  |
| extraVolumeMounts | list | `[]` |  |
| extraVolumes | list | `[]` |  |
| fullnameOverride | string | `""` |  |
| global.ccm.gcpProjectId | string | `"placeHolder"` |  |
| global.database.clickhouse.enabled | bool | `false` |  |
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
| global.database.timescaledb.certKey | string | `""` |  |
| global.database.timescaledb.certName | string | `""` |  |
| global.database.timescaledb.extraArgs | string | `""` |  |
| global.database.timescaledb.hosts | list | `["timescaledb-single-chart:5432"]` | provide default values if mongo.installed is set to false |
| global.database.timescaledb.installed | bool | `true` |  |
| global.database.timescaledb.passwordKey | string | `""` |  |
| global.database.timescaledb.protocol | string | `"jdbc:postgresql"` |  |
| global.database.timescaledb.secretName | string | `""` |  |
| global.database.timescaledb.userKey | string | `""` |  |
| global.imagePullSecrets | list | `[]` |  |
| global.ingress.className | string | `"harness"` |  |
| global.ingress.enabled | bool | `false` |  |
| global.ingress.hosts[0] | string | `"my-host.example.org"` |  |
| global.ingress.tls.enabled | bool | `true` |  |
| global.ingress.tls.secretName | string | `""` |  |
| global.loadbalancerURL | string | `"https://test"` |  |
| global.smtpCreateSecret.enabled | bool | `false` |  |
| global.stackDriverLoggingEnabled | bool | `false` |  |
| image.digest | string | `""` |  |
| image.imagePullSecrets | list | `[]` |  |
| image.pullPolicy | string | `"Always"` |  |
| image.registry | string | `"docker.io"` |  |
| image.repository | string | `"harness/batch-processing-signed"` |  |
| image.tag | string | `"80003-000"` |  |
| imageClickhouseEnabled.digest | string | `""` |  |
| imageClickhouseEnabled.imagePullSecrets | list | `[]` |  |
| imageClickhouseEnabled.pullPolicy | string | `"Always"` |  |
| imageClickhouseEnabled.registry | string | `"docker.io"` |  |
| imageClickhouseEnabled.repository | string | `"harness/batch-processing-signed"` |  |
| imageClickhouseEnabled.tag | string | `"80003-000"` |  |
| isolatedReplica | int | `0` |  |
| java.memory | string | `"7168"` |  |
| lifecycleHooks | object | `{}` |  |
| mongoSecrets.password.key | string | `"mongodb-root-password"` |  |
| mongoSecrets.password.name | string | `"mongodb-replicaset-chart"` |  |
| mongoSecrets.userName.key | string | `"mongodbUsername"` |  |
| mongoSecrets.userName.name | string | `"harness-secrets"` |  |
| nameOverride | string | `""` |  |
| nodeSelector | object | `{}` |  |
| podAnnotations | object | `{}` |  |
| podSecurityContext | object | `{}` |  |
| replicaCount | int | `1` |  |
| resources.limits.memory | string | `"10Gi"` |  |
| resources.requests.cpu | string | `"1024m"` |  |
| resources.requests.memory | string | `"10Gi"` |  |
| securityContext | object | `{}` |  |
| service.port | int | `6340` |  |
| service.type | string | `"ClusterIP"` |  |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `false` |  |
| serviceAccount.name | string | `"harness-default"` |  |
| smtp.host | string | `""` |  |
| smtp.password | string | `""` |  |
| smtp.user | string | `""` |  |
| storageObjectAdmin | string | `""` |  |
| timescaleSecret.password.key | string | `"timescaledbPostgresPassword"` |  |
| timescaleSecret.password.name | string | `"harness-secrets"` |  |
| tolerations | list | `[]` |  |
| waitForInitContainer.image.digest | string | `""` |  |
| waitForInitContainer.image.pullPolicy | string | `"IfNotPresent"` |  |
| waitForInitContainer.image.registry | string | `"docker.io"` |  |
| waitForInitContainer.image.repository | string | `"harness/helm-init-container"` |  |
| waitForInitContainer.image.tag | string | `"latest"` |  |
| workloadIdentity.enabled | bool | `false` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
