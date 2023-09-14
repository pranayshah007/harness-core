{{/*
{{ include "harnesscommon.secrets.hasExtKubernetesSecret" (dict "variableName" "MY_VARIABLE" "extKubernetesSecretCtxs" (list .Values.secrets)) }}
*/}}
{{- define "harnesscommon.secrets.hasExtKubernetesSecret" -}}
{{- $hasExtKubernetesSecret := "false" -}}
{{- if .variableName -}}
  {{- range .extKubernetesSecretCtxs -}}
    {{- range . -}}
      {{- if and . .secretName .keys -}}
        {{- if and (hasKey .keys $.variableName) (get .keys $.variableName) -}}
          {{- $hasExtKubernetesSecret = "true" -}}
        {{- end -}}
      {{- end -}}
    {{- end -}}
  {{- end -}}
{{- end -}}
{{- print $hasExtKubernetesSecret -}}
{{- end -}}

{{/*
{{ include "harnesscommon.secrets.manageExtKubernetesSecretEnv" (dict "variableName" "MY_VARIABLE" "extKubernetesSecretCtxs" (list .Values.secrets)) }}
*/}}
{{- define "harnesscommon.secrets.manageExtKubernetesSecretEnv" -}}
{{- $secretName := "" -}}
{{- $secretKey := "" -}}
{{- if .variableName -}}
  {{- range .extKubernetesSecretCtxs -}}
    {{- range . -}}
      {{- if and . .secretName .keys -}}
        {{- $currSecretKey := (get .keys $.variableName) -}}
        {{- if and (hasKey .keys $.variableName) $currSecretKey -}}
          {{- $secretName = .secretName -}}
          {{- $secretKey = $currSecretKey -}}
        {{- end -}}
      {{- end -}}
    {{- end -}}
  {{- end -}}
  {{- if and $secretName $secretKey -}}
- name: {{ print .variableName }}
  valueFrom:
    secretKeyRef:
      name: {{ printf "%s" $secretName }}
      key: {{ printf "%s" $secretKey }}
  {{- end -}}
{{- end -}}
{{- end -}}

{{/*
{{ include "harnesscommon.secrets.hasESOSecret" (dict "variableName" "MY_VARIABLE" "esoSecretCtxs" (list .Values.secrets.secretManagement.externalSecretsOperator)) }}
*/}}
{{- define "harnesscommon.secrets.hasESOSecret" -}}
{{- $hasESOSecret := "false" -}}
{{- if .variableName -}}
  {{- range .esoSecretCtxs -}}
    {{- range . -}}
      {{- if and . .secretStore .secretStore.name .secretStore.kind -}}
        {{- $remoteKeyName := (dig "remoteKeys" $.variableName "name" "" .) -}}
        {{- if $remoteKeyName -}}
          {{- $hasESOSecret = "true" -}}
        {{- end -}}
      {{- end -}}
    {{- end -}}
  {{- end -}}
{{- end -}}
{{- print $hasESOSecret -}}
{{- end -}}

{{/*
{{ include "harnesscommon.secrets.manageESOSecretEnv" (dict "variableName" "MY_VARIABLE" "esoSecretCtxs" (list .Values.secrets.secretManagement.externalSecretsOperator)) }}
*/}}
{{- define "harnesscommon.secrets.manageESOSecretEnv" -}}
{{- $secretName := "" -}}
{{- $secretKey := "" -}}
{{- if .variableName -}}
  {{- range .esoSecretCtxs -}}
    {{- range . -}}
      {{- if and . .secretStore .secretStore.name .secretStore.kind -}}
        {{- $remoteKeyName := (dig "remoteKeys" $.variableName "name" "" .) -}}
        {{- if $remoteKeyName -}}
          {{- $secretName = "test-1" -}}
          {{- $secretKey = $.variableName -}}
        {{- end -}}
      {{- end -}}
    {{- end -}}
  {{- end -}}
{{- end -}}
  {{- if and $secretName $secretKey -}}
- name: {{ print .variableName }}
  valueFrom:
    secretKeyRef:
      name: {{ printf "%s" $secretName }}
      key: {{ printf "%s" $secretKey }}
  {{- end -}}
{{- end -}}

