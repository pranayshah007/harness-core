{{/*
Generate DB ESO Context Identifier 

USAGE:
{{- include "harnesscommon.dbv3.esoSecretCtxIdentifier" (dict "ctx" $ "dbType" "mongo" "scope" "" "instanceName" "") }}

PARAMETERS:
- ctx: Context
- dbType: DB Type. Allowed values: mongo, timescaledb
- scope: Scope of ESO Secret Context Identifier. Allowed Values: "local", "global"
- instanceName: Name of the DB Instance. Required only when "scope" if "local"

*/}}
{{- define "harnesscommon.dbv3.esoSecretCtxIdentifier" }}
    {{- $ := .ctx }}
    {{- $dbTypeAllowedValues := dict "mongo" "" "timescaledb" "" }}
    {{- $dbType := .dbType }}
    {{- if not (hasKey $dbTypeAllowedValues $dbType) }}
        {{- $errMsg := printf "ERROR: invalid value %s for input argument dbType" $dbType }}
        {{- fail $errMsg }}
    {{- end }}
    {{- $scope := .scope -}}
    {{- $esoSecretCtxIdentifier := "" }}
    {{- if eq $scope "local" }}
        {{- $instanceName := .instanceName }}
        {{- if empty $instanceName }}
            {{- fail "ERROR: missing input argument - instanceName" }}
        {{- end }}
        {{- $instanceName =  lower $instanceName }}
        {{- $esoSecretCtxIdentifier = (include "harnesscommon.secrets.localESOSecretCtxIdentifier" (dict "ctx" $ "additionalCtxIdentifier" (printf "%s-%s" $instanceName $dbType) )) }}
    {{- else if eq $scope "global" }}
        {{- $esoSecretCtxIdentifier = (include "harnesscommon.secrets.globalESOSecretCtxIdentifier" (dict "ctx" $ "ctxIdentifier" $dbType )) }}
    {{- else }}
        {{- $errMsg := printf "ERROR: invalid value %s for input argument scope" $scope }}
        {{- fail $errMsg }}
    {{- end }}
    {{- printf "%s" $esoSecretCtxIdentifier }}
{{- end }}

{{/*
Generate K8S Env Spec for MongoDB Environment Variables

USAGE:
{{- include "harnesscommon.dbv3.mongoEnv" (dict "ctx" $ "instanceName" "harness") | indent 12 }}

PARAMETERS:
REQUIRED:
1. ctx
2. instanceName

*/}}
{{- define "harnesscommon.dbv3.mongoEnv" }}
    {{- $ := .ctx }}
    {{- $instanceName := .instanceName }}
    {{- if empty $instanceName }}
        {{- fail "ERROR: missing input argument - instanceName" }}
    {{- end }}
    {{- $localDBCtx := get $.Values.mongo $instanceName }}
    {{- $globalDBCtx := $.Values.global.database.mongo }}
    {{- if and $ $localDBCtx $globalDBCtx }}
        {{- $userNameEnvName := dig "envConfig" "username" "envName" "" $localDBCtx }}
        {{- $passwordEnvName := dig "envConfig" "password" "envName" "" $localDBCtx }}
        {{- $localEnabled := dig "enabled" false $localDBCtx }}
        {{- $installed := dig "installed" true $globalDBCtx }}
        {{- $globalDBESOSecretIdentifier := include "harnesscommon.dbv3.esoSecretCtxIdentifier" (dict "ctx" $ "dbType" "mongo" "scope" "global") }}
        {{- $localDBESOSecretCtxIdentifier := include "harnesscommon.dbv3.esoSecretCtxIdentifier" (dict "ctx" $ "dbType" "mongo" "scope" "local" "instanceName" $instanceName) }}
        {{- if $userNameEnvName }}
            {{- if $localEnabled }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_USER"  "overrideEnvName" $userNameEnvName "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $localDBESOSecretCtxIdentifier "secretCtx" $localDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- else if $installed }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_USER"  "overrideEnvName" $userNameEnvName "defaultKubernetesSecretName" "harness-secrets" "defaultKubernetesSecretKey" "mongodbUsername" "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- else }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_USER"  "overrideEnvName" $userNameEnvName "defaultKubernetesSecretName" $globalDBCtx.secretName "defaultKubernetesSecretKey" $globalDBCtx.userKey "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets $localDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- end }}
        {{- end }}
        {{- if $passwordEnvName }}
            {{- if $localEnabled }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_PASSWORD" "overrideEnvName" $passwordEnvName "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $localDBESOSecretCtxIdentifier "secretCtx" $localDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- else if $installed }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_PASSWORD" "overrideEnvName" $passwordEnvName "defaultKubernetesSecretName" "mongodb-replicaset-chart" "defaultKubernetesSecretKey" "mongodb-root-password" "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- else }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_PASSWORD" "overrideEnvName" $passwordEnvName "defaultKubernetesSecretName" $globalDBCtx.secretName "defaultKubernetesSecretKey" $globalDBCtx.passwordKey "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- end }}
        {{- end }}
    {{- else }}
        {{- fail (printf "ERROR: invalid contexts") }}
    {{- end }}
{{- end }}

