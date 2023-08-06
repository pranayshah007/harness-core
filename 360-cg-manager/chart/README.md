# harness-manager

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
| allowedOrigins | string | `""` |  |
| appLogLevel | string | `"INFO"` |  |
| autoscaling.enabled | bool | `false` |  |
| autoscaling.maxReplicas | int | `100` |  |
| autoscaling.minReplicas | int | `1` |  |
| autoscaling.targetCPU | string | `""` |  |
| autoscaling.targetMemory | string | `""` |  |
| delegate_docker_image.image.digest | string | `""` |  |
| delegate_docker_image.image.registry | string | `"docker.io"` |  |
| delegate_docker_image.image.repository | string | `"harness/delegate"` |  |
| delegate_docker_image.image.tag | string | `"latest"` |  |
| external_graphql_rate_limit | string | `"500"` |  |
| extraEnvVars | list | `[]` |  |
| extraVolumeMounts | list | `[]` |  |
| extraVolumes | list | `[]` |  |
| featureFlags | object | `{"ADDITIONAL":"","Base":"LDAP_SSO_PROVIDER,ASYNC_ARTIFACT_COLLECTION,JIRA_INTEGRATION,AUDIT_TRAIL_UI,GDS_TIME_SERIES_SAVE_PER_MINUTE,STACKDRIVER_SERVICEGUARD,BATCH_SECRET_DECRYPTION,TIME_SERIES_SERVICEGUARD_V2,TIME_SERIES_WORKFLOW_V2,CUSTOM_DASHBOARD,GRAPHQL,CV_FEEDBACKS,LOGS_V2_247,UPGRADE_JRE,LOG_STREAMING_INTEGRATION,NG_HARNESS_APPROVAL,GIT_SYNC_NG,NG_SHOW_DELEGATE,NG_CG_TASK_ASSIGNMENT_ISOLATION,CI_OVERVIEW_PAGE,AZURE_CLOUD_PROVIDER_VALIDATION_ON_DELEGATE,TERRAFORM_AWS_CP_AUTHENTICATION,NG_TEMPLATES,NEW_DEPLOYMENT_FREEZE,HELM_CHART_AS_ARTIFACT,RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION,WEBHOOK_TRIGGER_AUTHORIZATION,GITHUB_WEBHOOK_AUTHENTICATION,CUSTOM_MANIFEST,GIT_ACCOUNT_SUPPORT,AZURE_WEBAPP,LDAP_GROUP_SYNC_JOB_ITERATOR,POLLING_INTERVAL_CONFIGURABLE,APPLICATION_DROPDOWN_MULTISELECT,USER_GROUP_AS_EXPRESSION,RESOURCE_CONSTRAINT_SCOPE_PIPELINE_ENABLED,NG_TEMPLATE_GITX,ELK_HEALTH_SOURCE,NG_ENABLE_LDAP_CHECK,CVNG_METRIC_THRESHOLD,SRM_HOST_SAMPLING_ENABLE,SRM_ENABLE_HEALTHSOURCE_CLOUDWATCH_METRICS,NG_SETTINGS","CCM":"CENG_ENABLED,CCM_MICRO_FRONTEND,NODE_RECOMMENDATION_AGGREGATE","CD":"CDS_AUTO_APPROVAL,CDS_NG_TRIGGER_SELECTIVE_STAGE_EXECUTION","CDB":"NG_DASHBOARDS","CET":"CET_ENABLED,SRM_CODE_ERROR_NOTIFICATIONS,SRM_ET_RESOLVED_EVENTS,SRM_ET_CRITICAL_EVENTS","CHAOS":"CHAOS_ENABLED","CI":"CING_ENABLED,CI_INDIRECT_LOG_UPLOAD,CI_LE_STATUS_REST_ENABLED","FF":"CFNG_ENABLED","GitOps":"CUSTOM_ARTIFACT_NG,SERVICE_DASHBOARD_V2,OPTIMIZED_GIT_FETCH_FILES,MULTI_SERVICE_INFRA,ENV_GROUP,NG_SVC_ENV_REDESIGN","LICENSE":"NG_LICENSES_ENABLED,VIEW_USAGE_ENABLED","NG":"ENABLE_DEFAULT_NG_EXPERIENCE_FOR_ONPREM,NEXT_GEN_ENABLED,NEW_LEFT_NAVBAR_SETTINGS,SPG_SIDENAV_COLLAPSE,PL_ENABLE_JIT_USER_PROVISION","OPA":"OPA_PIPELINE_GOVERNANCE","SAMLAutoAccept":"AUTO_ACCEPT_SAML_ACCOUNT_INVITES,PL_NO_EMAIL_FOR_SAML_ACCOUNT_INVITES","SRM":"CVNG_ENABLED","STO":"SECURITY,SECURITY_STAGE,STO_CI_PIPELINE_SECURITY,STO_API_V2"}` | Feature Flags |
| featureFlags.ADDITIONAL | string | `""` | Additional Feature Flag |
| featureFlags.Base | string | `"LDAP_SSO_PROVIDER,ASYNC_ARTIFACT_COLLECTION,JIRA_INTEGRATION,AUDIT_TRAIL_UI,GDS_TIME_SERIES_SAVE_PER_MINUTE,STACKDRIVER_SERVICEGUARD,BATCH_SECRET_DECRYPTION,TIME_SERIES_SERVICEGUARD_V2,TIME_SERIES_WORKFLOW_V2,CUSTOM_DASHBOARD,GRAPHQL,CV_FEEDBACKS,LOGS_V2_247,UPGRADE_JRE,LOG_STREAMING_INTEGRATION,NG_HARNESS_APPROVAL,GIT_SYNC_NG,NG_SHOW_DELEGATE,NG_CG_TASK_ASSIGNMENT_ISOLATION,CI_OVERVIEW_PAGE,AZURE_CLOUD_PROVIDER_VALIDATION_ON_DELEGATE,TERRAFORM_AWS_CP_AUTHENTICATION,NG_TEMPLATES,NEW_DEPLOYMENT_FREEZE,HELM_CHART_AS_ARTIFACT,RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION,WEBHOOK_TRIGGER_AUTHORIZATION,GITHUB_WEBHOOK_AUTHENTICATION,CUSTOM_MANIFEST,GIT_ACCOUNT_SUPPORT,AZURE_WEBAPP,LDAP_GROUP_SYNC_JOB_ITERATOR,POLLING_INTERVAL_CONFIGURABLE,APPLICATION_DROPDOWN_MULTISELECT,USER_GROUP_AS_EXPRESSION,RESOURCE_CONSTRAINT_SCOPE_PIPELINE_ENABLED,NG_TEMPLATE_GITX,ELK_HEALTH_SOURCE,NG_ENABLE_LDAP_CHECK,CVNG_METRIC_THRESHOLD,SRM_HOST_SAMPLING_ENABLE,SRM_ENABLE_HEALTHSOURCE_CLOUDWATCH_METRICS,NG_SETTINGS"` | Base flags for all modules |
| featureFlags.CCM | string | `"CENG_ENABLED,CCM_MICRO_FRONTEND,NODE_RECOMMENDATION_AGGREGATE"` | CCM Feature Flags |
| featureFlags.CD | string | `"CDS_AUTO_APPROVAL,CDS_NG_TRIGGER_SELECTIVE_STAGE_EXECUTION"` | STO Feature Flags |
| featureFlags.CDB | string | `"NG_DASHBOARDS"` | Custom Dashboard Flags |
| featureFlags.CET | string | `"CET_ENABLED,SRM_CODE_ERROR_NOTIFICATIONS,SRM_ET_RESOLVED_EVENTS,SRM_ET_CRITICAL_EVENTS"` | CET Feature Flags |
| featureFlags.CHAOS | string | `"CHAOS_ENABLED"` | CHAOS Feature Flags |
| featureFlags.CI | string | `"CING_ENABLED,CI_INDIRECT_LOG_UPLOAD,CI_LE_STATUS_REST_ENABLED"` | STO Feature Flags |
| featureFlags.FF | string | `"CFNG_ENABLED"` | FF Feature Flags |
| featureFlags.GitOps | string | `"CUSTOM_ARTIFACT_NG,SERVICE_DASHBOARD_V2,OPTIMIZED_GIT_FETCH_FILES,MULTI_SERVICE_INFRA,ENV_GROUP,NG_SVC_ENV_REDESIGN"` | GitOps Feature Flags |
| featureFlags.LICENSE | string | `"NG_LICENSES_ENABLED,VIEW_USAGE_ENABLED"` | Licensing flags |
| featureFlags.NG | string | `"ENABLE_DEFAULT_NG_EXPERIENCE_FOR_ONPREM,NEXT_GEN_ENABLED,NEW_LEFT_NAVBAR_SETTINGS,SPG_SIDENAV_COLLAPSE,PL_ENABLE_JIT_USER_PROVISION"` | NG Specific Feature Flags |
| featureFlags.OPA | string | `"OPA_PIPELINE_GOVERNANCE"` | OPA |
| featureFlags.SAMLAutoAccept | string | `"AUTO_ACCEPT_SAML_ACCOUNT_INVITES,PL_NO_EMAIL_FOR_SAML_ACCOUNT_INVITES"` | AutoAccept Feature Flags |
| featureFlags.SRM | string | `"CVNG_ENABLED"` | SRM Flags |
| featureFlags.STO | string | `"SECURITY,SECURITY_STAGE,STO_CI_PIPELINE_SECURITY,STO_API_V2"` | STO Feature Flags |
| fullnameOverride | string | `""` |  |
| global.ccm.enabled | bool | `false` |  |
| global.cd.enabled | bool | `false` |  |
| global.cet.enabled | bool | `false` |  |
| global.cet.enabled | bool | `false` |  |
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
| global.ff.enabled | bool | `false` |  |
| global.gitops.enabled | bool | `false` |  |
| global.ingress.className | string | `"harness"` | set ingress object classname |
| global.ingress.enabled | bool | `false` | create ingress objects |
| global.ingress.hosts | list | `["my-host.example.org"]` | set host of ingressObjects |
| global.ingress.objects | object | `{"annotations":{}}` | add annotations to ingress objects |
| global.ingress.tls | object | `{"enabled":true,"secretName":""}` | set tls for ingress objects |
| global.istio.enabled | bool | `false` | create virtualServices objects |
| global.istio.gateway | object | `{"create":false}` | create gateway and use in virtualservice |
| global.istio.virtualService | object | `{"gateways":null,"hosts":null}` | if gateway not created, use specified gateway and host |
| global.kubeVersion | string | `""` |  |
| global.license.cg | string | `""` |  |
| global.license.ng | string | `""` |  |
| global.loadbalancerURL | string | `""` |  |
| global.mongoSSL | bool | `false` |  |
| global.ng.enabled | bool | `true` |  |
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
| global.stackDriverLoggingEnabled | bool | `false` |  |
| global.sto.enabled | bool | `false` |  |
| global.useImmutableDelegate | string | `"true"` |  |
| global.useMinimalDelegateImage | bool | `false` |  |
| image.digest | string | `""` |  |
| image.imagePullSecrets | list | `[]` |  |
| image.pullPolicy | string | `"IfNotPresent"` |  |
| image.registry | string | `"docker.io"` |  |
| image.repository | string | `"harness/manager-signed"` |  |
| image.tag | string | `"79819"` |  |
| immutable_delegate_docker_image.image.digest | string | `""` |  |
| immutable_delegate_docker_image.image.registry | string | `"docker.io"` |  |
| immutable_delegate_docker_image.image.repository | string | `"harness/delegate"` |  |
| immutable_delegate_docker_image.image.tag | string | `"23.07.79712"` |  |
| initContainer.image.digest | string | `""` |  |
| initContainer.image.pullPolicy | string | `"IfNotPresent"` |  |
| initContainer.image.registry | string | `"docker.io"` |  |
| initContainer.image.repository | string | `"busybox"` |  |
| initContainer.image.tag | string | `"latest"` |  |
| java.memory | string | `"2048"` |  |
| lifecycleHooks.preStop.exec.command[0] | string | `"touch"` |  |
| lifecycleHooks.preStop.exec.command[1] | string | `"shutdown"` |  |
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
| redisConfig.nettyThreads | string | `"32"` |  |
| replicaCount | int | `1` |  |
| resources.limits.memory | string | `"8192Mi"` |  |
| resources.requests.cpu | int | `2` |  |
| resources.requests.memory | string | `"3000Mi"` |  |
| securityContext.runAsNonRoot | bool | `true` |  |
| securityContext.runAsUser | int | `65534` |  |
| service.grpcport | int | `9879` |  |
| service.port | int | `9090` |  |
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
| version | string | `"1.0.79819"` |  |
| waitForInitContainer.image.digest | string | `""` |  |
| waitForInitContainer.image.imagePullSecrets | list | `[]` |  |
| waitForInitContainer.image.pullPolicy | string | `"IfNotPresent"` |  |
| waitForInitContainer.image.registry | string | `"docker.io"` |  |
| waitForInitContainer.image.repository | string | `"harness/helm-init-container"` |  |
| waitForInitContainer.image.tag | string | `"latest"` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