{{/*
{{ include "harnesscommon.secrets.getExternalKubernetesSecretName" (dict "secretsCtx" .Values.secrets.kubernetesSecrets "globalSecretsCtx" .Values.Global "secret" "MONGO_USER") }}
*/}}
{{- define "harnesscommon.secrets.getExternalKubernetesSecretName" -}}
{{- $secret := .secret -}}
{{- $kubernetesSecretName := "" -}}
{{- if not (empty .secretsCtx) -}}
  {{- range $secretIdx, $kubernetesSecret := .secretsCtx -}}
    {{- if not (empty $kubernetesSecret.secretName) -}}
      {{- with $kubernetesSecret.keys -}}
        {{- if and (hasKey . $secret) (not (empty (get . $secret))) -}}
          {{- $kubernetesSecretName = $kubernetesSecret.secretName -}}
        {{- end -}}
      {{- end -}}
    {{- end -}}
  {{- end -}}
{{- end -}}
{{- if and (eq $kubernetesSecretName "") (not (empty .globalSecretsCtx)) -}}
{{- $kubernetesSecretName = (include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" .globalSecretsCtx "secret" $secret)) -}}
{{- end -}}
{{- print $kubernetesSecretName -}}
{{- end -}}

{{/*
{{ include "harnesscommon.secrets.getExtSecretKey" (dict "secretsCtx" .Values.secrets.kubernetesSecrets "secret" "MONGO_USER") }}
*/}}
{{- define "harnesscommon.secrets.getExtSecretKey" -}}
{{- $secret := .secret -}}
{{- $kubernetesSecretName := "" -}}
  {{- range $secretIdx, $kubernetesSecret := .secretsCtx -}}
    {{- if not (empty $kubernetesSecret.secretName) -}}
      {{- with $kubernetesSecret.keys -}}
        {{- if and (hasKey . $secret) (not (empty (get . $secret))) -}}
          {{- $kubernetesSecretName = (get . $secret) -}}
        {{- end -}}
      {{- end -}}
    {{- end -}}
  {{- end -}}
  {{- print $kubernetesSecretName -}}
{{- end -}}


{{/*
Check if valid ESO Secrets are provided in the secrets context

Returns:
  "true" if ESO Secrets are provided
  "false" if ESO Secrets are not provided 

Example:
{{ include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" .Values.secrets) }}
*/}}
{{- define "harnesscommon.secrets.hasESOSecrets" -}}
{{- $hasESOSecrets := "false" -}}
{{- if and .secretsCtx .secretsCtx.secretManagement .secretsCtx.secretManagement.externalSecretsOperator -}}
  {{- with .secretsCtx.secretManagement.externalSecretsOperator -}}
    {{- range $externalSecretIdx, $externalSecret := . -}}
      {{- $hasESOSecrets = include "harnesscommon.secrets.hasValidESOSecret" (dict "esoSecretCtx" .) -}}
    {{- end -}}
  {{- end -}}
{{- end -}}
{{- print $hasESOSecrets -}}
{{- end -}}

{{/*
Check validity of ESO Secret in the externalSecretsOperator secret context

Returns:
  "true" if ESO Secret is Valid
  "false" if ESO Secret is not Valid 

Example:
{{ include "harnesscommon.secrets.hasValidESOSecret" (dict "esoSecretCtx" .externalSecretsOperator[idx]) }}
*/}}
{{- define "harnesscommon.secrets.hasValidESOSecret" -}}
{{- $hasValidESOSecret := "false" -}}
  {{- if and .esoSecretCtx .esoSecretCtx.secretStore .esoSecretCtx.secretStore.name .esoSecretCtx.secretStore.kind -}}
    {{- range $remoteKeyIndex, $remoteKey := .esoSecretCtx.remoteKeys -}}
      {{- if $remoteKey.name -}}
        {{- $hasValidESOSecret = "true" -}}
      {{- end -}}
    {{- end -}}
  {{- end -}}
{{- print $hasValidESOSecret -}}
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
  {{- printf "%s-%s-ext-secret-%s" $.Chart.Name $secretContextIdentifier $secretIdentifier  -}}
{{- else -}}
  {{- print (and (not (empty .ctx)) $secretContextIdentifier $secretIdentifier) -}}
{{- end -}}
{{- end -}}

{{/*
Generates ESO External Secret CRD

Example:
{{ include "harnesscommon.secrets.generateExternalSecret" (dict "ctx" . "secretsCtx" .Values.secrets "secretIdentifier" "local") }}
*/}}
{{- define "harnesscommon.secrets.generateExternalSecret" -}}
{{- $ := .ctx -}}
{{- if and .secretsCtx .secretsCtx.secretManagement .secretsCtx.secretManagement.externalSecretsOperator -}}
    {{- with .secretsCtx.secretManagement.externalSecretsOperator -}}
        {{- range $esoSecretIdx, $esoSecret := . -}}
          {{- if eq (include "harnesscommon.secrets.hasValidESOSecret" (dict "esoSecretCtx" .)) "true" -}}
            {{- $esoSecretName := include "harnesscommon.secrets.esoSecretName" (dict "ctx" $ "secretContextIdentifier" "local" "secretIdentifier" $esoSecretIdx) -}}
            {{- if gt $esoSecretIdx 0 -}}
{{ printf "\n---"  }}
            {{- end -}}
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
        {{- end -}}
    {{- end -}}
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
  {{- end -}}
{{- end -}}
{{- end -}}

{{/*
Generates env object for Secret

Example:
{{ include "harnesscommon.secrets.manageEnv" (dict "ctx" . "variableName" "MY_VARIABLE" "userProvidedValues" (list "values.secret1" "")  "extKubernetesSecretCtxs" (list .Values.secrets) "esoSecretCtxs" (list (dict "" .Values.secrets.secretManagement.externalSecretsOperator))) }}
*/}}
{{- define "harnesscommon.secrets.manageEnv" -}}
{{- if eq (include "harnesscommon.secrets.hasESOSecret" (dict "variableName" .variableName "esoSecretCtxs" .esoSecretCtxs)) "true" -}}
{{- else if eq (include "harnesscommon.secrets.hasExtKubernetesSecret" (dict "variableName" .variableName "extKubernetesSecretCtxs" .extKubernetesSecretCtxs)) "true" -}}
  {{- include "harnesscommon.secrets.manageExtKubernetesSecretEnv" (dict "variableName" .variableName "extKubernetesSecretCtxs" .extKubernetesSecretCtxs) -}}
{{- end -}}
{{- end -}}
