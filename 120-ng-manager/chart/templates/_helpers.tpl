{{/*
Expand the name of the chart.
*/}}
{{- define "ng-manager.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "ng-manager.fullname" -}}
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
{{- define "ng-manager.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "ng-manager.labels" -}}
helm.sh/chart: {{ include "ng-manager.chart" . }}
{{ include "ng-manager.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "ng-manager.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ng-manager.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "ng-manager.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "ng-manager.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "ng-manager.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.waitForInitContainer.image .Values.initContainer.image) "global" .Values.global ) }}
{{- end -}}

{{/*
Manage NG Manager Secrets
USAGE:
{{- "ng-manager.generateSecrets" (dict "ctx" $)}}
default LOG_STREAMING_SERVICE_TOKEN was c76e567a-b341-404d-a8dd-d9738714eb82 and
*/}}
{{- define "ng-manager.generateSecrets" }}
    {{- $ := .ctx }}
    {{- $hasAtleastOneSecret := false }}
    {{- $localESOSecretCtxIdentifier := (include "harnesscommon.secrets.localESOSecretCtxIdentifier" (dict "ctx" $ )) }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "LOG_STREAMING_SERVICE_TOKEN")) "true" }}
    {{- $hasAtleastOneSecret = true }}
LOG_STREAMING_SERVICE_TOKEN: {{ .ctx.Values.secrets.default.LOG_STREAMING_SERVICE_TOKEN | b64enc }}
    {{- end }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "OPA_SERVER_SECRET")) "true" }}
    {{- $hasAtleastOneSecret = true }}
OPA_SERVER_SECRET: {{ .ctx.Values.secrets.default.OPA_SERVER_SECRET | b64enc }}
    {{- end }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "GITOPS_SERVICE_SECRET")) "true" }}
    {{- $hasAtleastOneSecret = true }}
GITOPS_SERVICE_SECRET: {{ .ctx.Values.secrets.default.GITOPS_SERVICE_SECRET | b64enc }}
    {{- end }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "CE_AWS_ACCESS_KEY")) "true" }}
    {{- $hasAtleastOneSecret = true }}
CE_AWS_ACCESS_KEY: {{ default $.Values.ceSecret.access_key.name .ctx.Values.secrets.default.CE_AWS_ACCESS_KEY | b64enc }}
    {{- end }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "CE_AWS_SECRET_KEY")) "true" }}
    {{- $hasAtleastOneSecret = true }}
CE_AWS_SECRET_KEY: {{ default $.Values.ceSecret.secret_key.name .ctx.Values.secrets.default.CE_AWS_SECRET_KEY | b64enc }}
    {{- end }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "CE_AWS_DESTINATION_BUCKET")) "true" }}
    {{- $hasAtleastOneSecret = true }}
CE_AWS_DESTINATION_BUCKET: {{ default $.Values.ceSecret.destination_bucket.name .ctx.Values.secrets.default.CE_AWS_DESTINATION_BUCKET | b64enc }}
    {{- end }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "CE_AWS_TEMPLATE_URL")) "true" }}
    {{- $hasAtleastOneSecret = true }}
CE_AWS_TEMPLATE_URL: {{ default $.Values.ceSecret.template_url.name .ctx.Values.secrets.default.CE_AWS_TEMPLATE_URL | b64enc }}
    {{- end }}
    {{- if not $hasAtleastOneSecret }}
{}
    {{- end }}
{{- end }}