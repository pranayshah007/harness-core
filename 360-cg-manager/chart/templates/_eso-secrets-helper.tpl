{{/*
Check if valid ESO Secrets are provided in the secrets context

Returns:
  "true" if ESO Secrets are provided
  "false" if ESO Secrets are not provided 

Example:
{{ include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" .Values.secrets) }}
*/}}
{{- define "harnesscommon.secrets.hasESOSecrets" }}
{{- $hasESOSecrets := "false" }}
{{- if and .secretsCtx .secretsCtx.secretManagement .secretsCtx.secretManagement.externalSecretsOperator }}
  {{- with .secretsCtx.secretManagement.externalSecretsOperator }}
    {{- range $externalSecretIdx, $externalSecret := . }}
      {{- $hasESOSecrets = include "harnesscommon.secrets.hasValidESOSecret" (dict "esoSecretCtx" .) }}
    {{- end }}
  {{- end }}
{{- end }}
{{- print $hasESOSecrets }}
{{- end }}

{{/*
Check validity of ESO Secret in the externalSecretsOperator secret context

Returns:
  "true" if ESO Secret is Valid
  "false" if ESO Secret is not Valid 

Example:
{{ include "harnesscommon.secrets.hasValidESOSecret" (dict "esoSecretCtx" .externalSecretsOperator[idx]) }}
*/}}
{{- define "harnesscommon.secrets.hasValidESOSecret" }}
{{- $hasValidESOSecret := "false" }}
  {{- if and .esoSecretCtx .esoSecretCtx.secretStore .esoSecretCtx.secretStore.name .esoSecretCtx.secretStore.kind }}
    {{- range $remoteKeyIndex, $remoteKey := .esoSecretCtx.remoteKeys }}
      {{- if $remoteKey.name }}
        {{- $hasValidESOSecret = "true" }}
      {{- end }}
    {{- end }}
  {{- end }}
{{- print $hasValidESOSecret }}
{{- end }}

{{/*
Check if input variableName is provided as an ESO Secret

Returns:
  "true" if provided as an ESO Secret
  "false" if not provided as an ESO Secret

{{ include "harnesscommon.secrets.hasESOSecret" (dict "variableName" "MY_VARIABLE" "esoSecretCtxs" (list (dict "secretCtxIdentifier" "local" "secretCtx" .Values.secrets.secretManagement.externalSecretsOperator))) }}
*/}}
{{- define "harnesscommon.secrets.hasESOSecret" }}
{{- $hasESOSecret := "false" }}
{{- if .variableName }}
  {{- range .esoSecretCtxs }}
    {{- $secretCtxIdentifier := .secretCtxIdentifier }}
    {{- $secretCtx := .secretCtx }}
    {{- range $secretCtx }}
      {{- if and . .secretStore .secretStore.name .secretStore.kind }}
        {{- $remoteKeyName := (dig "remoteKeys" $.variableName "name" "" .) }}
        {{- if $remoteKeyName }}
          {{- $hasESOSecret = "true" -}}
        {{- end -}}
      {{- end -}}
    {{- end -}}
  {{- end -}}
{{- end -}}
{{- print $hasESOSecret -}}
{{- end -}}

{{/*
Generates secretRef objects for ESO Secrets

Example:
{{ include "harnesscommon.secrets.esoSecretName" (dict "ctx" . "secretContextIdentifier" "local" "secretIdentifier" "1") }}
*/}}
{{- define "harnesscommon.secrets.esoSecretName" -}}
{{- $ := .ctx -}}
{{- $secretContextIdentifier := .secretContextIdentifier | toString -}}
{{- $secretIdentifier := .secretIdentifier | toString -}}
{{- if and .ctx $secretContextIdentifier $secretIdentifier -}}
  {{- printf "%s-%s" $secretContextIdentifier $secretIdentifier  -}}
{{- else -}}
  {{- print (and (not (empty .ctx)) $secretContextIdentifier $secretIdentifier) -}}
{{- end -}}
{{- end -}}

