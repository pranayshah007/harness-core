{{/*
{{ include "harnesscommon.secrets.hasESOSecrets" (dict "secretsCtx" .Values.secrets "globalSecretsCtx" .Values.global) }}
*/}}
{{- define "harnesscommon.secrets.hasESOSecrets" -}}
{{- $hasESOSecrets := "false" -}}
{{- if not (empty .secretsCtx) -}}
  {{- with .secretsCtx.secretManagement -}}
      {{- with .externalSecretsOperator -}}
          {{- range $externalSecretIdx, $externalSecret := . -}}
            {{- if and $externalSecret.secretStore $externalSecret.secretStore.name $externalSecret.secretStore.kind -}}
                {{- range $remoteKeyIndex, $remoteKey := $externalSecret.remoteKeys -}}
                    {{- if not (empty $remoteKey.name) -}}
                        {{- $hasESOSecrets = "true" -}}
                    {{- end -}}
                {{- end -}}
            {{- end -}}
          {{- end -}}
      {{- end -}}
  {{- end -}}
{{- end -}}
{{- print $hasESOSecrets -}}
{{- end -}}

{{/*
{{ include "harnesscommon.secrets.getExtSecretName" (dict "secretsCtx" .Values.secrets.kubernetesSecrets "globalSecretsCtx" .Values.Global "secret" "MONGO_USER") }}
*/}}
{{- define "harnesscommon.secrets.getExtSecretName" -}}
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
{{ include "harnesscommon.secrets.generateExtSecret" (dict "secretsCtx" .Values.secrets "secretNamePrefix" "my-secret") }}
*/}}
{{- define "harnesscommon.secrets.generateExtSecret" -}}
{{- if not (empty .secretsCtx) -}}
  {{- with .secretsCtx.secretManagement -}}
      {{- with .externalSecretsOperator -}}
          {{- range $externalSecretIdx, $externalSecret := . -}}
            {{- if and $externalSecret.secretStore $externalSecret.secretStore.name $externalSecret.secretStore.kind }}
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: {{ printf "%s-external-secret-%d" $.secretNamePrefix $externalSecretIdx }}
spec:
  secretStoreRef:
    name:  {{ $externalSecret.secretStore.name }}
    kind: {{ $externalSecret.secretStore.kind }}
  target:
    name: {{ printf "%s-external-secrets" $.secretNamePrefix }}
    template:
      engineVersion: v2
      data:
        {{- range $remoteKeyName, $remoteKey := $externalSecret.remoteKeys }}
          {{- if not (empty $remoteKey.name) }}
        {{ $remoteKeyName }}: "{{ printf "{{ .%s }}" (lower $remoteKeyName) }}"
          {{- end }}
        {{- end }}
  data:
  {{- range $remoteKeyName, $remoteKey := $externalSecret.remoteKeys }}
    {{- if not (empty $remoteKey.name) }}
  - secretKey: {{ lower $remoteKeyName }}
    remoteRef:
      key: {{ $remoteKey.name }}
    {{- end }}
  {{- end }}
            {{- end }}
{{ indent 0 "---"  }}
          {{- end -}}
      {{- end -}}
  {{- end -}}
{{- end -}}
{{- end -}}