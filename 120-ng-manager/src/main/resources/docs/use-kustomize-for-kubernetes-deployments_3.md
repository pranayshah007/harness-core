# Limitations

* Harness supports Kustomize and Kustomize Patches for [Rolling](../../cd-execution/kubernetes-executions/create-a-kubernetes-rolling-deployment.md), [Canary](../../cd-execution/kubernetes-executions/create-a-kubernetes-canary-deployment.md), [Blue Green](../../cd-execution/kubernetes-executions/create-a-kubernetes-blue-green-deployment.md) strategies, and the Kubernetes [Apply](../../cd-technical-reference/cd-k8s-ref/kubernetes-apply-step.md) and [Delete](../../cd-execution/kubernetes-executions/delete-kubernetes-resources.md) steps.
* Harness does not use Kustomize for rollback. Harness renders the templates using Kustomize and then passes them onto kubectl. A rollback works exactly as it does for native Kubernetes.
* You cannot use Harness variables in the base manifest or kustomization.yaml. You can only use Harness variables in kustomize patches you add in **Kustomize Patches Manifest Details**.
* **Kustomize binary versions:**  
Harness includes Kustomize binary versions 3.5.4 and 4.0.0. By default, Harness uses 3.5.4. To use 4.0.0, you must enable the feature flag `NEW_KUSTOMIZE_BINARY` in your account. Contact [Harness Support](mailto:support@harness.io) to enable the feature.
* Harness will not follow symlinks in the Kustomize and Kustomize Patches files it pulls.
