{{/*
Expand the name of the chart.
*/}}
{{- define "access-control.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "access-control.fullname" -}}
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
{{- define "access-control.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "access-control.labels" -}}
helm.sh/chart: {{ include "access-control.chart" . }}
{{ include "access-control.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "access-control.selectorLabels" -}}
app.kubernetes.io/name: {{ include "access-control.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "access-control.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "access-control.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Manage Access-Control Secrets
USAGE:
{{- "access-control.generateSecrets" (dict "ctx" $)}}
*/}}

{{- define "access-control.generateSecrets" }}
    {{- $ := .ctx }}
    {{- $hasAtleastOneSecret := false }}
    {{- $localESOSecretCtxIdentifier := (include "harnesscommon.secrets.localESOSecretCtxIdentifier" (dict "ctx" $ )) }}
    {{- if eq (include "harnesscommon.secrets.isDefaultAppSecret" (dict "ctx" $ "variableName" "IDENTITY_SERVICE_SECRET")) "true" }}
    {{- $hasAtleastOneSecret = true }}
IDENTITY_SERVICE_SECRET: '{{ .ctx.Values.secrets.default.IDENTITY_SERVICE_SECRET | b64enc }}'
    {{- end }}
    {{- if not $hasAtleastOneSecret }}
{}
    {{- end }}
{{- end }}    
{{/*
Helper function for pullSecrets at chart level or global level.
*/}}
{{- define "access-control.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.waitForInitContainer.image) "global" .Values.global ) }}
{{- end -}}

{{/* Generates comma separated list of Mongo Host names based off environment 
*/}}
{{- define "access-control.mongohosts" }}
{{- $type := "mongo" }}
{{- $hosts := default (default .Values.global.database.mongo.hosts .Values.mongo.hosts) .Values.mongoHosts }}
{{- $installed := (pluck $type .Values.global.database | first ).installed }}
{{- if $installed }}
  {{- $namespace := .Release.Namespace }}
  {{- if .Values.global.ha -}}
    {{- printf " 'mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-1.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-2.mongodb-replicaset-chart.%s.svc:27017'" $namespace $namespace $namespace -}}
  {{- else }}
    {{- printf " 'mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc'" $namespace -}}
  {{- end }}
{{- else }}
    {{- printf "%s" (join "," $hosts ) -}}
{{- end }}
{{- end }}

{{/* Generates Mongo Connection string
{{ include "access-control.mongoConnectionUrl" (dict "database" "foo" "context" $) }}
*/}}
{{- define "access-control.mongoConnectionUrl" }}
  {{- $ := .context }}
  {{- $type := "mongo" }}
  {{- $dbType := $type | upper}}
  {{- $installed := true }}
  {{- if eq $.Values.global.database.mongo.installed false }}
      {{- $installed = false }}
  {{- end }}
  {{- $hosts := list }}
  {{- if gt (len $.Values.mongo.hosts) 0 }}
      {{- $hosts = $.Values.mongo.hosts }}
  {{- else }}
      {{- $hosts = $.Values.global.database.mongo.hosts }}
  {{- end }}
  {{- $protocol := (include "harnesscommon.precedence.getValueFromKey" (dict "ctx" $ "valueType" "string" "keys" (list ".Values.global.database.mongo.protocol" ".Values.mongo.protocol"))) }}
  {{- $extraArgs := (include "harnesscommon.precedence.getValueFromKey" (dict "ctx" $ "valueType" "string" "keys" (list ".Values.global.database.mongo.extraArgs" ".Values.mongo.extraArgs"))) }}
  {{- $userVariableName := default (printf "%s_USER" $dbType) .userVariableName }}
  {{- $passwordVariableName := default (printf "%s_PASSWORD" $dbType) .passwordVariableName }}
  {{- if $installed }}
      {{- $namespace := $.Release.Namespace }}
      {{- if $.Values.global.ha }}
      {{- printf "'mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-1.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-2.mongodb-replicaset-chart.%s.svc:27017/%s?replicaSet=rs0&authSource=admin'" $namespace $namespace $namespace .database }}
      {{- else }}
          {{- printf "'mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc/%s?authSource=admin'" $namespace .database }}
      {{- end }}
  {{- else }}
      {{- $args := (printf "/%s?%s" .database $extraArgs )}}
      {{- $finalhost := (index $hosts  0) }}
      {{- range $host := (rest $hosts ) }}
          {{- $finalhost = printf "%s,%s" $finalhost $host }}
      {{- end }}
      {{- $connectionString := (printf "%s%s" $finalhost $args) }}
      {{- printf "%s" $connectionString }}
  {{- end }}
{{- end }}
