<#macro scale>
apiVersion: autoscaling/v1
kind: HorizontalPodAutoscaler
metadata:
   name: ${delegateName}-hpa
   namespace: ${delegateNamespace}
   labels:
       harness.io/name: ${delegateName}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ${delegateName}
  minReplicas: 1
  maxReplicas: 1
  targetCPUUtilizationPercentage: 99
</#macro>