{{/*
Generates secretRef objects for ESO Secrets

Example:
{{ include "harnesscommon.secrets.externalSecretRefs" (dict "secretsCtx" .Values.secrets "secretNamePrefix" "my-secret") }}
*/}}
{{- define "harnesscommon.secrets.externalSecretRefs" -}}
  {{- $externalSecretIdx := 0 -}}
  {{- if and .secretsCtx .secretsCtx.secretManagement .secretsCtx.secretManagement.externalSecretsOperator -}}
    {{- range .secretsCtx.secretManagement.externalSecretsOperator -}}
      {{- if eq (include "harnesscommon.secrets.hasValidESOSecret" (dict "esoSecretCtx" .)) "true" -}}
      {{- $externalSecretIdx = add1 $externalSecretIdx }}
  - secretRef:
      name: {{ printf "%s-external-secret-%d" $.secretNamePrefix $externalSecretIdx }}
      {{- end }}
    {{- end }}
  {{- end }}
{{- end }}

{{/*
{{ include "harnesscommon.secrets.manageESOSecretEnv" (dict "ctx" . "variableName" "MY_VARIABLE" "esoSecretCtxs" (list .Values.secrets.secretManagement.externalSecretsOperator)) }}
*/}}
{{- define "harnesscommon.secrets.manageESOSecretEnv" }}
{{- $ := .ctx }}
{{- $variableName := .variableName }}
{{- $secretName := "" }}
{{- $secretKey := "" }}
{{- if .variableName }}
  {{- range .esoSecretCtxs }}
    {{- $secretCtxIdentifier := .secretCtxIdentifier }}
    {{- $secretCtx := .secretCtx }}
    {{- range $esoSecretIdx, $esoSecret := $secretCtx }}
      {{- if and $esoSecret $esoSecret.secretStore $esoSecret.secretStore.name $esoSecret.secretStore.kind }}
        {{- $remoteKeyName := (dig "remoteKeys" $variableName "name" "" .) }}
        {{- if $remoteKeyName }}
          {{- $secretName = include "harnesscommon.secrets.esoSecretName" (dict "ctx" $ "secretContextIdentifier" $secretCtxIdentifier "secretIdentifier" $esoSecretIdx) }}
          {{- $secretKey = $variableName }}
        {{- end }}
      {{- end }}
    {{- end }}
  {{- end }}
{{- end }}
  {{- if and $secretName $secretKey }}
- name: {{ print .variableName }}
  valueFrom:
    secretKeyRef:
      name: {{ printf "%s" $secretName }}
      key: {{ printf "%s" $secretKey }}
  {{- end }}
{{- end }}

{{/*
Generates ESO External Secret CRD

Example:
{{ include "harnesscommon.secrets.generateExternalSecret" (dict "ctx" . "secretsCtx" .Values.secrets "secretIdentifier" "local") }}
*/}}
{{- define "harnesscommon.secrets.generateExternalSecret" }}
{{- $ := .ctx }}
{{- $secretNamePrefix := .secretNamePrefix }}
{{- if and .secretsCtx .secretsCtx.secretManagement .secretsCtx.secretManagement.externalSecretsOperator }}
    {{- with .secretsCtx.secretManagement.externalSecretsOperator }}
        {{- range $esoSecretIdx, $esoSecret := . }}
          {{- if eq (include "harnesscommon.secrets.hasValidESOSecret" (dict "esoSecretCtx" .)) "true" }}
            {{- $esoSecretName := (printf "%s-%d" $secretNamePrefix $esoSecretIdx) }}
            {{- if gt $esoSecretIdx 0 }}
{{ printf "\n---"  }}
            {{- end }}
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: {{ $esoSecretName }}
spec:
  secretStoreRef:
    name:  {{ $esoSecret.secretStore.name }}
    kind: {{ $esoSecret.secretStore.kind }}
  target:
    name: {{ $esoSecretName }}
    template:
      engineVersion: v2
      mergePolicy: Replace
      data:
        {{- range $remoteKeyName, $remoteKey := $esoSecret.remoteKeys }}
          {{- if not (empty $remoteKey.name) }}
        {{ $remoteKeyName }}: "{{ printf "{{ .%s }}" (lower $remoteKeyName) }}"
          {{- end }}
        {{- end }}
  data:
  {{- range $remoteKeyName, $remoteKey := $esoSecret.remoteKeys }}
    {{- if not (empty $remoteKey.name) }}
  - secretKey: {{ lower $remoteKeyName }}
    remoteRef:
      key: {{ $remoteKey.name }}
    {{- end }}
  {{- end }}
          {{- end }}
        {{- end }}
    {{- end }}
{{- end }}
{{- end }}
