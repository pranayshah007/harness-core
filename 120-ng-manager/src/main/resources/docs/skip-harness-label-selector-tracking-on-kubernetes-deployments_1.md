# Labels in Harness Kubernetes canary phases

Default Harness Kubernetes canary deployments are a two-phase process that relies on deployment object labels for tracking. 

1. **Canary Phase**
	1. Harness creates a canary version of the Kubernetes deployment object defined in your service definition **Manifests** section.
	2. All pods in the canary phase have the label `harness.io/track: canary`. Traffic could be routed to these pods using this label.
	3. Once this deployment is verified, the canary delete step deletes the deployment by default.    
   
	Using this method, Harness provides a canary pod set as a way to test the new build, run your verification.
2. **Primary Phase**
	1. Runs the actual deployment using a Kubernetes rolling update with the number of pods you specify in the **Manifests** files. For example, `replicas: 3`.
	2. All pods in the primary phase have the label `harness.io/track: stable`.
