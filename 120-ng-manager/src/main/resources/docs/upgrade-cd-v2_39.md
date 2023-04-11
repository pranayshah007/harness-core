# NOTE: the Environment reference maps the infrastructure definition to the environment

  environmentRef: staging
  deploymentType: Kubernetes
  type: KubernetesDirect
  spec:
    connectorRef: pmk8scluster
    namespace: <+input>.allowedValues(dev,qa,prod)
    releaseName: release-<+INFRA_KEY>
  allowSimultaneousDeployments: false
```