{{/*
Generate K8S Env Spec for MongoDB Connection Environment Variables

USAGE:
{{ include "harnesscommon.dbv3.mongoConnectionEnv" (dict "ctx" $ "instanceName" "harness") | indent 12 }}

PARAMETERS:
REQUIRED:
1. ctx
2. instanceName

*/}}
{{- define "harnesscommon.dbv3.mongoConnectionEnv" }}
    {{- $ := .ctx }}
    {{- $instanceName := .instanceName }}
    {{- if empty $instanceName }}
        {{- fail "ERROR: missing input argument - instanceName" }}
    {{- end }}
    {{- $localDBCtx := get $.Values.mongo $instanceName }}
    {{- $globalDBCtx := $.Values.global.database.mongo }}
    {{- $database := dig "database" "" $localDBCtx }}
    {{- if and $ $localDBCtx $globalDBCtx $database }}
        {{- $userNameEnvName := dig "envConfig" "username" "envName" "" $localDBCtx }}
        {{- $passwordEnvName := dig "envConfig" "password" "envName" "" $localDBCtx }}
        {{- $connectionURIEnvName := dig "envConfig" "connectionURI" "envName" "" $localDBCtx }}
        {{- $localEnabled := dig "enabled" false $localDBCtx }}
        {{- $installed := dig "installed" true $globalDBCtx }}
        {{- $connectionURI := "" }}
        {{- if $connectionURIEnvName }}
            {{- if $localEnabled }}
                {{- $hosts := $localDBCtx.hosts }}
                {{- $protocol := $localDBCtx.protocol }}
                {{- $extraArgs := $localDBCtx.extraArgs }}
                {{- $args := (printf "/%s?%s" $database $extraArgs ) -}}
                {{- $connectionURI = include "harnesscommon.dbconnection.connection" (dict "type" "mongo" "hosts" $hosts "protocol" $protocol "extraArgs" $args "userVariableName" $userNameEnvName "passwordVariableName" $passwordEnvName)}}
            {{- else if $installed }}
                {{- $namespace := $.Release.Namespace }}
                {{- if $.Values.global.ha }}
                    {{- $connectionURI = printf "'mongodb://$(%s):$(%s)@mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-1.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-2.mongodb-replicaset-chart.%s.svc:27017/%s?replicaSet=rs0&authSource=admin'" $userNameEnvName $passwordEnvName $namespace $namespace $namespace $database }}
                {{- else }}
                    {{- $connectionURI = printf "'mongodb://$(%s):$(%s)@mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc/%s?authSource=admin'" $userNameEnvName $passwordEnvName $namespace $database }}
                {{- end }}
            {{- else }}
                {{- $hosts := $globalDBCtx.hosts }}
                {{- $protocol := $globalDBCtx.protocol }}
                {{- $extraArgs := $globalDBCtx.extraArgs }}
                {{- $args := (printf "/%s?%s" $database $extraArgs ) -}}
                {{- $connectionURI = include "harnesscommon.dbconnection.connection" (dict "type" "mongo" "hosts" $hosts "protocol" $protocol "extraArgs" $args "userVariableName" $userNameEnvName "passwordVariableName" $passwordEnvName)}}
            {{- end }}
- name: {{ printf "%s" $connectionURIEnvName }}
  value: {{ printf "%s" $connectionURI }}
        {{- end }}
    {{- else }}
        {{- fail (printf "ERROR: invalid contexts") }}
    {{- end }}
{{- end }}

