{{- define "harnesssecrets.generateSecrets" }}
{{- $timescaledbAdminPassword := include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-secrets" "key" "timescaledbAdminPassword" "providedValues" (list "timescaledb.adminPassword") "length" 16 "context" $) }}
{{- $timescaledbPostgresPassword := include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-secrets" "key" "timescaledbPostgresPassword" "providedValues" (list "timescaledb.postgresPassword") "length" 16 "context" $) }}
{{- $timescaledbStandbyPassword := include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-secrets" "key" "timescaledbStandbyPassword" "providedValues" (list "timescaledb.standbyPassword") "length" 16  "context" $) }}
    mongodbUsername: YWRtaW4=
    mongodbPassword: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-secrets" "key" "mongodbPassword" "providedValues" (list "mongodb.password") "length" 16 "context" $) }}
    postgresdbAdminPassword: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-secrets" "key" "postgresdbAdminPassword" "providedValues" (list "postgresdb.adminPassword") "length" 16 "context" $) }}
    stoAppHarnessToken:  {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-secrets" "key" "stoAppHarnessToken" "providedValues" (list "sto.appHarnessToken") "length" 16 "context" $) }}
    stoAppAuditJWTSecret:  {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-secrets" "key" "stoAppAuditJWTSecret" "providedValues" (list "sto.appAuditJWTSecret") "length" 16 "context" $) }}
    timescaledbAdminPassword: {{ $timescaledbAdminPassword }}
    timescaledbPostgresPassword: {{ $timescaledbPostgresPassword }}
    timescaledbStandbyPassword:  {{ $timescaledbStandbyPassword }}
    PATRONI_SUPERUSER_PASSWORD: {{ $timescaledbPostgresPassword }}
    PATRONI_REPLICATION_PASSWORD: {{ $timescaledbStandbyPassword }}
    PATRONI_admin_PASSWORD: {{ $timescaledbAdminPassword }}
{{- end }}

{{- define "harnesssecrets.generateMinioSecrets" }}
    root-user: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "minio" "key" "root-user" "providedValues" (list "minio.rootUser") "length" 10 "context" $) }}
    root-password: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "minio" "key" "root-password" "providedValues" (list "minio.rootPassword") "length" 10 "context" $) }}
{{- end }}

{{- define "harnesssecrets.generateMinioSecretsAgain" }}
{{- if .Values.minio.rootUser }}
root-user: {{ .Values.minio.rootUser | b64enc | quote }}
{{- else}}
root-user: {{ randAlphaNum 10 | quote }}
{{- end -}}
{{- if .Values.minio.rootPassword }}
root-password: {{ .Values.minio.rootPassword }}
{{- else}}
root-password: {{ randAlphaNum 10 | quote }}
{{- end -}}
{{- end }}

{{- define "harnesssecrets.generateMongoSecrets" }}
    mongodb-replica-set-key: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "mongodb-replicaset-chart" "key" "mongodb-replica-set-key" "providedValues" (list "mongo.replicaSetKey") "length" 10 "context" $) }}
    mongodb-root-password: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "mongodb-replicaset-chart" "key" "mongodb-root-password" "providedValues" (list "mongo.rootPassword") "length" 10 "context" $) }}
{{- end }}

{{- define "harnesssecrets.generatePostgresSecrets" }}
    postgres-password: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "postgres" "key" "postgres-password" "providedValues" (list "postgres.postgresPassword") "length" 10 "context" $) }}
{{- end }}

