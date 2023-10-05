{{/*
Check if only one valid ESO Secret is provided in the secrets context

Returns: bool

Example:
{{ include "harnesscommon.secrets.hasSingleValidESOSecret" (dict "secretsCtx" .Values.secrets) }}
*/}}
{{- define "harnesscommon.secrets.hasSingleValidESOSecret" }}
  {{- $secretsCtx := .secretsCtx }}
  {{- $hasSingleValidESOSecret := "false" }}
  {{- if and $secretsCtx .secretsCtx.secretManagement $secretsCtx.secretManagement.externalSecretsOperator (eq (len $secretsCtx.secretManagement.externalSecretsOperator) 1) }}
    {{- $externalSecret := first $secretsCtx.secretManagement.externalSecretsOperator }}
        {{- $hasSingleValidESOSecret = include "harnesscommon.secrets.hasValidESOSecret" (dict "esoSecretCtx" $externalSecret) }}
  {{- end }}
  {{- print $hasSingleValidESOSecret }}
{{- end }}

{{/*
Returns Name of the Secret to use for Harness Installed Mongo DB

USAGE:
{{ include "harnesscommon.secrets.InstalledMongoDBSecret" (dict "ctx" $) }}
*/}}
{{- define "harnesscommon.secrets.InstalledMongoDBSecret" }}
  {{- $ := .ctx }}
  {{- if $.Values.global.database.mongo.installed }}
    {{- $secretName := "" }}
    {{- if eq (include "harnesscommon.secrets.hasSingleValidESOSecret" (dict "secretsCtx" $.Values.global.database.mongo.secrets)) "true" }}
      {{- $secretName = include "harnesscommon.secrets.globalESOSecretCtxIdentifier" (dict "ctx" $  "ctxIdentifier" "mongo") }}
    {{- else }}
      {{- $secretName = "mongodb-replicaset-chart" }}
    {{- end }}
    {{- print $secretName }}
  {{- end }}
{{- end }}