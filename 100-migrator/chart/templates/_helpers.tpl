{{/*
Expand the name of the chart.
*/}}
{{- define "migrator.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "migrator.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "migrator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "migrator.labels" -}}
helm.sh/chart: {{ include "migrator.chart" . }}
{{ include "migrator.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "migrator.selectorLabels" -}}
app.kubernetes.io/name: {{ include "migrator.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "migrator.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "migrator.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create the name of the delegate image to use
*/}}
{{- define "migrator.delegate_docker_image" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.delegate_docker_image.image "global" .Values.global) }}
{{- end }}

{{/*
Create the name of the immutable delegate image to use
*/}}
{{- define "migrator.immutable_delegate_docker_image" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.immutable_delegate_docker_image.image "global" .Values.global) }}
{{- end }}

{{/*
Create the name of the delegate upgrader image to use
*/}}
{{- define "migrator.upgrader_docker_image" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.upgrader_docker_image.image "global" .Values.global) }}
{{- end }}

{{/*
Manage Migrator Secrets

USAGE:
{{- "migrator.generateSecrets" (dict "ctx" $)}}
*/}}
{{- define "migrator.generateSecrets" }}
    {{- $ := .ctx }}
    {{- $hasAtleastOneSecret := false }}
    {{- $localESOSecretCtxIdentifier := (include "harnesscommon.secrets.localESOSecretCtxIdentifier" (dict "ctx" $ )) }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "LOG_STREAMING_SERVICE_TOKEN")) "true" }}
    {{- $hasAtleastOneSecret = true }}
LOG_STREAMING_SERVICE_TOKEN: {{ .ctx.Values.secrets.default.LOG_STREAMING_SERVICE_TOKEN | b64enc }}
    {{- end }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "VERIFICATION_SERVICE_SECRET")) "true" }}
    {{- $hasAtleastOneSecret = true }}
VERIFICATION_SERVICE_SECRET: {{ .ctx.Values.secrets.default.VERIFICATION_SERVICE_SECRET | b64enc }}
    {{- end }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "MONGO_SSL_CA_TRUST_STORE_PATH")) "true" }}
    {{- $hasAtleastOneSecret = true }}
MONGO_SSL_CA_TRUST_STORE_PATH: {{ .ctx.Values.secrets.default.MONGO_SSL_CA_TRUST_STORE_PATH | b64enc }}
    {{- end }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "MONGO_SSL_CA_TRUST_STORE_PASSWORD")) "true" }}
    {{- $hasAtleastOneSecret = true }}
MONGO_SSL_CA_TRUST_STORE_PASSWORD: {{ .ctx.Values.secrets.default.MONGO_SSL_CA_TRUST_STORE_PASSWORD | b64enc }}
    {{- end }}
    {{- if not $hasAtleastOneSecret }}
{}
    {{- end }}
{{- end }}


{{- define "migrator.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.waitForInitContainer.image) "global" .Values.global ) }}
{{- end -}}
