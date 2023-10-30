# batch-processing

![Version: 0.12.0](https://img.shields.io/badge/Version-0.12.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.0.81100](https://img.shields.io/badge/AppVersion-0.0.81100-informational?style=flat-square)

A Helm chart for Kubernetes

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| https://harness.github.io/helm-common | harness-common | 1.x.x |

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| CF_CLIENT_API_KEY | string | `"BATCH_PROCESSING_ON_PREM"` |  |
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
| cliProxy.enabled | bool | `false` |  |
| cliProxy.host | string | `"localhost"` |  |
| cliProxy.password | string | `""` |  |
| cliProxy.port | int | `80` |  |
| cliProxy.protocol | string | `"http"` |  |
| cliProxy.username | string | `""` |  |
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
| global.awsServiceEndpointUrls.cloudwatchEndPointUrl | string | `"https://monitoring.us-east-2.amazonaws.com"` |  |
| global.awsServiceEndpointUrls.ecsEndPointUrl | string | `"https://ecs.us-east-2.amazonaws.com"` |  |
| global.awsServiceEndpointUrls.enabled | bool | `false` |  |
| global.awsServiceEndpointUrls.endPointRegion | string | `"us-east-2"` |  |
| global.awsServiceEndpointUrls.stsEndPointUrl | string | `"https://sts.us-east-2.amazonaws.com"` |  |
| global.ccm.gcpProjectId | string | `"placeHolder"` |  |
| global.commonAnnotations | object | `{}` |  |
| global.commonLabels | object | `{}` |  |
| global.database.clickhouse.enabled | bool | `false` |  |
| global.database.clickhouse.secrets.kubernetesSecrets[0].keys.CLICKHOUSE_PASSWORD | string | `""` |  |
| global.database.clickhouse.secrets.kubernetesSecrets[0].keys.CLICKHOUSE_USERNAME | string | `""` |  |
| global.database.clickhouse.secrets.kubernetesSecrets[0].secretName | string | `""` |  |
| global.database.clickhouse.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.CLICKHOUSE_PASSWORD.name | string | `""` |  |
| global.database.clickhouse.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.CLICKHOUSE_PASSWORD.property | string | `""` |  |
| global.database.clickhouse.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.CLICKHOUSE_USERNAME.name | string | `""` |  |
| global.database.clickhouse.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.CLICKHOUSE_USERNAME.property | string | `""` |  |
| global.database.clickhouse.secrets.secretManagement.externalSecretsOperator[0].secretStore.kind | string | `""` |  |
| global.database.clickhouse.secrets.secretManagement.externalSecretsOperator[0].secretStore.name | string | `""` |  |
| global.database.mongo.extraArgs | string | `""` |  |
| global.database.mongo.hosts | list | `[]` | provide default values if mongo.installed is set to false |
| global.database.mongo.installed | bool | `true` |  |
| global.database.mongo.passwordKey | string | `""` |  |
| global.database.mongo.protocol | string | `"mongodb"` |  |
| global.database.mongo.secretName | string | `""` |  |
| global.database.mongo.secrets.kubernetesSecrets[0].keys.MONGO_PASSWORD | string | `""` |  |
| global.database.mongo.secrets.kubernetesSecrets[0].keys.MONGO_USER | string | `""` |  |
| global.database.mongo.secrets.kubernetesSecrets[0].secretName | string | `""` |  |
| global.database.mongo.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.MONGO_PASSWORD.name | string | `""` |  |
| global.database.mongo.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.MONGO_PASSWORD.property | string | `""` |  |
| global.database.mongo.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.MONGO_USER.name | string | `""` |  |
| global.database.mongo.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.MONGO_USER.property | string | `""` |  |
| global.database.mongo.secrets.secretManagement.externalSecretsOperator[0].secretStore.kind | string | `""` |  |
| global.database.mongo.secrets.secretManagement.externalSecretsOperator[0].secretStore.name | string | `""` |  |
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
| global.database.timescaledb.secrets | object | `{"kubernetesSecrets":[{"keys":{"TIMESCALEDB_PASSWORD":"","TIMESCALEDB_SSL_ROOT_CERT":"","TIMESCALEDB_USERNAME":""},"secretName":""}],"secretManagement":{"externalSecretsOperator":[{"remoteKeys":{"TIMESCALEDB_PASSWORD":{"name":"","property":""},"TIMESCALEDB_SSL_ROOT_CERT":{"name":"","property":""},"TIMESCALEDB_USERNAME":{"name":"","property":""}},"secretStore":{"kind":"","name":""}}]}}` | TimescaleDB secrets |
| global.database.timescaledb.sslEnabled | bool | `false` | Enable TimescaleDB SSL |
| global.database.timescaledb.userKey | string | `""` |  |
| global.deployMode | string | `"KUBERNETES_ONPREM"` |  |
| global.imagePullSecrets | list | `[]` |  |
| global.ingress.className | string | `"harness"` |  |
| global.ingress.enabled | bool | `false` |  |
| global.ingress.hosts[0] | string | `"my-host.example.org"` |  |
| global.ingress.tls.enabled | bool | `true` |  |
| global.ingress.tls.secretName | string | `""` |  |
| global.loadbalancerURL | string | `"https://test"` |  |
| global.proxy.enabled | bool | `false` |  |
| global.proxy.host | string | `"localhost"` |  |
| global.proxy.password | string | `""` |  |
| global.proxy.port | int | `80` |  |
| global.proxy.protocol | string | `"http"` |  |
| global.proxy.username | string | `""` |  |
| global.smtpCreateSecret.SMTP_HOST | string | `""` |  |
| global.smtpCreateSecret.SMTP_PASSWORD | string | `""` |  |
| global.smtpCreateSecret.SMTP_PORT | string | `"465"` |  |
| global.smtpCreateSecret.SMTP_USERNAME | string | `""` |  |
| global.smtpCreateSecret.SMTP_USE_SSL | string | `"true"` |  |
| global.smtpCreateSecret.enabled | bool | `false` |  |
| global.smtpCreateSecret.secrets.kubernetesSecrets[0].keys.SMTP_HOST | string | `""` |  |
| global.smtpCreateSecret.secrets.kubernetesSecrets[0].keys.SMTP_PASSWORD | string | `""` |  |
| global.smtpCreateSecret.secrets.kubernetesSecrets[0].keys.SMTP_PORT | string | `""` |  |
| global.smtpCreateSecret.secrets.kubernetesSecrets[0].keys.SMTP_USERNAME | string | `""` |  |
| global.smtpCreateSecret.secrets.kubernetesSecrets[0].keys.SMTP_USE_SSL | string | `""` |  |
| global.smtpCreateSecret.secrets.kubernetesSecrets[0].secretName | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.SMTP_HOST.name | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.SMTP_HOST.property | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.SMTP_PASSWORD.name | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.SMTP_PASSWORD.property | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.SMTP_PORT.name | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.SMTP_PORT.property | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.SMTP_USERNAME.name | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.SMTP_USERNAME.property | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.SMTP_USE_SSL.name | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.SMTP_USE_SSL.property | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].secretStore.kind | string | `""` |  |
| global.smtpCreateSecret.secrets.secretManagement.externalSecretsOperator[0].secretStore.name | string | `""` |  |
| global.stackDriverLoggingEnabled | bool | `false` |  |
| image.digest | string | `""` |  |
| image.imagePullSecrets | list | `[]` |  |
| image.pullPolicy | string | `"Always"` |  |
| image.registry | string | `"docker.io"` |  |
| image.repository | string | `"harness/batch-processing-signed"` |  |
| image.tag | string | `"81100-000"` |  |
| imageClickhouseEnabled.digest | string | `""` |  |
| imageClickhouseEnabled.imagePullSecrets | list | `[]` |  |
| imageClickhouseEnabled.pullPolicy | string | `"Always"` |  |
| imageClickhouseEnabled.registry | string | `"docker.io"` |  |
| imageClickhouseEnabled.repository | string | `"harness/batch-processing-signed"` |  |
| imageClickhouseEnabled.tag | string | `"81100-000"` |  |
| isolatedReplica | int | `0` |  |
| java.memory | string | `"7168"` |  |
| java17flags | string | `""` |  |
| lifecycleHooks | object | `{}` |  |
| mongo.extraArgs | string | `""` |  |
| mongo.hosts | list | `[]` |  |
| mongo.passwordKey | string | `""` |  |
| mongo.protocol | string | `""` |  |
| mongo.secretName | string | `""` |  |
| mongo.secrets.kubernetesSecrets[0].keys.MONGO_PASSWORD | string | `""` |  |
| mongo.secrets.kubernetesSecrets[0].keys.MONGO_USER | string | `""` |  |
| mongo.secrets.kubernetesSecrets[0].secretName | string | `""` |  |
| mongo.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.MONGO_PASSWORD.name | string | `""` |  |
| mongo.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.MONGO_PASSWORD.property | string | `""` |  |
| mongo.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.MONGO_USER.name | string | `""` |  |
| mongo.secrets.secretManagement.externalSecretsOperator[0].remoteKeys.MONGO_USER.property | string | `""` |  |
| mongo.secrets.secretManagement.externalSecretsOperator[0].secretStore.kind | string | `""` |  |
| mongo.secrets.secretManagement.externalSecretsOperator[0].secretStore.name | string | `""` |  |
| mongo.userKey | string | `""` |  |
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
| secrets.default.CE_NG_SERVICE_SECRET | string | `"IC04LYMBf1lDP5oeY4hupxd4HJhLmN6azUku3xEbeE3SUx5G3ZYzhbiwVtK4i7AmqyU9OZkwB4v8E9qM"` |  |
| secrets.default.NEXT_GEN_MANAGER_SECRET | string | `"IC04LYMBf1lDP5oeY4hupxd4HJhLmN6azUku3xEbeE3SUx5G3ZYzhbiwVtK4i7AmqyU9OZkwB4v8E9qM"` |  |
| secrets.default.S3_SYNC_CONFIG_ACCESSKEY | string | `""` |  |
| secrets.default.S3_SYNC_CONFIG_SECRETKEY | string | `""` |  |
| secrets.kubernetesSecrets[0].keys.CE_NG_SERVICE_SECRET | string | `""` |  |
| secrets.kubernetesSecrets[0].keys.NEXT_GEN_MANAGER_SECRET | string | `""` |  |
| secrets.kubernetesSecrets[0].keys.S3_SYNC_CONFIG_ACCESSKEY | string | `""` |  |
| secrets.kubernetesSecrets[0].keys.S3_SYNC_CONFIG_SECRETKEY | string | `""` |  |
| secrets.kubernetesSecrets[0].secretName | string | `""` |  |
| secrets.secretManagement.externalSecretsOperator[0].remoteKeys.CE_NG_SERVICE_SECRET.name | string | `""` |  |
| secrets.secretManagement.externalSecretsOperator[0].remoteKeys.CE_NG_SERVICE_SECRET.property | string | `""` |  |
| secrets.secretManagement.externalSecretsOperator[0].remoteKeys.NEXT_GEN_MANAGER_SECRET.name | string | `""` |  |
| secrets.secretManagement.externalSecretsOperator[0].remoteKeys.NEXT_GEN_MANAGER_SECRET.property | string | `""` |  |
| secrets.secretManagement.externalSecretsOperator[0].remoteKeys.S3_SYNC_CONFIG_ACCESSKEY.name | string | `""` |  |
| secrets.secretManagement.externalSecretsOperator[0].remoteKeys.S3_SYNC_CONFIG_ACCESSKEY.property | string | `""` |  |
| secrets.secretManagement.externalSecretsOperator[0].remoteKeys.S3_SYNC_CONFIG_SECRETKEY.name | string | `""` |  |
| secrets.secretManagement.externalSecretsOperator[0].remoteKeys.S3_SYNC_CONFIG_SECRETKEY.property | string | `""` |  |
| secrets.secretManagement.externalSecretsOperator[0].secretStore.kind | string | `""` |  |
| secrets.secretManagement.externalSecretsOperator[0].secretStore.name | string | `""` |  |
| securityContext | object | `{}` |  |
| service.annotations | object | `{}` |  |
| service.port | int | `6340` |  |
| service.type | string | `"ClusterIP"` |  |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `false` |  |
| serviceAccount.name | string | `"ccm-default"` |  |
| smtp.host | string | `""` |  |
| smtp.password | string | `""` |  |
| smtp.user | string | `""` |  |
| storageObjectAdmin | string | `""` |  |
| timescaleSecret.password.key | string | `"timescaledbPostgresPassword"` |  |
| timescaleSecret.password.name | string | `"harness-secrets"` |  |
| timescaledb.hosts | list | `[]` | TimescaleDB host names |
| timescaledb.secrets | object | `{"kubernetesSecrets":[{"keys":{"TIMESCALEDB_PASSWORD":"","TIMESCALEDB_SSL_ROOT_CERT":"","TIMESCALEDB_USERNAME":""},"secretName":""}],"secretManagement":{"externalSecretsOperator":[{"remoteKeys":{"TIMESCALEDB_PASSWORD":{"name":"","property":""},"TIMESCALEDB_SSL_ROOT_CERT":{"name":"","property":""},"TIMESCALEDB_USERNAME":{"name":"","property":""}},"secretStore":{"kind":"","name":""}}]}}` | TimescaleDB secrets |
| tolerations | list | `[]` |  |
| waitForInitContainer.image.digest | string | `""` |  |
| waitForInitContainer.image.pullPolicy | string | `"IfNotPresent"` |  |
| waitForInitContainer.image.registry | string | `"docker.io"` |  |
| waitForInitContainer.image.repository | string | `"harness/helm-init-container"` |  |
| waitForInitContainer.image.tag | string | `"latest"` |  |
| workloadIdentity.enabled | bool | `false` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.3](https://github.com/norwoodj/helm-docs/releases/v1.11.3)
