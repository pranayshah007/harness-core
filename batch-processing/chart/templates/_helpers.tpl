{{/*
Expand the name of the chart.
*/}}
{{- define "batch-processing.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "batch-processing.fullname" -}}
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
{{- define "batch-processing.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "batch-processing.labels" -}}
helm.sh/chart: {{ include "batch-processing.chart" . }}
{{ include "batch-processing.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "batch-processing.selectorLabels" -}}
app.kubernetes.io/name: {{ include "batch-processing.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "batch-processing.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "batch-processing.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "batch-processing.deploymentEnv" -}}
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
        name: postgres
        key: postgres-password
- { name: APP_DATABASE_DATASOURCE, value: "{{ printf "postgres://postgres:$(DB_PASSWORD)@postgres:5432" }}" }
- { name: APP_DB_MIGRATION_DATASOURCE, value: "{{ printf "postgres://postgres:$(DB_PASSWORD)@postgres:5432" }}" }
{{- end }}

{{- define "batch-processing.generateSecrets" }}
    S3_SYNC_CONFIG_ACCESSKEY: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "batch-processing" "key" "S3_SYNC_CONFIG_ACCESSKEY" "providedValues" (list "awsSecret.S3_SYNC_CONFIG_ACCESSKEY") "length" 10 "context" $) }}
    S3_SYNC_CONFIG_SECRETKEY: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "batch-processing" "key" "S3_SYNC_CONFIG_SECRETKEY" "providedValues" (list "awsSecret.S3_SYNC_CONFIG_SECRETKEY") "length" 10 "context" $) }}
{{- end }}

{{- define "batch-processing.generateMountSecrets" }}
    {{- if not .Values.workloadIdentity.enabled }}
    ce-batch-gcp-credentials: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "batch-processing-secret-mount" "key" "ce-batch-gcp-credentials" "providedValues" (list "ceBatchGCPCredentials") "length" 10 "context" $) }}
    ce-gcp-home-project-creds: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "batch-processing-secret-mount" "key" "ce-gcp-home-project-creds" "providedValues" (list "ceGCPHomeProjectCreds") "length" 10 "context" $) }}
    {{- end }}
    cloud-data-store: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "batch-processing-secret-mount" "key" "cloud-data-store" "providedValues" (list "storageObjectAdmin") "length" 10 "context" $) }}
{{- end }}

{{- define "batch-processing.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.waitForInitContainer.image) "global" .Values.global ) }}
{{- end -}}