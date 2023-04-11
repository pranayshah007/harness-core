# Pipeline execution of a Helm chart with subcharts

During pipeline execution, Harness fetches the subcharts and dependencies for the deployment based on the values in the service YAML. 

You can see the subchart and the list of files fetched in the fetch section of the pipeline execution log.

![](./static/helm-subchart-fetch.png)

You can see the `template` command with the `--dependency-update` flag running in the prepare section of the pipeline execution.

![](./static/helm-dependency.png)
