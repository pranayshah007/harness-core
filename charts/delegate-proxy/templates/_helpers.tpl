{{/*
Expand the name of the chart.
*/}}
{{- define "delegate-proxy.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "delegate-proxy.fullname" -}}
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
{{- define "delegate-proxy.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "delegate-proxy.labels" -}}
helm.sh/chart: {{ include "delegate-proxy.chart" . }}
{{ include "delegate-proxy.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "delegate-proxy.selectorLabels" -}}
app.kubernetes.io/name: {{ include "delegate-proxy.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "delegate-proxy.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "delegate-proxy.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "delegate-proxy.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.image) "global" .Values.global ) }}
{{- end -}}

{{/*
Create the name of the immutable delegate image to use
*/}}
{{- define "delegate-proxy.docker_image" -}}
{{- $tag := printf "%s" .Values.image.tag }}
{{- if .Values.global.useMinimalDelegateImage }}
{{- $tag = printf "%s_minimal" $tag }}
{{- end }}
{{- $image := dict "registry" .Values.image.registry "repository" .Values.image.repository "tag" $tag "digest" .Values.image.digest -}}
{{ include "common.images.image" (dict "imageRoot" $image "global" .Values.global) }}
{{- end }}