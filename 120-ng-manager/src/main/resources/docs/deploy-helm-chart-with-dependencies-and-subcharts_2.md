# Service configuration

To configure Helm subcharts, you must define the subchart name and path in your service YAML. 

To resolve dependencies, you must configure the Helm command `Template` with the flag, `--dependency-update`. This allows Harness to fetch your dependencies defined in `Chart.yaml`.

:::important
Helm charts store their dependencies in the `charts/` folder. Make sure that all subcharts are located within the `charts/` folder inside your parent chart. Look at the sample [Harness respository](https://github.com/thisrohangupta/custom-remote-test-repo/tree/main/parent-chart) for structural guidance.

Here is a sample directory: 

```
charts/
	- subchart1/
		 - templates/
			 - app.yaml
		 - Chart.yaml
		 - values.yaml
```
:::

Here is a sample service YAML where a subchart is defined.

```
service:
  name: K8sHelmSubChart
  identifier: K8sHelmSubChart
  serviceDefinition:
    type: Kubernetes
    spec:
      manifests:
        - manifest:
            identifier: m1
            type: HelmChart
            spec:
              store:
                type: Github
                spec:
                  connectorRef: gitHubAchyuth
                  gitFetchType: Branch
                  folderPath: parent-chart
                  branch: main
              subChartName: first-child
              skipResourceVersioning: false
              enableDeclarativeRollback: false
              helmVersion: V3
              commandFlags:
                - commandType: Template
                  flag: "--dependency-update"
  gitOpsEnabled: false

```