{{- define "getDefaultOrRandom" }}
{{- if .Default }}
{{- printf "%s" .Default}}
{{- else }}
{{- randAlphaNum .Length}}
{{- end}}
{{- end }}
{{- define "harnesssecrets.generateSmtpSecrets" }}
    SMTP_HOST: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "smtp-secret" "key" "SMTP_HOST" "providedValues" (list "global.smtpCreateSecret.SMTP_HOST") "length" 10 "context" $) }}
    SMTP_PORT: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "smtp-secret" "key" "SMTP_PORT" "providedValues" (list "global.smtpCreateSecret.SMTP_PORT") "length" 10 "context" $) }}
    SMTP_USERNAME: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "smtp-secret" "key" "SMTP_USERNAME" "providedValues" (list "global.smtpCreateSecret.SMTP_USERNAME") "length" 10 "context" $) }}
    SMTP_PASSWORD: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "smtp-secret" "key" "SMTP_PASSWORD" "providedValues" (list "global.smtpCreateSecret.SMTP_PASSWORD") "length" 10 "context" $) }}
    SMTP_USE_SSL: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "smtp-secret" "key" "SMTP_USE_SSL" "providedValues" (list "global.smtpCreateSecret.SMTP_USE_SSL") "length" 10 "context" $) }}
{{- end }}

{{- define "harnesssecrets.generateClickhouseSecrets" }}
    admin-password: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "clickhouse" "key" "admin-password" "providedValues" (list "clickhouse.adminPassword") "length" 10 "context" $) }}
{{- end }}

{{/*
Generate External Secret CRD with for Harness Installed MongoDB

The generated K8S Secret contains the following keys:
1. mongodb-root-password
2. mongodb-replica-set-key

Note:
If input ESO secrets do not contain the required keys, the corresponding secret keys will be silently ignored in the generated secret

USAGE:
{{ include "harnesscommon.secrets.generateInstalledMongoExternalSecret" (dict "ctx" $) }}
*/}}
{{- define "harnesscommon.secrets.generateInstalledMongoExternalSecret" }}
  {{- $ := .ctx }}
  {{- if and $.Values.global.database.mongo.installed (eq (include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" $.Values.global.database.mongo.secrets)) "true") (eq (len $.Values.global.database.mongo.secrets.secretManagement.externalSecretsOperator) 1) }}
    {{- $esoSecretName := "mongo-ext-secret" }}
    {{- $mongoPasswordKey := "MONGO_PASSWORD" }}
    {{- $mongoReplicaSetKey := "MONGO_REPLICA_SET_KEY" }}
    {{- $installedMongoSecretKeys := dict $mongoPasswordKey "mongodb-root-password" $mongoReplicaSetKey "mongodb-replica-set-key" -}}
    {{- $esoSecret := first $.Values.global.database.mongo.secrets.secretManagement.externalSecretsOperator }}
    {{- if and (dig "secretStore" "name" "" $esoSecret) (dig "secretStore" "kind" "" $esoSecret) (or (dig $mongoPasswordKey "name" "" $esoSecret.remoteKeys) (dig $mongoReplicaSetKey "name" "" $esoSecret.remoteKeys)) }}
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: {{ $esoSecretName }}
spec:
  secretStoreRef:
    name: {{ $esoSecret.secretStore.name }}
    kind: {{ $esoSecret.secretStore.kind }}
  target:
    name: {{ $esoSecretName }}
    template:
      engineVersion: v2
      mergePolicy: Replace
      data:
        {{- range $remoteKeyName, $remoteKey := $esoSecret.remoteKeys }}
          {{- if and (hasKey $installedMongoSecretKeys $remoteKeyName) (not (empty $remoteKey.name)) }}
          {{- $esoSecretKeyName := (get $installedMongoSecretKeys $remoteKeyName) }}
        {{ $esoSecretKeyName }}: "{{ printf "{{ .%s }}" (lower $remoteKeyName) }}"
          {{- end }}
        {{- end }}
  data:
    {{- range $remoteKeyName, $remoteKey := $esoSecret.remoteKeys }}
    {{- if and (hasKey $installedMongoSecretKeys $remoteKeyName) (not (empty $remoteKey.name)) }}
  - secretKey: {{ lower $remoteKeyName }}
    remoteRef:
      key: {{ $remoteKey.name }}
      {{- if not (empty $remoteKey.property) }}
      property: {{ $remoteKey.property }}
      {{- end }}
    {{- end }}
    {{- end }}
  {{- end }}
  {{- end }}
{{- end }}
