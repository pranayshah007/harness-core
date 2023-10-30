{{/*
Generates MongoDB environment variables

USAGE:
{{ include "harnesscommon.dbconnectionv2.mongoEnv" (dict "ctx" $ "instanceName" "harness") | indent 12 }}

INPUT ARGUMENTS:
REQUIRED:
1. ctx
2. instanceName

*/}}
{{- define "harnesscommon.dbconnectionv3.mongoEnv" }}
    {{- $ := .ctx }}
    {{- $instanceName := .instanceName }}
    {{- if empty $instanceName }}
        {{- fail "missing input argument: instanceName" }}
    {{- end }}
    {{- $localDBCtx := get $.Values.mongo.instances $instanceName }}
    {{- $globalDBCtx := $.Values.global.database.mongo }}
    {{- if and $ $localDBCtx $globalDBCtx }}
        {{- $type := "mongo" }}
        {{- $dbType := $type | upper}}
        {{- $enabled := $localDBCtx.enabled }}
        {{- $defaultUserVariableName := (printf "%s_%s_USER" ($instanceName | upper) $dbType) }}
        {{- $userVariableName := default $defaultUserVariableName (dig "tmpl" "username" "envName" ""  $localDBCtx) }}
        {{- $defaultPasswordVariableName := (printf "%s_%s_PASSWORD" ($instanceName | upper) $dbType) }}
        {{- $passwordVariableName := default $defaultPasswordVariableName (dig "tmpl" "password" "envName" ""  $localDBCtx) }}
        {{- $installed := true }}
        {{- if eq $globalDBCtx.installed false }}
            {{- $installed = $globalDBCtx.installed }}
        {{- end }}
        {{- $localMongoESOSecretCtxIdentifier := (include "harnesscommon.secrets.localESOSecretCtxIdentifier" (dict "ctx" $ "additionalCtxIdentifier" (printf "%s-%s" $instanceName "mongo") )) }}
        {{- $globalMongoESOSecretIdentifier := (include "harnesscommon.secrets.globalESOSecretCtxIdentifier" (dict "ctx" $ "ctxIdentifier" "mongo" )) }}
        {{- if $enabled }}
            {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_USER"  "overrideEnvName" $userVariableName "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $localMongoESOSecretCtxIdentifier "secretCtx" $localDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_PASSWORD" "overrideEnvName" $passwordVariableName "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "extKubernetesSecretCtxs" (list $localDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $localMongoESOSecretCtxIdentifier "secretCtx" $localDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
        {{- else if $installed }}
            {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_USER"  "overrideEnvName" $userVariableName "defaultKubernetesSecretName" "harness-secrets" "defaultKubernetesSecretKey" "mongodbUsername" "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalMongoESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_PASSWORD" "overrideEnvName" $passwordVariableName "defaultKubernetesSecretName" "mongodb-replicaset-chart" "defaultKubernetesSecretKey" "mongodb-root-password" "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalMongoESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
        {{- else }}
            {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_USER"  "overrideEnvName" $userVariableName "defaultKubernetesSecretName" $globalDBCtx.secretName "defaultKubernetesSecretKey" $globalDBCtx.userKey "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets $localDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalMongoESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
            {{- include "harnesscommon.secrets.manageEnv" (dict "ctx" $ "variableName" "MONGO_PASSWORD" "overrideEnvName" $passwordVariableName "defaultKubernetesSecretName" $globalDBCtx.secretName "defaultKubernetesSecretKey" $globalDBCtx.passwordKey "extKubernetesSecretCtxs" (list $globalDBCtx.secrets.kubernetesSecrets $localDBCtx.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict "secretCtxIdentifier" $globalMongoESOSecretIdentifier "secretCtx" $globalDBCtx.secrets.secretManagement.externalSecretsOperator))) }}
        {{- end }}
    {{- else }}
        {{- fail (printf "invalid input") }}
    {{- end }}
{{- end }}

{{/*
Generates MongoDB Connection Environment Variables

USAGE:
{{ include "harnesscommon.dbconnectionv2.mongoConnectionEnv" (dict "ctx" $ "instanceName" "harness") | indent 12 }}

INPUT ARGUMENTS:
REQUIRED:
1. ctx
2. instanceName

*/}}
{{- define "harnesscommon.dbconnectionv3.mongoConnectionEnv" }}
    {{- $ := .ctx }}
    {{- $instanceName := .instanceName }}
    {{- if empty $instanceName }}
        {{- fail "missing input argument: instanceName" }}
    {{- end }}
    {{- $localDBCtx := get $.Values.mongo.instances $instanceName }}
    {{- $globalDBCtx := $.Values.global.database.mongo }}
    {{- $dbName := dig "tmpl" "dbName" "" $localDBCtx }}
    {{- if and $ $localDBCtx $globalDBCtx $dbName }}
        {{- $type := "mongo" }}
        {{- $dbType := $type | upper}}
        {{- $enabled := $localDBCtx.enabled }}
        {{- $defaultUserVariableName := (printf "%s_%s_USER" ($instanceName | upper) $dbType) }}
        {{- $userVariableName := default $defaultUserVariableName (dig "tmpl" "username" "envName" ""  $localDBCtx) }}
        {{- $defaultPasswordVariableName := (printf "%s_%s_PASSWORD" ($instanceName | upper) $dbType) }}
        {{- $passwordVariableName := default $defaultPasswordVariableName (dig "tmpl" "password" "envName" ""  $localDBCtx) }}
        {{- $defaultConnectionVariableName := (printf "%s_%s_URI" ($instanceName | upper) $dbType) }}
        {{- $connectionVariableName := default $defaultConnectionVariableName (dig "tmpl" "connectionURI" "envName" ""  $localDBCtx) }}
        {{- $installed := true }}
        {{- if eq $.Values.global.database.mongo.installed false }}
            {{- $installed = false }}
        {{- end }}
        {{- $mongoURI := "" }}
        {{- if $installed }}
            {{- $namespace := $.Release.Namespace }}
            {{- if $.Values.global.ha }}
                {{- $mongoURI = printf "'mongodb://$(%s):$(%s)@mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-1.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-2.mongodb-replicaset-chart.%s.svc:27017/%s?replicaSet=rs0&authSource=admin'" $userVariableName $passwordVariableName $namespace $namespace $namespace $dbName }}
            {{- else }}
                {{- $mongoURI = printf "'mongodb://$(%s):$(%s)@mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc/%s?authSource=admin'" $userVariableName $passwordVariableName $namespace $dbName }}
            {{- end }}
        {{- else }}
            {{- $hosts := list }}
            {{- $protocol := "" }}
            {{- $extraArgs := "" }}
            {{- if $localDBCtx.enabled }}
                {{- $hosts = $localDBCtx.hosts }}
                {{- $protocol = $localDBCtx.protocol }}
                {{- $extraArgs = $localDBCtx.extraArgs }}
            {{- else }}
                {{- $hosts = $.Values.global.database.mongo.hosts }}
                {{- $protocol = $.Values.global.database.mongo.protocol }}
                {{- $extraArgs = $.Values.global.database.mongo.extraArgs }}
            {{- end }}
            {{- $args := (printf "/%s?%s" $dbName $extraArgs ) -}}
            {{- $mongoURI = include "harnesscommon.dbconnection.connection" (dict "type" "mongo" "hosts" $hosts "protocol" $protocol "extraArgs" $args "userVariableName" $userVariableName "passwordVariableName" $passwordVariableName)}}
        {{- end }}
- name: {{ printf "%s" $connectionVariableName }}
  value: {{ printf "%s" $mongoURI }}
    {{- end }}
{{- end }}

{{- define "harnesscommon.dbconnectionv3.manageMongoEnvs" }}
    {{- $ := .ctx }}
    {{- range $instanceName, $instanceConfig := $.Values.mongo.instances }}
        {{- include "harnesscommon.dbconnectionv3.mongoEnv" (dict "ctx" $ "instanceName" $instanceName)}}
        {{- include "harnesscommon.dbconnectionv3.mongoConnectionEnv" (dict "ctx" $ "instanceName" $instanceName)}}
    {{- end }}
{{- end }}

{{- define "harnesscommon.dbconnectionv3.manageDBEnvs" }}
    {{- $ := .ctx }}
    {{- include "harnesscommon.dbconnectionv3.manageMongoEnvs" (dict "ctx" $) }}
{{- end }}