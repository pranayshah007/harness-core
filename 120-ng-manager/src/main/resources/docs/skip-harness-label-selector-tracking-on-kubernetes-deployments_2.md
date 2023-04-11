# Invalid value LabelSelector

Kubernetes labels are immutable. If a specific deployment object already exists in the cluster, you cannot change its labels.

If the deployment object already exists before the Harness deployment, it might conflict with the default Harness Kubernetes canary deployment due to the use of the label `harness.io/track`.

If you are deploying different Harness deployment objects to the same cluster, you might encounter a selector error.


```
The Deployment “harness-example-deployment” is invalid: spec.selector:   
  Invalid value: v1.LabelSelector{MatchLabels:map[string]string{“app”:“harness-example”},   
  MatchExpressions:[]v1.LabelSelectorRequirement{}}: field is immutable
```

Most often, you can delete or rename the deployment object. In some cases, you will experience downtime when the object restarts. 

As an alternative, you can force Harness to skip the `harness.io/track: stable` label in the canary deployment.
