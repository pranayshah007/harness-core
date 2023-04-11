## Two Kubernetes services

If you have more than one service, Harness does not automatically know which is the primary service unless you add the annotations like this: 

* The **primary** service uses this annotation: `annotations: harness.io/primary-service: "true"`. You must have this annotation added in your manifest.
* The **stage** service uses this annotation: `annotations: harness.io/stage-service: "true"`. You must have this annotation added in your manifest.

Here's an example:

```yaml
apiVersion: v1  
kind: Service  
metadata:  
  name: test-deploy-svc-1  
  annotations:  
    harness.io/primary-service: "true"  
spec:  
  type: ClusterIP  
  ports:  
  - port: 80  
    targetPort: 80  
    protocol: TCP  
  selector:  
    app: nginx  
---  
apiVersion: v1  
kind: Service  
metadata:  
  name: test-deploy-svc-2  
  annotations:  
    harness.io/stage-service: "true"  
spec:  
  type: ClusterIP  
  ports:  
  - port: 80  
    targetPort: 80  
    protocol: TCP  
  selector:  
    app: nginx
```
If you use two services, please annotate them as described.

Let's look at the deployment process using two Kubernetes services:  


1. **First deployment:**
	1. Harness creates two services (primary and stage) and one pod set for the app.
	2. The primary service uses this annotation: `annotations: harness.io/primary-service: "true"`. You must have this annotation added in your manifest.
	3. The stage service uses this annotation: `annotations: harness.io/stage-service: "true"`. You must have this annotation added in your manifest.
	4. The pod set is given an annotation of `harness.io/color: blue`.
	5. Harness points the stage service at the pod set and verifies that the set reached steady state.
	6. Harness swaps the primary service to pod set. Production traffic now flows to the app.
2. **Second deployment (new version of the same app):**
	1. Harness creates a new pod set for new app version. The pod set is given the annotation `harness.io/color: green`.
	2. Harness points the stage service at new pod set (with new app version) and verifies that the set reached steady state.
	3. Harness swaps the primary service to new pod set, stage service to old pod set.
3. **Third deployment:**
	1. Harness deploy new app version to the pod set not using the primary service.
	2. Harness points the stage service at new pod set (with new app version) and verifies that the set reached steady state.
	3. Harness swaps the primary service to new pod set, stage service to old pod set.
