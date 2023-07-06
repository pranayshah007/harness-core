# migrator

![Version: 0.3.0](https://img.shields.io/badge/Version-0.3.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.0.1](https://img.shields.io/badge/AppVersion-0.0.1-informational?style=flat-square)

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
| config.ACCESS_CONTROL_BASE_URL | string | `nil` |  |
| config.ACCESS_CONTROL_ENABLED | string | `"true"` |  |
| config.ALLOWED_ORIGINS | string | `""` |  |
| config.ATMOSPHERE_BACKEND | string | `"REDIS"` |  |
| config.AUDIT_CLIENT_BASEURL | string | `nil` |  |
| config.BACKGROUND_SCHEDULER_CLUSTERED | string | `"true"` |  |
| config.CACHE_BACKEND | string | `"REDIS"` |  |
| config.CAPSULE_JAR | string | `"migrator-capsule.jar"` |  |
| config.DELEGATE_METADATA_URL | string | `""` |  |
| config.DELEGATE_SERVICE_AUTHORITY | string | `"harness-manager:9879"` |  |
| config.DELEGATE_SERVICE_TARGET | string | `"harness-manager:9879"` |  |
| config.DEPLOY_MODE | string | `"KUBERNETES_ONPREM"` |  |
| config.DISABLE_NEW_RELIC | string | `"true"` |  |
| config.DISTRIBUTED_LOCK_IMPLEMENTATION | string | `"REDIS"` |  |
| config.ENABLE_G1GC | string | `"true"` |  |
| config.ENABLE_ITERATORS | string | `"true"` |  |
| config.EVENTS_FRAMEWORK_AVAILABLE_IN_ONPREM | string | `"true"` |  |
| config.EVENTS_FRAMEWORK_REDIS_SENTINELS | string | `nil` |  |
| config.EVENTS_FRAMEWORK_REDIS_URL | string | `"redis://localhost:6379"` |  |
| config.EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME | string | `"harness-redis"` |  |
| config.EVENTS_FRAMEWORK_USE_SENTINEL | string | `"true"` |  |
| config.EXTERNAL_GRAPHQL_RATE_LIMIT | int | `500` |  |
| config.GITOPS_SERVICE_CLIENT_BASEURL | string | `nil` |  |
| config.HARNESS_ENABLE_NG_AUTH_UI_PLACEHOLDER | string | `"true"` |  |
| config.HAZELCAST_NAMESPACE | string | `nil` |  |
| config.HAZELCAST_SERVICE | string | `"harness-manager"` |  |
| config.HZ_CLUSTER_NAME | string | `"harness-manager"` |  |
| config.IMMUTABLE_DELEGATE_ENABLED | string | `nil` |  |
| config.ITERATOR_CONFIG_PATH | string | `"/opt/harness/config"` |  |
| config.LICENSE_INFO | string | `nil` |  |
| config.LOGGING_LEVEL | string | `"INFO"` |  |
| config.LOG_STREAMING_SERVICE_BASEURL | string | `nil` |  |
| config.LOG_STREAMING_SERVICE_EXTERNAL_URL | string | `nil` |  |
| config.MEMORY | string | `"2048"` |  |
| config.MONGO_SSL_CONFIG | string | `"false"` |  |
| config.NG_MANAGER_BASE_URL | string | `nil` |  |
| config.NG_MANAGER_CLIENT_BASEURL | string | `nil` |  |
| config.PIPELINE_SERVICE_CLIENT_BASEURL | string | `nil` |  |
| config.REDIS_MASTER_NAME | string | `"harness-redis"` |  |
| config.REDIS_SENTINEL | string | `"true"` |  |
| config.REDIS_SENTINELS | string | `nil` |  |
| config.REDIS_URL | string | `"redis://localhost:6379"` |  |
| config.SERVER_PORT | string | `"9080"` |  |
| config.SERVICE_ACC | string | `"/opt/harness/svc/service_acc.json"` |  |
| config.TEMPLATE_SERVICE_ENDPOINT | string | `nil` |  |
| config.VERSION | string | `"1.0.78900"` |  |
| config.WATCHER_METADATA_URL | string | `""` |  |
| delegate_docker_image.image.digest | string | `""` |  |
| delegate_docker_image.image.registry | string | `"docker.io"` |  |
| delegate_docker_image.image.repository | string | `"harness/delegate"` |  |
| delegate_docker_image.image.tag | string | `"latest"` |  |
| external_graphql_rate_limit | string | `"500"` |  |
| featureFlags | object | `{"ADDITIONAL":"","Base":"LDAP_SSO_PROVIDER,ASYNC_ARTIFACT_COLLECTION,JIRA_INTEGRATION,AUDIT_TRAIL_UI,GDS_TIME_SERIES_SAVE_PER_MINUTE,STACKDRIVER_SERVICEGUARD,BATCH_SECRET_DECRYPTION,TIME_SERIES_SERVICEGUARD_V2,TIME_SERIES_WORKFLOW_V2,CUSTOM_DASHBOARD,GRAPHQL,CV_FEEDBACKS,LOGS_V2_247,UPGRADE_JRE,LOG_STREAMING_INTEGRATION,NG_HARNESS_APPROVAL,GIT_SYNC_NG,NG_SHOW_DELEGATE,NG_CG_TASK_ASSIGNMENT_ISOLATION,CI_OVERVIEW_PAGE,AZURE_CLOUD_PROVIDER_VALIDATION_ON_DELEGATE,TERRAFORM_AWS_CP_AUTHENTICATION,NG_TEMPLATES,NEW_DEPLOYMENT_FREEZE,HELM_CHART_AS_ARTIFACT,RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION,WEBHOOK_TRIGGER_AUTHORIZATION,GITHUB_WEBHOOK_AUTHENTICATION,CUSTOM_MANIFEST,GIT_ACCOUNT_SUPPORT,AZURE_WEBAPP,PRUNE_KUBERNETES_RESOURCES,LDAP_GROUP_SYNC_JOB_ITERATOR,POLLING_INTERVAL_CONFIGURABLE,APPLICATION_DROPDOWN_MULTISELECT,USER_GROUP_AS_EXPRESSION,RESOURCE_CONSTRAINT_SCOPE_PIPELINE_ENABLED,NG_TEMPLATE_GITX,ELK_HEALTH_SOURCE,NG_ENABLE_LDAP_CHECK,CVNG_METRIC_THRESHOLD,SRM_HOST_SAMPLING_ENABLE,SRM_ENABLE_HEALTHSOURCE_CLOUDWATCH_METRICS","CCM":"CENG_ENABLED,CCM_MICRO_FRONTEND,NODE_RECOMMENDATION_AGGREGATE","CD":"CDNG_ENABLED","CDB":"NG_DASHBOARDS","CHAOS":"CHAOS_ENABLED","CI":"CI_INDIRECT_LOG_UPLOAD","FF":"CFNG_ENABLED","GitOps":"CUSTOM_ARIFACT_NG,SERVICE_DASHBOARD_V2,OPTIMIZED_GIT_FETCH_FILES,MULTI_SERVICE_INFRA,ENV_GROUP,NG_SVC_ENV_REDESIGN,OPTIMIZED_GIT_FETCH_FILES","LICENSE":"NG_LICENSES_ENABLED,VIEW_USAGE_ENABLED","NG":"ENABLE_DEFAULT_NG_EXPERIENCE_FOR_ONPREM,NEXT_GEN_ENABLED,NEW_LEFT_NAVBAR_SETTINGS,SPG_SIDENAV_COLLAPSE","OLDGITSYNC":"USE_OLD_GIT_SYNC","OPA":"OPA_PIPELINE_GOVERNANCE,OPA_FEATURE_GOVERNANCE,OPA_FF_GOVERNANCE","SAMLAutoAccept":"AUTO_ACCEPT_SAML_ACCOUNT_INVITES,PL_NO_EMAIL_FOR_SAML_ACCOUNT_INVITES","SRM":"CVNG_ENABLED,ERROR_TRACKING_ENABLED","STO":"SECURITY,SECURITY_STAGE,STO_CI_PIPELINE_SECURITY,STO_API_V2"}` | Feature Flags |
| featureFlags.ADDITIONAL | string | `""` | Additional Feature Flag |
| featureFlags.Base | string | `"LDAP_SSO_PROVIDER,ASYNC_ARTIFACT_COLLECTION,JIRA_INTEGRATION,AUDIT_TRAIL_UI,GDS_TIME_SERIES_SAVE_PER_MINUTE,STACKDRIVER_SERVICEGUARD,BATCH_SECRET_DECRYPTION,TIME_SERIES_SERVICEGUARD_V2,TIME_SERIES_WORKFLOW_V2,CUSTOM_DASHBOARD,GRAPHQL,CV_FEEDBACKS,LOGS_V2_247,UPGRADE_JRE,LOG_STREAMING_INTEGRATION,NG_HARNESS_APPROVAL,GIT_SYNC_NG,NG_SHOW_DELEGATE,NG_CG_TASK_ASSIGNMENT_ISOLATION,CI_OVERVIEW_PAGE,AZURE_CLOUD_PROVIDER_VALIDATION_ON_DELEGATE,TERRAFORM_AWS_CP_AUTHENTICATION,NG_TEMPLATES,NEW_DEPLOYMENT_FREEZE,HELM_CHART_AS_ARTIFACT,RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION,WEBHOOK_TRIGGER_AUTHORIZATION,GITHUB_WEBHOOK_AUTHENTICATION,CUSTOM_MANIFEST,GIT_ACCOUNT_SUPPORT,AZURE_WEBAPP,PRUNE_KUBERNETES_RESOURCES,LDAP_GROUP_SYNC_JOB_ITERATOR,POLLING_INTERVAL_CONFIGURABLE,APPLICATION_DROPDOWN_MULTISELECT,USER_GROUP_AS_EXPRESSION,RESOURCE_CONSTRAINT_SCOPE_PIPELINE_ENABLED,NG_TEMPLATE_GITX,ELK_HEALTH_SOURCE,NG_ENABLE_LDAP_CHECK,CVNG_METRIC_THRESHOLD,SRM_HOST_SAMPLING_ENABLE,SRM_ENABLE_HEALTHSOURCE_CLOUDWATCH_METRICS"` | Base flags for all modules |
| featureFlags.CCM | string | `"CENG_ENABLED,CCM_MICRO_FRONTEND,NODE_RECOMMENDATION_AGGREGATE"` | CCM Feature Flags |
| featureFlags.CD | string | `"CDNG_ENABLED"` | STO Feature Flags |
| featureFlags.CDB | string | `"NG_DASHBOARDS"` | Custom Dashboard Flags |
| featureFlags.CHAOS | string | `"CHAOS_ENABLED"` | CHAOS Feature Flags |
| featureFlags.CI | string | `"CI_INDIRECT_LOG_UPLOAD"` | STO Feature Flags |
| featureFlags.FF | string | `"CFNG_ENABLED"` | FF Feature Flags |
| featureFlags.GitOps | string | `"CUSTOM_ARIFACT_NG,SERVICE_DASHBOARD_V2,OPTIMIZED_GIT_FETCH_FILES,MULTI_SERVICE_INFRA,ENV_GROUP,NG_SVC_ENV_REDESIGN,OPTIMIZED_GIT_FETCH_FILES"` | GitOps Feature Flags |
| featureFlags.LICENSE | string | `"NG_LICENSES_ENABLED,VIEW_USAGE_ENABLED"` | Licensing flags |
| featureFlags.NG | string | `"ENABLE_DEFAULT_NG_EXPERIENCE_FOR_ONPREM,NEXT_GEN_ENABLED,NEW_LEFT_NAVBAR_SETTINGS,SPG_SIDENAV_COLLAPSE"` | NG Specific Feature Flags |
| featureFlags.OLDGITSYNC | string | `"USE_OLD_GIT_SYNC"` | Disable OLD_GIT_SYNC if ngGitSync is enabled |
| featureFlags.OPA | string | `"OPA_PIPELINE_GOVERNANCE,OPA_FEATURE_GOVERNANCE,OPA_FF_GOVERNANCE"` | OPA |
| featureFlags.SAMLAutoAccept | string | `"AUTO_ACCEPT_SAML_ACCOUNT_INVITES,PL_NO_EMAIL_FOR_SAML_ACCOUNT_INVITES"` | AutoAccept Feature Flags |
| featureFlags.SRM | string | `"CVNG_ENABLED,ERROR_TRACKING_ENABLED"` | SRM Flags |
| featureFlags.STO | string | `"SECURITY,SECURITY_STAGE,STO_CI_PIPELINE_SECURITY,STO_API_V2"` | STO Feature Flags |
| fullnameOverride | string | `""` |  |
| global.ccm.enabled | bool | `false` |  |
| global.cd.enabled | bool | `false` |  |
| global.cg.enabled | bool | `false` |  |
| global.chaos.enabled | bool | `false` |  |
| global.ci.enabled | bool | `false` |  |
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
| global.ff.enabled | bool | `false` |  |
| global.gitops.enabled | bool | `false` |  |
| global.ha | bool | `false` |  |
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
| global.mongoSSL | bool | `false` |  |
| global.ng.enabled | bool | `true` |  |
| global.ngGitSync.enabled | bool | `false` |  |
| global.ngcustomdashboard.enabled | bool | `false` |  |
| global.opa.enabled | bool | `false` |  |
| global.saml.autoaccept | bool | `false` |  |
| global.smtpCreateSecret.SMTP_HOST | string | `""` |  |
| global.smtpCreateSecret.SMTP_PASSWORD | string | `""` |  |
| global.smtpCreateSecret.SMTP_PORT | string | `"465"` |  |
| global.smtpCreateSecret.SMTP_USERNAME | string | `""` |  |
| global.smtpCreateSecret.SMTP_USE_SSL | string | `"true"` |  |
| global.smtpCreateSecret.enabled | bool | `false` |  |
| global.srm.enabled | bool | `false` |  |
| global.sto.enabled | bool | `false` |  |
| global.useImmutableDelegate | string | `"false"` |  |
| image.digest | string | `""` |  |
| image.imagePullSecrets[0] | string | `"gcr-io-secret"` |  |
| image.pullPolicy | string | `"IfNotPresent"` |  |
| image.registry | string | `"docker.io"` |  |
| image.repository | string | `"harness/migrator-signed"` |  |
| image.tag | string | `"100400-000"` |  |
| immutable_delegate_docker_image.image.digest | string | `""` |  |
| immutable_delegate_docker_image.image.registry | string | `"docker.io"` |  |
| immutable_delegate_docker_image.image.repository | string | `"harness/delegate"` |  |
| immutable_delegate_docker_image.image.tag | string | `"22.12.77617"` |  |
| initContainer.image.digest | string | `""` |  |
| initContainer.image.pullPolicy | string | `"IfNotPresent"` |  |
| initContainer.image.registry | string | `"docker.io"` |  |
| initContainer.image.repository | string | `"busybox"` |  |
| initContainer.image.tag | string | `"latest"` |  |
| iteratorConfig.accessRequestHandler.enabled | bool | `true` |  |
| iteratorConfig.accessRequestHandler.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.accessRequestHandler.targetIntervalInSeconds | int | `15` |  |
| iteratorConfig.accessRequestHandler.threadPoolIntervalInSeconds | int | `5` |  |
| iteratorConfig.accessRequestHandler.threadPoolSize | int | `2` |  |
| iteratorConfig.accessRequestHandler.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.alertReconciliation.enabled | bool | `true` |  |
| iteratorConfig.alertReconciliation.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.alertReconciliation.targetIntervalInSeconds | int | `600` |  |
| iteratorConfig.alertReconciliation.threadPoolIntervalInSeconds | int | `60` |  |
| iteratorConfig.alertReconciliation.threadPoolSize | int | `3` |  |
| iteratorConfig.alertReconciliation.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.approvalPolling.enabled | bool | `true` |  |
| iteratorConfig.approvalPolling.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.approvalPolling.targetIntervalInSeconds | int | `60` |  |
| iteratorConfig.approvalPolling.threadPoolIntervalInSeconds | int | `10` |  |
| iteratorConfig.approvalPolling.threadPoolSize | int | `5` |  |
| iteratorConfig.approvalPolling.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.artifactCleanup.enabled | bool | `true` |  |
| iteratorConfig.artifactCleanup.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.artifactCleanup.targetIntervalInSeconds | int | `7200` |  |
| iteratorConfig.artifactCleanup.threadPoolIntervalInSeconds | int | `300` |  |
| iteratorConfig.artifactCleanup.threadPoolSize | int | `5` |  |
| iteratorConfig.artifactCleanup.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.artifactCollection.enabled | bool | `true` |  |
| iteratorConfig.artifactCollection.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.artifactCollection.targetIntervalInSeconds | int | `60` |  |
| iteratorConfig.artifactCollection.threadPoolIntervalInSeconds | int | `10` |  |
| iteratorConfig.artifactCollection.threadPoolSize | int | `20` |  |
| iteratorConfig.artifactCollection.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.barrierInstanceMonitor.enabled | bool | `true` |  |
| iteratorConfig.barrierInstanceMonitor.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.barrierInstanceMonitor.targetIntervalInSeconds | int | `60` |  |
| iteratorConfig.barrierInstanceMonitor.threadPoolIntervalInSeconds | int | `60` |  |
| iteratorConfig.barrierInstanceMonitor.threadPoolSize | int | `2` |  |
| iteratorConfig.barrierInstanceMonitor.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.ceLicenceExpiryProcessor.enabled | bool | `true` |  |
| iteratorConfig.ceLicenceExpiryProcessor.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.ceLicenceExpiryProcessor.targetIntervalInSeconds | int | `86400` |  |
| iteratorConfig.ceLicenceExpiryProcessor.threadPoolIntervalInSeconds | int | `86400` |  |
| iteratorConfig.ceLicenceExpiryProcessor.threadPoolSize | int | `2` |  |
| iteratorConfig.ceLicenceExpiryProcessor.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.delegateDisconnectDetector.enabled | bool | `true` |  |
| iteratorConfig.delegateDisconnectDetector.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.delegateDisconnectDetector.targetIntervalInSeconds | int | `60` |  |
| iteratorConfig.delegateDisconnectDetector.threadPoolIntervalInSeconds | int | `60` |  |
| iteratorConfig.delegateDisconnectDetector.threadPoolSize | int | `2` |  |
| iteratorConfig.delegateDisconnectDetector.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.delegateTaskFail.enabled | bool | `true` |  |
| iteratorConfig.delegateTaskFail.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.delegateTaskFail.targetIntervalInSeconds | int | `30` |  |
| iteratorConfig.delegateTaskFail.threadPoolIntervalInSeconds | int | `30` |  |
| iteratorConfig.delegateTaskFail.threadPoolSize | int | `2` |  |
| iteratorConfig.delegateTaskFail.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.delegateTelemetryPublisherIteration.enabled | bool | `true` |  |
| iteratorConfig.delegateTelemetryPublisherIteration.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.delegateTelemetryPublisherIteration.targetIntervalInSeconds | int | `86400` |  |
| iteratorConfig.delegateTelemetryPublisherIteration.threadPoolIntervalInSeconds | int | `600` |  |
| iteratorConfig.delegateTelemetryPublisherIteration.threadPoolSize | int | `1` |  |
| iteratorConfig.delegateTelemetryPublisherIteration.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.deleteAccountIterator.enabled | bool | `false` |  |
| iteratorConfig.deleteAccountIterator.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.deleteAccountIterator.targetIntervalInSeconds | int | `18000` |  |
| iteratorConfig.deleteAccountIterator.threadPoolIntervalInSeconds | int | `60` |  |
| iteratorConfig.deleteAccountIterator.threadPoolSize | int | `2` |  |
| iteratorConfig.deleteAccountIterator.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.deletedEntityIterator.enabled | bool | `false` |  |
| iteratorConfig.deletedEntityIterator.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.deletedEntityIterator.targetIntervalInSeconds | int | `43200` |  |
| iteratorConfig.deletedEntityIterator.threadPoolIntervalInSeconds | int | `60` |  |
| iteratorConfig.deletedEntityIterator.threadPoolSize | int | `2` |  |
| iteratorConfig.deletedEntityIterator.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.deploymentFreezeActivities.enabled | bool | `true` |  |
| iteratorConfig.deploymentFreezeActivities.nextIterationMode | string | `"THROTTLE"` |  |
| iteratorConfig.deploymentFreezeActivities.targetIntervalInSeconds | int | `0` |  |
| iteratorConfig.deploymentFreezeActivities.threadPoolIntervalInSeconds | int | `0` |  |
| iteratorConfig.deploymentFreezeActivities.threadPoolSize | int | `3` |  |
| iteratorConfig.deploymentFreezeActivities.throttleIntervalInSeconds | int | `45` |  |
| iteratorConfig.deploymentFreezeDeactivation.enabled | bool | `true` |  |
| iteratorConfig.deploymentFreezeDeactivation.nextIterationMode | string | `"THROTTLE"` |  |
| iteratorConfig.deploymentFreezeDeactivation.targetIntervalInSeconds | int | `0` |  |
| iteratorConfig.deploymentFreezeDeactivation.threadPoolIntervalInSeconds | int | `0` |  |
| iteratorConfig.deploymentFreezeDeactivation.threadPoolSize | int | `3` |  |
| iteratorConfig.deploymentFreezeDeactivation.throttleIntervalInSeconds | int | `45` |  |
| iteratorConfig.encryptedDataLocalToGcpKmsMigrationHandler.enabled | bool | `true` |  |
| iteratorConfig.encryptedDataLocalToGcpKmsMigrationHandler.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.encryptedDataLocalToGcpKmsMigrationHandler.targetIntervalInSeconds | int | `72000` |  |
| iteratorConfig.encryptedDataLocalToGcpKmsMigrationHandler.threadPoolIntervalInSeconds | int | `30` |  |
| iteratorConfig.encryptedDataLocalToGcpKmsMigrationHandler.threadPoolSize | int | `5` |  |
| iteratorConfig.encryptedDataLocalToGcpKmsMigrationHandler.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.entityAuditRecordProcessor.enabled | bool | `true` |  |
| iteratorConfig.entityAuditRecordProcessor.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.entityAuditRecordProcessor.targetIntervalInSeconds | int | `1800` |  |
| iteratorConfig.entityAuditRecordProcessor.threadPoolIntervalInSeconds | int | `30` |  |
| iteratorConfig.entityAuditRecordProcessor.threadPoolSize | int | `2` |  |
| iteratorConfig.entityAuditRecordProcessor.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.eventDelivery.enabled | bool | `true` |  |
| iteratorConfig.eventDelivery.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.eventDelivery.targetIntervalInSeconds | int | `5` |  |
| iteratorConfig.eventDelivery.threadPoolIntervalInSeconds | int | `60` |  |
| iteratorConfig.eventDelivery.threadPoolSize | int | `25` |  |
| iteratorConfig.eventDelivery.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.exportExecutionsRequestCleanupHandler.enabled | bool | `true` |  |
| iteratorConfig.exportExecutionsRequestCleanupHandler.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.exportExecutionsRequestCleanupHandler.targetIntervalInSeconds | int | `2700` |  |
| iteratorConfig.exportExecutionsRequestCleanupHandler.threadPoolIntervalInSeconds | int | `3600` |  |
| iteratorConfig.exportExecutionsRequestCleanupHandler.threadPoolSize | int | `2` |  |
| iteratorConfig.exportExecutionsRequestCleanupHandler.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.exportExecutionsRequestHandler.enabled | bool | `true` |  |
| iteratorConfig.exportExecutionsRequestHandler.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.exportExecutionsRequestHandler.targetIntervalInSeconds | int | `1800` |  |
| iteratorConfig.exportExecutionsRequestHandler.threadPoolIntervalInSeconds | int | `60` |  |
| iteratorConfig.exportExecutionsRequestHandler.threadPoolSize | int | `3` |  |
| iteratorConfig.exportExecutionsRequestHandler.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.gcpBilling.enabled | bool | `true` |  |
| iteratorConfig.gcpBilling.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.gcpBilling.targetIntervalInSeconds | int | `3600` |  |
| iteratorConfig.gcpBilling.threadPoolIntervalInSeconds | int | `1800` |  |
| iteratorConfig.gcpBilling.threadPoolSize | int | `2` |  |
| iteratorConfig.gcpBilling.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.gitSyncEntityExpiryCheck.enabled | bool | `true` |  |
| iteratorConfig.gitSyncEntityExpiryCheck.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.gitSyncEntityExpiryCheck.targetIntervalInSeconds | int | `43200` |  |
| iteratorConfig.gitSyncEntityExpiryCheck.threadPoolIntervalInSeconds | int | `600` |  |
| iteratorConfig.gitSyncEntityExpiryCheck.threadPoolSize | int | `2` |  |
| iteratorConfig.gitSyncEntityExpiryCheck.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.gitSyncPollingIterator.enabled | bool | `true` |  |
| iteratorConfig.gitSyncPollingIterator.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.gitSyncPollingIterator.targetIntervalInSeconds | int | `300` |  |
| iteratorConfig.gitSyncPollingIterator.threadPoolIntervalInSeconds | int | `60` |  |
| iteratorConfig.gitSyncPollingIterator.threadPoolSize | int | `2` |  |
| iteratorConfig.gitSyncPollingIterator.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.instanceSync.enabled | bool | `true` |  |
| iteratorConfig.instanceSync.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.instanceSync.targetIntervalInSeconds | int | `600` |  |
| iteratorConfig.instanceSync.threadPoolIntervalInSeconds | int | `30` |  |
| iteratorConfig.instanceSync.threadPoolSize | int | `10` |  |
| iteratorConfig.instanceSync.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.ldapGroupScheduled.enabled | bool | `true` |  |
| iteratorConfig.ldapGroupScheduled.nextIterationMode | string | `"THROTTLE"` |  |
| iteratorConfig.ldapGroupScheduled.targetIntervalInSeconds | int | `0` |  |
| iteratorConfig.ldapGroupScheduled.threadPoolIntervalInSeconds | int | `0` |  |
| iteratorConfig.ldapGroupScheduled.threadPoolSize | int | `8` |  |
| iteratorConfig.ldapGroupScheduled.throttleIntervalInSeconds | int | `45` |  |
| iteratorConfig.licenseExpiryCheck.enabled | bool | `true` |  |
| iteratorConfig.licenseExpiryCheck.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.licenseExpiryCheck.targetIntervalInSeconds | int | `3600` |  |
| iteratorConfig.licenseExpiryCheck.threadPoolIntervalInSeconds | int | `30` |  |
| iteratorConfig.licenseExpiryCheck.threadPoolSize | int | `2` |  |
| iteratorConfig.licenseExpiryCheck.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.perpetualTaskAssignment.enabled | bool | `true` |  |
| iteratorConfig.perpetualTaskAssignment.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.perpetualTaskAssignment.targetIntervalInSeconds | int | `60` |  |
| iteratorConfig.perpetualTaskAssignment.threadPoolIntervalInSeconds | int | `60` |  |
| iteratorConfig.perpetualTaskAssignment.threadPoolSize | int | `5` |  |
| iteratorConfig.perpetualTaskAssignment.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.resourceConstraintBackup.enabled | bool | `true` |  |
| iteratorConfig.resourceConstraintBackup.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.resourceConstraintBackup.targetIntervalInSeconds | int | `30` |  |
| iteratorConfig.resourceConstraintBackup.threadPoolIntervalInSeconds | int | `60` |  |
| iteratorConfig.resourceConstraintBackup.threadPoolSize | int | `10` |  |
| iteratorConfig.resourceConstraintBackup.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.resourceLookupTagLinkSync.enabled | bool | `true` |  |
| iteratorConfig.resourceLookupTagLinkSync.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.resourceLookupTagLinkSync.targetIntervalInSeconds | int | `43200` |  |
| iteratorConfig.resourceLookupTagLinkSync.threadPoolIntervalInSeconds | int | `43200` |  |
| iteratorConfig.resourceLookupTagLinkSync.threadPoolSize | int | `1` |  |
| iteratorConfig.resourceLookupTagLinkSync.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.scheduledTrigger.enabled | bool | `true` |  |
| iteratorConfig.scheduledTrigger.nextIterationMode | string | `"THROTTLE"` |  |
| iteratorConfig.scheduledTrigger.targetIntervalInSeconds | int | `0` |  |
| iteratorConfig.scheduledTrigger.threadPoolIntervalInSeconds | int | `0` |  |
| iteratorConfig.scheduledTrigger.threadPoolSize | int | `8` |  |
| iteratorConfig.scheduledTrigger.throttleIntervalInSeconds | int | `45` |  |
| iteratorConfig.segmentGroupEventJob.enabled | bool | `true` |  |
| iteratorConfig.segmentGroupEventJob.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.segmentGroupEventJob.targetIntervalInSeconds | int | `86400` |  |
| iteratorConfig.segmentGroupEventJob.threadPoolIntervalInSeconds | int | `1800` |  |
| iteratorConfig.segmentGroupEventJob.threadPoolSize | int | `2` |  |
| iteratorConfig.segmentGroupEventJob.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.settingAttributeValidateConnectivity.enabled | bool | `true` |  |
| iteratorConfig.settingAttributeValidateConnectivity.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.settingAttributeValidateConnectivity.targetIntervalInSeconds | int | `10800` |  |
| iteratorConfig.settingAttributeValidateConnectivity.threadPoolIntervalInSeconds | int | `600` |  |
| iteratorConfig.settingAttributeValidateConnectivity.threadPoolSize | int | `5` |  |
| iteratorConfig.settingAttributeValidateConnectivity.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.settingAttributesSecretsMigrationHandler.enabled | bool | `true` |  |
| iteratorConfig.settingAttributesSecretsMigrationHandler.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.settingAttributesSecretsMigrationHandler.targetIntervalInSeconds | int | `1800` |  |
| iteratorConfig.settingAttributesSecretsMigrationHandler.threadPoolIntervalInSeconds | int | `30` |  |
| iteratorConfig.settingAttributesSecretsMigrationHandler.threadPoolSize | int | `2` |  |
| iteratorConfig.settingAttributesSecretsMigrationHandler.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.timeoutEngine.enabled | bool | `true` |  |
| iteratorConfig.timeoutEngine.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.timeoutEngine.targetIntervalInSeconds | int | `10` |  |
| iteratorConfig.timeoutEngine.threadPoolIntervalInSeconds | int | `10` |  |
| iteratorConfig.timeoutEngine.threadPoolSize | int | `5` |  |
| iteratorConfig.timeoutEngine.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.usageMetricsHandler.enabled | bool | `true` |  |
| iteratorConfig.usageMetricsHandler.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.usageMetricsHandler.targetIntervalInSeconds | int | `86400` |  |
| iteratorConfig.usageMetricsHandler.threadPoolIntervalInSeconds | int | `30` |  |
| iteratorConfig.usageMetricsHandler.threadPoolSize | int | `2` |  |
| iteratorConfig.usageMetricsHandler.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.vaultSecretManagerRenewalHandler.enabled | bool | `true` |  |
| iteratorConfig.vaultSecretManagerRenewalHandler.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.vaultSecretManagerRenewalHandler.targetIntervalInSeconds | int | `31` |  |
| iteratorConfig.vaultSecretManagerRenewalHandler.threadPoolIntervalInSeconds | int | `5` |  |
| iteratorConfig.vaultSecretManagerRenewalHandler.threadPoolSize | int | `5` |  |
| iteratorConfig.vaultSecretManagerRenewalHandler.throttleIntervalInSeconds | int | `0` |  |
| iteratorConfig.workflowExecutionMonitor.enabled | bool | `true` |  |
| iteratorConfig.workflowExecutionMonitor.nextIterationMode | string | `"TARGET"` |  |
| iteratorConfig.workflowExecutionMonitor.targetIntervalInSeconds | int | `60` |  |
| iteratorConfig.workflowExecutionMonitor.threadPoolIntervalInSeconds | int | `10` |  |
| iteratorConfig.workflowExecutionMonitor.threadPoolSize | int | `5` |  |
| iteratorConfig.workflowExecutionMonitor.throttleIntervalInSeconds | int | `0` |  |
| maxSurge | int | `1` |  |
| maxUnavailable | int | `0` |  |
| migrator.mongodb.extraArgs | string | `""` |  |
| migrator.mongodb.hosts | list | `[]` |  |
| migrator.mongodb.override | bool | `false` |  |
| migrator.mongodb.passwordKey | string | `""` |  |
| migrator.mongodb.protocol | string | `"mongodb"` |  |
| migrator.mongodb.secretName | string | `""` |  |
| migrator.mongodb.userKey | string | `""` |  |
| mongoSecrets.password.key | string | `"mongodb-root-password"` |  |
| mongoSecrets.password.name | string | `"mongodb-replicaset-chart"` |  |
| mongoSecrets.userName.key | string | `"mongodbUsername"` |  |
| mongoSecrets.userName.name | string | `"harness-secrets"` |  |
| nameOverride | string | `""` |  |
| nodeSelector | object | `{}` |  |
| podAnnotations | object | `{}` |  |
| podSecurityContext | object | `{}` |  |
| replicaCount | int | `1` |  |
| resources.limits.cpu | int | `2` |  |
| resources.limits.memory | string | `"8192Mi"` |  |
| resources.requests.cpu | int | `2` |  |
| resources.requests.memory | string | `"3000Mi"` |  |
| secrets.LOG_STREAMING_SERVICE_TOKEN | string | `"c76e567a-b341-404d-a8dd-d9738714eb82"` |  |
| secrets.MONGO_SSL_CA_TRUST_STORE_PASSWORD | string | `"password"` |  |
| secrets.MONGO_SSL_CA_TRUST_STORE_PATH | string | `"/opt/harness/keystore.jks"` |  |
| secrets.VERIFICATION_SERVICE_SECRET | string | `"59MR5RlVARcdH7zb7pNx6GzqiglBmXR8"` |  |
| securityContext.runAsNonRoot | bool | `true` |  |
| securityContext.runAsUser | int | `65534` |  |
| service.grpcport | int | `9879` |  |
| service.port | int | `9080` |  |
| service.type | string | `"ClusterIP"` |  |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `false` |  |
| serviceAccount.name | string | `"harness-default"` |  |
| timescaleSecret.password.key | string | `"timescaledbPostgresPassword"` |  |
| timescaleSecret.password.name | string | `"harness-secrets"` |  |
| tolerations | list | `[]` |  |
| upgrader_docker_image.image.digest | string | `""` |  |
| upgrader_docker_image.image.registry | string | `"docker.io"` |  |
| upgrader_docker_image.image.repository | string | `"harness/upgrader"` |  |
| upgrader_docker_image.image.tag | string | `"latest"` |  |
| waitForInitContainer.image.digest | string | `""` |  |
| waitForInitContainer.image.imagePullSecrets | list | `[]` |  |
| waitForInitContainer.image.pullPolicy | string | `"IfNotPresent"` |  |
| waitForInitContainer.image.registry | string | `"docker.io"` |  |
| waitForInitContainer.image.repository | string | `"harness/helm-init-container"` |  |
| waitForInitContainer.image.tag | string | `"latest"` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
