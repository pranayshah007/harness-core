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
Generates ESO External Secret CRD

Example:
{{ include "harnesscommon.secrets.generateExternalSecret" (dict "secretsCtx" .Values.secrets "secretNamePrefix" "my-secret") }}
*/}}
{{- define "harnesscommon.secrets.generateExternalSecret" -}}
{{- $externalSecretIdx := 0 -}}
{{- if and .secretsCtx .secretsCtx.secretManagement .secretsCtx.secretManagement.externalSecretsOperator -}}
    {{- with .secretsCtx.secretManagement.externalSecretsOperator -}}
        {{- range . -}}
          {{- if eq (include "harnesscommon.secrets.hasValidESOSecret" (dict "esoSecretCtx" .)) "true" -}}
            {{- if gt $externalSecretIdx 0 -}}
{{ printf "\n---"  }}
            {{- end -}}
            {{- $externalSecretIdx = add1 $externalSecretIdx -}}
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: {{ printf "%s-external-secret-%d" $.secretNamePrefix $externalSecretIdx }}
spec:
  secretStoreRef:
    name:  {{ .secretStore.name }}
    kind: {{ .secretStore.kind }}
  target:
    name: {{ printf "%s-external-secret-%d" $.secretNamePrefix $externalSecretIdx }}
    template:
      engineVersion: v2
      mergePolicy: Replace
      data:
        {{- range $remoteKeyName, $remoteKey := .remoteKeys }}
          {{- if not (empty $remoteKey.name) }}
        {{ $remoteKeyName }}: "{{ printf "{{ .%s }}" (lower $remoteKeyName) }}"
          {{- end }}
        {{- end }}
  data:
  {{- range $remoteKeyName, $remoteKey := .remoteKeys }}
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