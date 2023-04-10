{{/*
Expand the name of the chart.
*/}}
{{- define "platform-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "platform-service.fullname" -}}
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
{{- define "platform-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "platform-service.labels" -}}
helm.sh/chart: {{ include "platform-service.chart" . }}
{{ include "platform-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "platform-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "platform-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "platform-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "platform-service.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "platform-service.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.waitForInitContainer.image) "global" .Values.global ) }}
{{- end -}}

{{- define "platform-service.generateSmtpSecrets" }}
    SMTP_HOST: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "smtp-secret" "key" "SMTP_HOST" "providedValues" (list "global.smtpCreateSecret.SMTP_HOST") "length" 10 "context" $) }}
    SMTP_PORT: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "smtp-secret" "key" "SMTP_PORT" "providedValues" (list "global.smtpCreateSecret.SMTP_PORT") "length" 10 "context" $) }}
    SMTP_USERNAME: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "smtp-secret" "key" "SMTP_USERNAME" "providedValues" (list "global.smtpCreateSecret.SMTP_USERNAME") "length" 10 "context" $) }}
    SMTP_PASSWORD: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "smtp-secret" "key" "SMTP_PASSWORD" "providedValues" (list "global.smtpCreateSecret.SMTP_PASSWORD") "length" 10 "context" $) }}
    SMTP_USE_SSL: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "smtp-secret" "key" "SMTP_USE_SSL" "providedValues" (list "global.smtpCreateSecret.SMTP_USE_SSL") "length" 10 "context" $) }}
{{- end }}