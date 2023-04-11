# Add deploy stage and service

For steps on adding a stage, see [Add a Stage](../../../platform/8_Pipelines/add-a-stage.md).

1. When you add a stage, select **Deploy**.
2. Name the stage, and select what you'd like to deploy. For example, select **Service**.
3. Click **Set Up Stage**.
   
   The new stage's settings appear.
4. In **Service**, create or select a Service.
5. In Service Definition **Deployment Type**, select **Kubernetes**.
6. Add artifacts and manifests. See:

   + [Add Container Images as Artifacts for Kubernetes Deployments](../../cd-advanced/cd-kubernetes-category/add-artifacts-for-kubernetes-deployments.md)
   + [Add Kubernetes Manifests](../../cd-advanced/cd-kubernetes-category/define-kubernetes-manifests.md)
7. Click **Next** or **Infrastructure**.
