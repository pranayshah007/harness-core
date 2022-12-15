<#macro accountToken secret>
apiVersion: v1
kind: Secret
metadata:
  name: ${accountTokenName}
  namespace: ${delegateNamespace}
type: Opaque
data:
  DELEGATE_TOKEN: "${secret}"
</#macro>