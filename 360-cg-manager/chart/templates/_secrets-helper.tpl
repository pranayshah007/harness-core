{{/*
Generates env object for Secret

Example:
{{ include "harnesscommon.secrets.manageEnv" (dict "ctx" . "variableName" "MY_VARIABLE" "userProvidedValues" (list "values.secret1" "")  "extKubernetesSecretCtxs" (list .Values.secrets) "esoSecretCtxs" (list (dict "" .Values.secrets.secretManagement.externalSecretsOperator))) }}
*/}}
{{- define "harnesscommon.secrets.manageEnv" -}}
{{- $ := .ctx -}}
{{- if eq (include "harnesscommon.secrets.hasESOSecret" (dict "variableName" .variableName "esoSecretCtxs" .esoSecretCtxs)) "true" -}}
  {{- include "harnesscommon.secrets.manageESOSecretEnv" (dict "ctx" $ "variableName" .variableName "esoSecretCtxs" .esoSecretCtxs) -}}
{{- else if eq (include "harnesscommon.secrets.hasExtKubernetesSecret" (dict "variableName" .variableName "extKubernetesSecretCtxs" .extKubernetesSecretCtxs)) "true" -}}
  {{- include "harnesscommon.secrets.manageExtKubernetesSecretEnv" (dict "variableName" .variableName "extKubernetesSecretCtxs" .extKubernetesSecretCtxs) -}}
{{- end -}}
{{- end -}}
