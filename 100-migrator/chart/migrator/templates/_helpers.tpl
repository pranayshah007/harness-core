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


## Generate ffString based of feature flag values and globally enabled features
{{- define "migrator.ffString" -}}
{{- $flags := .Values.featureFlags.Base }}
{{- if .Values.global.gitops.enabled }}
{{- $flags = printf "%s,%s" $flags $.Values.featureFlags.GitOps }}
{{- end }}
{{- if .Values.global.opa.enabled }}
{{- $flags = printf "%s,%s" $flags $.Values.featureFlags.OPA }}
{{- end }}
{{- if not .Values.global.ngGitSync.enabled }}
{{- $flags = printf "%s,%s" $flags $.Values.featureFlags.OLDGITSYNC }}
{{- end }}
{{- if .Values.global.cd.enabled }}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.CD }}
{{- end }}
{{- if .Values.global.ci.enabled }}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.CI }}
{{- end }}
{{- if .Values.global.sto.enabled }}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.STO }}
{{- end }}
{{- if .Values.global.srm.enabled }}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.SRM }}
{{- end }}
{{- if .Values.global.ngcustomdashboard.enabled }}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.CDB }}
{{- end }}
{{- if .Values.global.ff.enabled }}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.FF }}
{{- end }}
{{- if .Values.global.ccm.enabled }}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.CCM }}
{{- end }}
{{- if .Values.global.saml.autoaccept }}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.SAMLAutoAccept }}
{{- end }}
{{- $length := len .Values.global.license.ng }}
{{- if gt $length 0}}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.LICENSE }}
{{- end }}
{{- if .Values.global.ng.enabled }}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.NG }}
{{- end }}
{{- if .Values.global.chaos.enabled }}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.CHAOS }}
{{- end }}
{{- $length2 := len .Values.featureFlags.ADDITIONAL }}
{{- if gt $length2 0}}
{{- $flags = printf "%s,%s" $flags .Values.featureFlags.ADDITIONAL }}
{{- end }}
{{- printf "%s" $flags }}
{{- end }}

{{/*
Randomly Creates Secret for access-control unless overwritten.
*/}}
{{- define "migrator.generateSecrets" }}
    LOG_STREAMING_SERVICE_TOKEN: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-manager" "key" "LOG_STREAMING_SERVICE_TOKEN" "providedValues" (list "secrets.LOG_STREAMING_SERVICE_TOKEN") "length" 10 "context" $) }}
    MONGO_SSL_CA_TRUST_STORE_PATH: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-manager" "key" "MONGO_SSL_CA_TRUST_STORE_PATH" "providedValues" (list "secrets.MONGO_SSL_CA_TRUST_STORE_PATH") "length" 10 "context" $) }}
    MONGO_SSL_CA_TRUST_STORE_PASSWORD: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-manager" "key" "MONGO_SSL_CA_TRUST_STORE_PASSWORD" "providedValues" (list "secrets.MONGO_SSL_CA_TRUST_STORE_PASSWORD") "length" 10 "context" $) }}
    VERIFICATION_SERVICE_SECRET: {{ include "harnesscommon.secrets.passwords.manage" (dict "secret" "harness-manager" "key" "VERIFICATION_SERVICE_SECRET" "providedValues" (list "secrets.VERIFICATION_SERVICE_SECRET") "length" 10 "context" $) }}
{{- end }}

{{- define "migrator.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.waitForInitContainer.image) "global" .Values.global ) }}
{{- end -}}

{{/*
Overrride mongoUri if provided, else use the default
*/}}
{{- define "migrator.mongoConnection" }}
{{- $type := "MONGO" }}
{{- $override := .Values.migrator.mongodb.override }}
{{- $hosts := .Values.migrator.mongodb.hosts }}
{{- $protocol := .Values.migrator.mongodb.protocol }}
{{- $extraArgs:= .Values.migrator.mongodb.extraArgs }}
{{- if $override }}
{{- include "harnesscommon.dbconnection.connection" (dict "type" $type "hosts" $hosts "protocol" $protocol "extraArgs" $extraArgs )}}
{{- else }}
{{- include "harnesscommon.dbconnection.mongoConnection" (dict "database" "harness" "context" $) }}
{{- end }}
{{- end }}

{{- define "migrator.mongoEnv" }}
{{- $type := "mongo" }}
{{- $override := .Values.migrator.mongodb.override }}
{{- $passwordSecret := .Values.migrator.mongodb.secretName }}
{{- $passwordKey := .Values.migrator.mongodb.passwordKey }}
{{- $userKey := .Values.migrator.mongodb.userKey }}
{{- if $override }}
{{- include "harnesscommon.dbconnection.dbenvuser" (dict "type" $type "secret" $passwordSecret "userKey" $userKey) }}
{{- include "harnesscommon.dbconnection.dbenvpassword" (dict "type" $type "secret" $passwordSecret "passwordKey" $passwordKey ) }}
{{- else }}
{{- include "harnesscommon.dbconnection.mongoEnv" . }}
{{- end }}
{{- end }}