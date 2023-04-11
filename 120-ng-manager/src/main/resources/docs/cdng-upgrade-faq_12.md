# What is not upgraded from Harness CD FirstGen?

The following FirstGen entities and configurations are not upgraded in NextGen:

- **Delegates**. Harness NextGen leverages new delegates for executing NextGen workloads.
    
    You can run NextGen delegates alongside your FirstGen delegates as you are upgrading. There will be no impact to your existing workloads while running both sets of delegates.
  
  - For details on NextGen delegates, go to [Delegate overview](https://developer.harness.io/docs/platform/Delegates/delegate-overview).
- **Triggers**. Harness won’t upgrade FirstGen triggers. You will need to reconfigure triggers for any upgraded pipelines.
  
  - For details on NextGen triggers, go to [Triggers](https://developer.harness.io/docs/category/triggers).
- **Infrastructure provisioners**. Harness CD NextGen no longer provides the construct of infrastructure provisioners as it did in FirstGen.
    
    Infrastructure provisioner capabilities are condensed into execution steps in NextGen. Harness can orchestrate infrastructure by using Terraform, Terragrunt, CloudFormation, shell script provisioning, and Azure ARM and Blueprint.
  
  - For details on NextGen infrastructure provisioning, go to [Terraform](https://developer.harness.io/docs/category/terraform) and [CloudFormation](https://developer.harness.io/docs/category/cloudformation).
- **AWS ECS**. FirstGen ECS deployments can't migrate to NextGen ECS pipelines because the NextGen ECS integration is redesigned.
    
    NextGen now supports rolling, canary, and load balancer–based blue-green deployments. We have deprecated the FirstGen Service Setup and Wait for Steady State steps. Harness NextGen can still use your existing service and infrastructure configurations.

  - For details on NextGen ECS integration, go to [ECS deployment tutorial](../cd-quickstarts/ecs-deployment-tutorial.md).
- **Variable references in manifests and steps**. The Harness variable reference format has changed in NextGen.
    
    In FirstGen, variables use the `${...}` format. In NextGen, variables use the `<+...>` format. You must upgrade your references to the new format.
 
  - For details on NextGen variables, go to [Variables and expressions](https://developer.harness.io/docs/category/variables-and-expressions).
- **Tag management**. Harness CD NextGen does not use tag management in the same way as Harness FirstGen.
    
    NextGen does support tags, but there is not a centralized management feature for them as in FirstGen.

  - For details on NextGen tags, go to [Tags reference](https://developer.harness.io/docs/platform/References/tags-reference).
- **SSO providers**. NextGen supports the same SSO providers as FirstGen, but you must reconfigure the SSO provider setup in NextGen.

  - For details on NextGen SSO providers, go to [Authentication overview](https://developer.harness.io/docs/platform/Authentication/authentication-overview).
- **Deployment history for CD dashboards**. Harness NextGen does not retain the deployment data from FirstGen. Harness will recalculate the deployment stats and metrics by using NextGen CD deployments.

  - For details on NextGen dashboards, go to [Monitor deployments and services in CD dashboards](https://developer.harness.io/docs/continuous-delivery/cd-dashboards/monitor-cd-deployments).
- **API automation**. Harness will not upgrade API automation built with FirstGen GraphQL APIs.
    
    GraphQL APIs are not used in NextGen. They are replaced with REST APIs. The API endpoints are different and take different arguments. You must rewrite API-based automation to reintegrate with Harness NextGen.

  - For details on NextGen APIs, go to [APIs](https://developer.harness.io/docs/category/apis).
- **Artifact collection**. Harness will not migrate the artifact history of a FirstGen artifact source to NextGen.
    
    Harness NextGen does not perform artifact polling to collect the list of artifacts and maintain a history for future selection. NextGen fetches the list of tags at pipeline runtime. 

  - For details on NextGen artifact collection, review the artifact sources covered in [Harness Kubernetes services](https://developer.harness.io/docs/continuous-delivery/cd-services/k8s-services/kubernetes-services).
