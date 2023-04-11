# Step 2: Create Pipeline

We'll create a quick CD Pipeline that deploys a public manifest and image to a local Kubernetes cluster. The following steps are similar to the [Kubernetes CD Quickstart](kubernetes-cd-quickstart.md).

1. In Harness, click **Create Project**.
2. In **About the Project**, in **Name**, enter **Quickstart**, and then click **Save and Continue**.
3. In **Invite Collaborators**, click **Save and Continue**. Your new project appears. Let's add a CD Pipeline.
4. In **Setup Pipeline**, enter the name **quickstart**, and then click **Start**. Your new Pipeline is started!

  Now let's jumpstart your Pipeline setup by pasting in the YAML for a Pipeline. Once it's pasted in, we'll update a few placeholders and then deploy.

  Copy the following YAML:

  ```yaml
  pipeline:  
      name: quickstart  
      identifier: quickstart  
      projectIdentifier: Quickstart  
      orgIdentifier: default  
      tags: {}  
      stages:  
          - stage:  
                name: demo  
                identifier: demo  
                description: ""  
                type: Deployment  
                spec:  
                    serviceConfig:  
                        serviceRef: <+input>  
                        serviceDefinition:  
                            type: Kubernetes  
                            spec:  
                                variables: []  
                                manifests:  
                                    - manifest:  
                                          identifier: nginx  
                                          type: K8sManifest  
                                          spec:  
                                              store:  
                                                  type: Github  
                                                  spec:  
                                                      connectorRef: <+input>  
                                                      gitFetchType: Branch  
                                                      paths:  
                                                          - content/en/examples/application/nginx-app.yaml  
                                                      repoName: <+input>  
                                                      branch: main  
                                              skipResourceVersioning: false  
                    infrastructure:  
                        environmentRef: <+input>  
                        infrastructureDefinition:  
                            type: KubernetesDirect  
                            spec:  
                                connectorRef: <+input>  
                                namespace: <+input>  
                                releaseName: release-<+INFRA_KEY>  
                        allowSimultaneousDeployments: false  
                    execution:  
                        steps:  
                            - step:  
                                  type: K8sRollingDeploy  
                                  name: Rollout Deployment  
                                  identifier: Rollout_Deployment  
                                  spec:  
                                      skipDryRun: false  
                                  timeout: 10m  
                        rollbackSteps: []  
                    serviceDependencies: []  
                tags: {}  
                failureStrategies:  
                    - onFailure:  
                          errors:  
                              - AllErrors  
                          action:  
                              type: StageRollback
  ```
1. In the Pipeline Studio, click **YAML**.
  ![](./static/harness-community-edition-quickstart-134.png)
2. Click **Edit YAML**.
3. Replace all of the YAML with the YAML you copied above.
4. Click **Save**.
5. Click **Visual**. The new Pipeline is created.
  ![](./static/harness-community-edition-quickstart-135.png)

  Let's quickly review some key Pipeline concepts:

  * **Harness Delegate:** the Harness Delegate is a software service you install in your environment that connects to the Harness Manager and performs tasks using your container orchestration platforms, artifact repositories, monitoring systems, etc.
  * **Pipelines:** A CD Pipeline is a series of Stages where each Stage deploys a Service to an Environment.
  * **Stages:** A CD Stage is a subset of a Pipeline that contains the logic to perform one major segment of the deployment process.
  * **Services:** A Service represents your microservices and other workloads logically. A Service is a logical entity to be deployed, monitored, or changed independently.
  * **Service Definition:** Service Definitions represent the real artifacts, manifests, and variables of a Service. They are the actual files and variable values.
  * **Environments:** Environments represent your deployment targets logically (QA, Prod, etc).
  * **Infrastructure Definition:** Infrastructure Definitions represent an Environment's infrastructure physically. They are the actual target clusters, hosts, etc.
  * **Execution Steps:** Execution steps perform the CD operations like applying a manifest, asking for approval, rollback, and so on. Harness automatically adds the steps you need for the deployment strategy you select. You can then add additional steps to perform many other operations.
  * **Connectors:** Connectors contain the information necessary to integrate and work with 3rd party tools such as Git providers and artifact repos. Harness uses Connectors at Pipeline runtime to authenticate and perform operations with a 3rd party tool.

  You'll notice a Runtime Input expression `<+input>` for most of the settings. These are placeholders we'll replace when we run the Pipeline.
10. Click **Run**. The **Run Pipeline** settings appear.

![](./static/harness-community-edition-quickstart-136.png)

Now let's update the placeholders.

