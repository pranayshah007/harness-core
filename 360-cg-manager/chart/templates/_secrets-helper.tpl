{{/*
Generates env object for Secret

Example:
{{ include "harnesscommon.secrets.manageEnv" (dict "ctx" . "variableName" "MY_VARIABLE" "kubernetesSecretName" "secretName" "kubernetesSecretKey" "secretKey" "defaultKubernetesSecretName" "defaultSecretName" "defaultKubernetesSecretKey" "defaultSecretKey" "providedSecretValues" (list "values.secret1" "")  "extKubernetesSecretCtxs" (list .Values.secrets) "esoSecretCtxs" (list (dict "" .Values.secrets.secretManagement.externalSecretsOperator))) }}
*/}}
{{- define "harnesscommon.secrets.manageEnv" }}
{{- $ := .ctx }}
{{- if eq (include "harnesscommon.secrets.hasESOSecret" (dict "variableName" .variableName "esoSecretCtxs" .esoSecretCtxs)) "true" }}
  {{- include "harnesscommon.secrets.manageESOSecretEnv" (dict "ctx" $ "variableName" .variableName "esoSecretCtxs" .esoSecretCtxs) }}
{{- else if eq (include "harnesscommon.secrets.hasExtKubernetesSecret" (dict "variableName" .variableName "extKubernetesSecretCtxs" .extKubernetesSecretCtxs)) "true" }}
  {{- include "harnesscommon.secrets.manageExtKubernetesSecretEnv" (dict "variableName" .variableName "extKubernetesSecretCtxs" .extKubernetesSecretCtxs) }}
{{- else if eq (include "harnesscommon.secrets.hasprovidedSecretValues" (dict "ctx" $ "providedSecretValues" .providedSecretValues)) "true" }}
  {{- include "harnesscommon.secrets.manageProvidedSecretValuesEnv" (dict "ctx" $ "variableName" .variableName "providedSecretValues" .providedSecretValues) }}
{{- else }}
  {{- if and (not (empty .kubernetesSecretName)) (not (empty .kubernetesSecretKey)) }}
    {{- include "harnesscommon.secrets.manageDefaultKubernetesSecretEnv" (dict "variableName" .variableName "defaultKubernetesSecretName" .kubernetesSecretName "defaultKubernetesSecretKey" .kubernetesSecretKey) }}
  {{- else }}
    {{- include "harnesscommon.secrets.manageDefaultKubernetesSecretEnv" (dict "variableName" .variableName "defaultKubernetesSecretName" .defaultKubernetesSecretName "defaultKubernetesSecretKey" .defaultKubernetesSecretKey) }}
  {{- end }}
{{- end }}
{{- end }}