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
*/}}
{{- define "ng-manager.generateSecrets" }}
    {{- $ := .ctx }}
    {{- $hasAtleastOneSecret := false }}
    {{- $localESOSecretCtxIdentifier := (include "harnesscommon.secrets.localESOSecretCtxIdentifier" (dict "ctx" $ )) }}
    {{- if eq (include "harnesscommon.secrets.isDefault" (dict "ctx" $ "variableName" "LOG_STREAMING_SERVICE_TOKEN" "extKubernetesSecretCtxs" (list $.Values.secrets.kubernetesSecrets) "esoSecretCtxs" (list (dict $localESOSecretCtxIdentifier $.Values.secrets.secretManagement.externalSecretsOperator)))) "true" }}
    {{- $hasAtleastOneSecret = true }}
LOG_STREAMING_SERVICE_TOKEN: c76e567a-b341-404d-a8dd-d9738714eb82
    {{- end }}
    {{- if not $hasAtleastOneSecret }}
{}
    {{- end }}
{{- end }}