{{/*
Generate External Secret CRDs for Mongo DBs

USAGE:
{{- include "harnesscommon.dbv3.manageMongoExternalSecret" (dict "ctx" $) | indent 12 }}

PARAMETERS:
REQUIRED:
1. ctx

*/}}
{{- define "harnesscommon.dbv3.generateLocalMongoExternalSecret" }}
    {{- $ := .ctx }}
    {{- range $instanceName, $instance := $.Values.mongo }}
        {{- $localDBCtx := get $.Values.mongo $instanceName }}
        {{- $localEnabled := dig "enabled" false $localDBCtx }}
        {{- if and $localEnabled (eq (include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" $localDBCtx.secrets)) "true") }}
            {{- $localMongoESOSecretCtxIdentifier := include "harnesscommon.dbv3.mongoESOSecretCtxIdentifier" (dict "ctx" $ "scope" "local" "instanceName" $instanceName) }}
            {{- include "harnesscommon.secrets.generateExternalSecret" (dict "secretsCtx" $localDBCtx.secrets "secretNamePrefix" $localMongoESOSecretCtxIdentifier) }}
            {{- print "\n---" }}
        {{- end }}
    {{- end }}
{{- end }}


{{/*
Generate K8S Env Spec for all MongoDB Environment Variables

USAGE:
{{- include "harnesscommon.dbv3.manageMongoEnvs" (dict "ctx" $) | indent 12 }}

PARAMETERS:
REQUIRED:
1. ctx

*/}}
{{- define "harnesscommon.dbv3.manageMongoEnvs" }}
    {{- $ := .ctx }}
    {{- range $instanceName, $instance := $.Values.mongo }}
        {{- include "harnesscommon.dbv3.mongoEnv" (dict "ctx" $ "instanceName" $instanceName)}}
        {{- include "harnesscommon.dbv3.mongoConnectionEnv" (dict "ctx" $ "instanceName" $instanceName)}}
    {{- end }}
{{- end }}

{{/*
Generate K8S Env Spec for TimescaleDB Environment Variables

USAGE:
{{- include "harnesscommon.dbv3.timescaleEnv" (dict "ctx" $ "instanceName" "harness") | indent 12 }}

PARAMETERS:
REQUIRED:
1. ctx
2. instanceName

Generates TimescaleDB environment variables

USAGE:
{{ include "harnesscommon.dbconnectionv2.timescaleEnv" (dict "ctx" $ "userVariableName" "TIMESCALEDB_USERNAME" "passwordVariableName" "TIMESCALEDB_PASSWORD" "sslModeVariableName" "TIMESCALEDB_SSL_MODE" "certVariableName" "TIMESCALEDB_SSL_ROOT_CERT" "localTimescaleDBCtx" .Values.timescaledb "globalTimescaleDBCtx" .Values.global.database.timescaledb) | indent 12 }}

INPUT ARGUMENTS:
REQUIRED:
1. ctx

OPTIONAL:
1. localTimescaleDBCtx
    DEFAULT: $.Values.timescaledb
2. globalTimescaleDBCtx
    DEFAULT: $.Values.global.database.timescaledb
3. userVariableName
    DEFAULT: TIMESCALEDB_USERNAME
4. passwordVariableName
    DEFAULT: TIMESCALEDB_PASSWORD
5. sslModeVariableName
6. sslModeValue
7. certVariableName 
8. certPathVariableName
9. certPathValue


*/}}
{{- define "harnesscommon.dbv3.timescaleEnv" }}
    {{- $ := .ctx }}
    {{- $dbType := "timescaledb" }}
    {{- $instanceName := .instanceName }}
    {{- if empty $instanceName }}
        {{- fail "ERROR: missing input argument - instanceName" }}
    {{- end }}
    {{- $localDBCtx := get $.Values.timescaledb $instanceName }}
    {{- $globalDBCtx := $.Values.global.database.timescaledb }}
    {{- if and $ $localDBCtx $globalDBCtx }}
        {{- $userNameEnvName := dig "envConfig" "username" "envName" "" $localDBCtx }}
        {{- $passwordEnvName := dig "envConfig" "password" "envName" "" $localDBCtx }}
        {{- $certEnvName := dig "envConfig" "cert" "envName" "" $localDBCtx }}
        {{- $enableSSLEnvName := dig "envConfig" "enableSSL" "envName" "" $localDBCtx }}
        {{- $sslModeEnvName := dig "envConfig" "sslMode" "envName" "" $localDBCtx }}
        {{- $certPathEnvName := dig "envConfig" "certPath" "envName" "" $localDBCtx }}
        {{- $localEnabled := dig "enabled" false $localDBCtx }}
        {{- $installed := dig "installed" true $globalDBCtx }}
        {{- $globalDBESOSecretIdentifier := include "harnesscommon.dbv3.esoSecretCtxIdentifier" (dict "ctx" $ "dbType" $dbType "scope" "global") }}
        {{- $localDBESOSecretCtxIdentifier := include "harnesscommon.dbv3.esoSecretCtxIdentifier" (dict "ctx" $ "dbType" $dbType "scope" "local" "instanceName" $instanceName) }}
        {{- if $userNameEnvName }}
            {{- if $localEnabled }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "TIMESCALEDB_USERNAME"  "overrideEnvName" $userNameEnvName "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $localDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- else if $installed }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "TIMESCALEDB_USERNAME" "overrideEnvName" $userNameEnvName "defaultValue" "postgres" "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- else }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "TIMESCALEDB_USERNAME" "overrideEnvName" $userNameEnvName "defaultKubernetesSecretName" $globalDBCtx.secretName "defaultKubernetesSecretKey" $globalDBCtx.userKey "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- end }}
        {{- end }}
        {{- if $passwordEnvName }}
            {{- if $localEnabled }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "TIMESCALEDB_PASSWORD" "overrideEnvName" $passwordEnvName "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $localDBESOSecretCtxIdentifier "secretCtx" $localDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- else if $installed }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "TIMESCALEDB_PASSWORD" "overrideEnvName" $passwordEnvName "defaultKubernetesSecretName" "harness-secrets" "defaultKubernetesSecretKey" "timescaledbPostgresPassword" "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- else }}
                {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "TIMESCALEDB_PASSWORD" "overrideEnvName" $passwordEnvName "defaultKubernetesSecretName" $globalDBCtx.secretName "defaultKubernetesSecretKey" $globalDBCtx.passwordKey "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- end }}
        {{- end }}
        {{- $sslEnabled := false }}
        {{- $certEnv := "" }}
        {{- if $localEnabled }}
            {{- $sslEnabled = dig "sslEnabled" false $localDBCtx }}
            {{- if $sslEnabled }}
                {{- if $certEnvName }}
                    {{- $certEnv = include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "TIMESCALEDB_SSL_ROOT_CERT" "overrideEnvName" $certEnvName "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $localDBESOSecretCtxIdentifier "secretCtx" $localDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
                {{- end }}
            {{- end }}
        {{- else if $installed }}
            {{- $sslEnabled = dig "sslEnabled" false $globalDBCtx }}
            {{- if $sslEnabled }}
                {{- if $certEnvName }}
                    {{- $certEnv = include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "TIMESCALEDB_SSL_ROOT_CERT" "overrideEnvName" $certEnvName "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
                {{- end }}
            {{- end }}
        {{- else }}
            {{- $sslEnabled = dig "sslEnabled" false $globalDBCtx }}
            {{- if $sslEnabled }}
                {{- if $certEnvName }}
                    {{- $certEnv = include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "TIMESCALEDB_SSL_ROOT_CERT" "overrideEnvName" $certEnvName "defaultKubernetesSecretName" $globalDBCtx.certName "defaultKubernetesSecretKey" $globalDBCtx.certKey "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalDBESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
                {{- end }}
            {{- end }}
        {{- end }}
        {{- if $enableSSLEnvName }}
            {{- if $sslEnabled }}
- name: {{ print $enableSSLEnvName }}
  value: "true"
            {{- end }}
        {{- end }}
        {{- if $sslModeEnvName }}
            {{- $sslModeValue := "" }}
            {{- if $sslEnabled }}
                {{- $sslModeValue = dig "envConfig" "sslMode" "enabledValue" "" $localDBCtx }}
            {{- else }}
                {{- $sslModeValue = dig "envConfig" "sslMode" "disabledValue" "" $localDBCtx }}
            {{- end }}
            {{- if $sslModeValue }}
- name: {{ print $sslModeEnvName }}
  value: {{ print $sslModeValue }}
            {{- end }}
        {{- end }}
        {{- if $certEnv }}
            {{- $certEnv }}
        {{- end }}
        {{- if $certPathEnvName }}
            {{- $certPathValue := dig "envConfig" "certPath" "value" "" $localDBCtx }}
            {{- if $certPathValue }}
- name: {{ print $certPathEnvName }}
  value: {{ print $certPathValue }}
            {{- end }}
        {{- end }}
    {{- else }}
        {{- fail (printf "ERROR: invalid contexts") }}
    {{- end }}
{{- end }}

{{/*
Generate K8S Env Spec for all TimescaleDB Environment Variables

USAGE:
{{- include "harnesscommon.dbv3.manageTimescaleDBEnvs" (dict "ctx" $) | indent 12 }}

PARAMETERS:
REQUIRED:
1. ctx

*/}}
{{- define "harnesscommon.dbv3.manageTimescaleDBEnvs" }}
    {{- $ := .ctx }}
    {{- range $instanceName, $instance := $.Values.timescaledb }}
        {{- include "harnesscommon.dbv3.timescaleEnv" (dict "ctx" $ "instanceName" $instanceName)}}
    {{- end }}
{{- end }}

{{/*
Generate K8S Env Spec for all DB related Environment Variables

USAGE:
{{- include "harnesscommon.dbv3.manageEnvs" (dict "ctx" $) | indent 12 }}

PARAMETERS:
REQUIRED:
1. ctx

*/}}
{{- define "harnesscommon.dbv3.manageEnvs" }}
    {{- $ := .ctx }}
    {{- include "harnesscommon.dbv3.manageMongoEnvs" (dict "ctx" $) }}
{{- end }}
