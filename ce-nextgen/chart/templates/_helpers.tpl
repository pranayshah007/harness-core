{{/*
Expand the name of the chart.
*/}}
{{- define "ce-nextgen.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "ce-nextgen.fullname" -}}
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
{{- define "ce-nextgen.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "ce-nextgen.labels" -}}
helm.sh/chart: {{ include "ce-nextgen.chart" . }}
{{ include "ce-nextgen.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "ce-nextgen.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ce-nextgen.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "ce-nextgen.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "ce-nextgen.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "ce-nextgen.deploymentEnv" -}}
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
        name: postgres
        key: postgres-password
- { name: APP_DATABASE_DATASOURCE, value: "{{ printf "postgres://postgres:$(DB_PASSWORD)@postgres:5432" }}" }
- { name: APP_DB_MIGRATION_DATASOURCE, value: "{{ printf "postgres://postgres:$(DB_PASSWORD)@postgres:5432" }}" }
{{- end }}

{{- define "ce-nextgen.generateSecrets" }}
    AWS_ACCESS_KEY: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "ce-nextgen" "key" "AWS_ACCESS_KEY" "providedValues" (list "awsSecret.AWS_ACCESS_KEY") "length" 10 "context" $) }}
    AWS_SECRET_KEY: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "ce-nextgen" "key" "AWS_SECRET_KEY" "providedValues" (list "awsSecret.AWS_SECRET_KEY") "length" 10 "context" $) }}
    AWS_ACCOUNT_ID: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "ce-nextgen" "key" "AWS_ACCOUNT_ID" "providedValues" (list "awsSecret.AWS_ACCOUNT_ID") "length" 10 "context" $) }}
    AWS_DESTINATION_BUCKET: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "ce-nextgen" "key" "AWS_DESTINATION_BUCKET" "providedValues" (list "awsSecret.AWS_DESTINATION_BUCKET") "length" 10 "context" $) }}
    AWS_TEMPLATE_LINK: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "ce-nextgen" "key" "AWS_TEMPLATE_LINK" "providedValues" (list "awsSecret.AWS_TEMPLATE_LINK") "length" 10 "context" $) }}
    CE_AWS_TEMPLATE_URL: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "ce-nextgen" "key" "CE_AWS_TEMPLATE_URL" "providedValues" (list "awsSecret.CE_AWS_TEMPLATE_URL") "length" 10 "context" $) }}
{{- end }}

{{- define "ce-nextgen.generateMountSecrets" }}
    {{- if not .Values.workloadIdentity.enabled }}
    ceng-gcp-credentials: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "ceng-secret-mount" "key" "ceng-gcp-credentials" "providedValues" (list "ceng-gcp-credentials") "length" 10 "context" $) }}
    {{- end }}
{{- end }}

{{- define "ce-nextgen.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.waitForInitContainer.image) "global" .Values.global ) }}
{{- end -}}