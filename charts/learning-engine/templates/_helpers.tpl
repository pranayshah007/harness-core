{{/*
Return the proper image name
{{ include "learning-engine.images.image" ( dict "imageRoot" .Values.path.to.the.image "global" $) }}
*/}}
{{- define "learning-engine.images.image" -}}
{{- $registryName := .imageRoot.registry -}}
{{- $repositoryName := .imageRoot.repository -}}
{{- if .global }}
  {{- if .global.imageRegistry }}
    {{- $registryName = .global.imageRegistry -}}
  {{- end -}}
{{- end -}}
{{- printf "%s/%s" $registryName $repositoryName -}}
{{- end -}}

{{/*
Expand the name of the chart.
*/}}
{{- define "learning-engine.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "learning-engine.fullname" -}}
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
{{- define "learning-engine.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "learning-engine.labels" -}}
helm.sh/chart: {{ include "learning-engine.chart" . }}
{{ include "learning-engine.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "learning-engine.selectorLabels" -}}
app.kubernetes.io/name: {{ include "learning-engine.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "learning-engine.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "learning-engine.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "learning-engine.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.image ) "global" .Values.global ) }}
{{- end -}}