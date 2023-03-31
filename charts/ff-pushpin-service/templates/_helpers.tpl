{{/*
Expand the name of the chart.
*/}}
{{- define "ff-pushpin-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "ff-pushpin-service.fullname" -}}
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
{{- define "ff-pushpin-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "ff-pushpin-service.labels" -}}
helm.sh/chart: {{ include "ff-pushpin-service.chart" . }}
{{ include "ff-pushpin-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "ff-pushpin-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ff-pushpin-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "ff-pushpin-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "ff-pushpin-service.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create the name of the sentinet image to use
*/}}
{{- define "ff-pushpin-service.securityImage" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.securityImage.image "global" .Values.global) }}
{{- end }}

{{/*
Create the name of the redis image to use
*/}}
{{- define "ff-pushpin-service.pushpinImage" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.pushpin.image "global" .Values.global) }}
{{- end }}

{{/*
Create the name of the redis image to use
*/}}
{{- define "ff-pushpin-service.pushpinworkerImage" -}}
{{ include "common.images.image" (dict "imageRoot" .Values.pushpinworker.image "global" .Values.global) }}
{{- end }}

{{- define "ff-pushpin-service.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.waitForInitContainer.image .Values.pushpinworker.image .Values.pushpin.image ) "global" .Values.global ) }}
{{- end -}}