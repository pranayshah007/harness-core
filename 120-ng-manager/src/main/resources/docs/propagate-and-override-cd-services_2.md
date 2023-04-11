# Important notes

* Service propagation is not supported when using multiple services in a single stage (multi service deployments).
  * For details on using multiple services, go to [Use multiple services and multiple environments in a deployment](../cd-services-general/../../cd-deployments-category/multiserv-multienv.md).
* Service propagation is progressive: you can only propagate services from stage to stage in a forward direction in your pipeline. For example, Stage 2 cannot propagate a service from a subsequent Stage 3.
* In a pipeline's **Advanced Options**, in **Stage Execution Settings**, you can set up selective stage executions. This allows you to select which stages to deploy at runtime.
  * If you select a stage that uses a propagated service (a child service), that stage will not work.
  * This is because the parent service's settings must be resolved as part of the deployment. Additionally, if the child service is overriding the parent service's settings, Harness cannot ensure that the settings can be overridden correctly without deploying the parent service.
* When propagation is set up between a parent stage and child stage, moving the parent or child stage out of sequence resets any propagated settings to their defaults.
  * If you do this, you are prompted to confirm. If you confirm, the stages are reset to their defaults.
  
![](./static/propagate-and-override-cd-services-00.png)
