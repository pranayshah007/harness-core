{{/*
Generate External Secret CRD with for Harness Installed MongoDB

The generated K8S Secret contains the following keys:
1. mongodb-root-password
2. mongodb-replica-set-key

Note:
If input ESO secrets do not contain the required keys, the corresponding secret keys will be silently ignored in the generated secret

USAGE:
{{ include "harnesssecrets.secrets.generateInstalledMongoExternalSecret" (dict "ctx" $) }}
*/}}
{{- define "harnesssecrets.secrets.generateInstalledMongoExternalSecret" }}
  {{- $ := .ctx }}
  {{- if and $.Values.global.database.mongo.installed (eq (len $.Values.global.database.mongo.secrets.secretManagement.externalSecretsOperator) 1) (eq (include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" $.Values.global.database.mongo.secrets)) "true") }}
    {{- $esoSecretName := "mongo-ext-secret" }}
    {{- $mongoPasswordKey := "MONGO_PASSWORD" }}
    {{- $mongoReplicaSetKey := "MONGO_REPLICA_SET_KEY" }}
    {{- $installedMongoSecretKeys := dict $mongoPasswordKey "mongodb-root-password" $mongoReplicaSetKey "mongodb-replica-set-key" }}
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

{{/*
Generate External Secret CRD with for Harness Installed Postgres

The generated K8S Secret contains the following keys:
1. mongodb-root-password
2. mongodb-replica-set-key

Note:
If input ESO secrets do not contain the required keys, the corresponding secret keys will be silently ignored in the generated secret

USAGE:
{{ include "harnesssecrets.secrets.generateInstalledPostgresExternalSecret" (dict "ctx" $) }}
*/}}
{{- define "harnesssecrets.secrets.generateInstalledPostgresExternalSecret" }}
  {{- $ := .ctx }}
  {{- if and $.Values.global.database.postgres.installed (eq (len $.Values.global.database.postgres.secrets.secretManagement.externalSecretsOperator) 1) (eq (include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" $.Values.global.database.postgres.secrets)) "true") }}
    {{- $esoSecretName := "postgres-ext-secret" }}
    {{- $postgresPasswordKey := "POSTGRES_PASSWORD" }}
    {{- $installedPostgresSecretKeys := dict $postgresPasswordKey "postgres-password" }}
    {{- $esoSecret := first $.Values.global.database.postgres.secrets.secretManagement.externalSecretsOperator }}
    {{- if and (dig "secretStore" "name" "" $esoSecret) (dig "secretStore" "kind" "" $esoSecret) (or (dig $postgresPasswordKey "name" "" $esoSecret.remoteKeys)) }}
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
          {{- if and (hasKey $installedPostgresSecretKeys $remoteKeyName) (not (empty $remoteKey.name)) }}
          {{- $esoSecretKeyName := (get $installedPostgresSecretKeys $remoteKeyName) }}
        {{ $esoSecretKeyName }}: "{{ printf "{{ .%s }}" (lower $remoteKeyName) }}"
          {{- end }}
        {{- end }}
  data:
    {{- range $remoteKeyName, $remoteKey := $esoSecret.remoteKeys }}
    {{- if and (hasKey $installedPostgresSecretKeys $remoteKeyName) (not (empty $remoteKey.name)) }}
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

{{/*
Generate External Secret CRD with for Harness Installed Minio

The generated K8S Secret contains the following keys:
1. mongodb-root-password
2. mongodb-replica-set-key

Note:
If input ESO secrets do not contain the required keys, the corresponding secret keys will be silently ignored in the generated secret

USAGE:
{{ include "harnesssecrets.secrets.generateInstalledMinioExternalSecret" (dict "ctx" $) }}
*/}}
{{- define "harnesssecrets.secrets.generateInstalledMinioExternalSecret" }}
  {{- $ := .ctx }}
  {{- if and $.Values.global.database.minio.installed (eq (len $.Values.global.database.minio.secrets.secretManagement.externalSecretsOperator) 1) (eq (include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" $.Values.global.database.minio.secrets)) "true") }}
    {{- $esoSecretName := "minio-ext-secret" }}
    {{- $minioUserKey := "S3_USER" }}
    {{- $minioPasswordKey := "S3_PASSWORD" }}
    {{- $installedMinioSecretKeys := dict $minioUserKey "root-user" $minioPasswordKey "root-password" }}
    {{- $esoSecret := first $.Values.global.database.minio.secrets.secretManagement.externalSecretsOperator }}
    {{- if and (dig "secretStore" "name" "" $esoSecret) (dig "secretStore" "kind" "" $esoSecret) (or (dig $minioUserKey "name" "" $esoSecret.remoteKeys) (dig $minioPasswordKey "name" "" $esoSecret.remoteKeys)) }}
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
          {{- if and (hasKey $installedMinioSecretKeys $remoteKeyName) (not (empty $remoteKey.name)) }}
          {{- $esoSecretKeyName := (get $installedMinioSecretKeys $remoteKeyName) }}
        {{ $esoSecretKeyName }}: "{{ printf "{{ .%s }}" (lower $remoteKeyName) }}"
          {{- end }}
        {{- end }}
  data:
    {{- range $remoteKeyName, $remoteKey := $esoSecret.remoteKeys }}
    {{- if and (hasKey $installedMinioSecretKeys $remoteKeyName) (not (empty $remoteKey.name)) }}
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

{{/*
Generate External Secret CRD with for Harness Installed Clickhouse

The generated K8S Secret contains the following keys:
1. mongodb-root-password
2. mongodb-replica-set-key

Note:
If input ESO secrets do not contain the required keys, the corresponding secret keys will be silently ignored in the generated secret

USAGE:
{{ include "harnesssecrets.secrets.generateInstalledClickhouseExternalSecret" (dict "ctx" $) }}
*/}}
{{- define "harnesssecrets.secrets.generateInstalledClickhouseExternalSecret" }}
  {{- $ := .ctx }}
  {{- if and $.Values.global.database.clickhouse.installed (eq (len $.Values.global.database.clickhouse.secrets.secretManagement.externalSecretsOperator) 1) (eq (include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" $.Values.global.database.clickhouse.secrets)) "true") }}
    {{- $esoSecretName := "clickhouse-ext-secret" }}
    {{- $clickhousePasswordKey := "CLICKHOUSE_PASSWORD" }}
    {{- $installedClickhouseSecretKeys := dict $clickhousePasswordKey "admin-password" }}
    {{- $esoSecret := first $.Values.global.database.clickhouse.secrets.secretManagement.externalSecretsOperator }}
    {{- if and (dig "secretStore" "name" "" $esoSecret) (dig "secretStore" "kind" "" $esoSecret) (or (dig $clickhousePasswordKey "name" "" $esoSecret.remoteKeys)) }}
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
          {{- if and (hasKey $installedClickhouseSecretKeys $remoteKeyName) (not (empty $remoteKey.name)) }}
          {{- $esoSecretKeyName := (get $installedClickhouseSecretKeys $remoteKeyName) }}
        {{ $esoSecretKeyName }}: "{{ printf "{{ .%s }}" (lower $remoteKeyName) }}"
          {{- end }}
        {{- end }}
  data:
    {{- range $remoteKeyName, $remoteKey := $esoSecret.remoteKeys }}
    {{- if and (hasKey $installedClickhouseSecretKeys $remoteKeyName) (not (empty $remoteKey.name)) }}
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

{{/*
Generate External Secret CRD with for Harness Installed Clickhouse

The generated K8S Secret contains the following keys:
1. PATRONI_SUPERUSER_PASSWORD
2. PATRONI_REPLICATION_PASSWORD
3. PATRONI_admin_PASSWORD

Note:
If input ESO secrets do not contain the required keys, the corresponding secret keys will be silently ignored in the generated secret

USAGE:
{{ include "harnesssecrets.secrets.generateInstalledTimescaleDBExternalSecret" (dict "ctx" $) }}
*/}}
{{- define "harnesssecrets.secrets.generateInstalledTimescaleDBExternalSecret" }}
  {{- $ := .ctx }}
  {{- if and $.Values.global.database.timescaledb.installed (eq (len $.Values.global.database.timescaledb.secrets.secretManagement.externalSecretsOperator) 1) (eq (include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" $.Values.global.database.timescaledb.secrets)) "true") }}
    {{- $esoSecretName := "timescaledb-ext-secret" }}
    {{- $timescaledbPatroniSuperuserPasswordKey := "TIMESCALEDB_PASSWORD" }}
    {{- $timescaledbPatroniReplicationPasswordKey := "TIMESCALEDB_REPLICATION_PASSWORD" }}
    {{- $timescaledbPatroniAdminPasswordKey := "TIMESCALEDB_ADMIN_PASSWORD" }}
    {{- $installedTimescaledbSecretKeys := dict $timescaledbPatroniSuperuserPasswordKey "PATRONI_SUPERUSER_PASSWORD" $timescaledbPatroniReplicationPasswordKey "PATRONI_REPLICATION_PASSWORD" $timescaledbPatroniAdminPasswordKey "PATRONI_admin_PASSWORD" }}
    {{- $esoSecret := first $.Values.global.database.timescaledb.secrets.secretManagement.externalSecretsOperator }}
    {{- if and (dig "secretStore" "name" "" $esoSecret) (dig "secretStore" "kind" "" $esoSecret) (or (dig $timescaledbPatroniSuperuserPasswordKey "name" "" $esoSecret.remoteKeys) (dig $timescaledbPatroniReplicationPasswordKey "name" "" $esoSecret.remoteKeys) (dig $timescaledbPatroniAdminPasswordKey "name" "" $esoSecret.remoteKeys)) }}
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
          {{- if and (hasKey $installedTimescaledbSecretKeys $remoteKeyName) (not (empty $remoteKey.name)) }}
          {{- $esoSecretKeyName := (get $installedTimescaledbSecretKeys $remoteKeyName) }}
        {{ $esoSecretKeyName }}: "{{ printf "{{ .%s }}" (lower $remoteKeyName) }}"
          {{- end }}
        {{- end }}
  data:
    {{- range $remoteKeyName, $remoteKey := $esoSecret.remoteKeys }}
    {{- if and (hasKey $installedTimescaledbSecretKeys $remoteKeyName) (not (empty $remoteKey.name)) }}
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
