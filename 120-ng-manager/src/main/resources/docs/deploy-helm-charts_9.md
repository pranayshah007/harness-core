# Step 1: Add the Helm chart

Adding a Helm chart is a simple process of connecting Harness to the Git or HTTP Helm repo where your chart is located.

1. In your CD stage, click **Service**.
2. In **Service Definition**, select **Kubernetes**.
3. In **Manifests**, click **Add Manifest**.
4. In **Specify Manifest Type**, select **Helm Chart**, and click **Continue**.
   
   ![](./static/deploy-helm-charts-02.png)
5. In **Specify Helm Chart Store**, select the type of repo or or cloud storage service (Google Cloud Storage, AWS S3) you're using.

For the steps and settings of each option, see the [Connect to an Artifact Repo](../../../platform/7_Connectors/connect-to-an-artifact-repo.md) How-tos.

If you are using Google Cloud Storage or Amazon S3, see [Cloud Platform Connectors](/docs/category/cloud-platform-connectors).

You can also use a local Helm chart if you are deploying the same Helm chart and version to many clusters/namespaces in parallel. For information, see [Use a local Helm Chart](use-a-local-helm-chart.md). For all of the Helm Chart Store types (Git, GitHub, HTTP Helm, OCI, etc), you will need to provide the following Helm info:


- **Manifest Identifier**: Enter a name that identifies this Helm chart. It doesn't have to be the chart name. It can be the name of the service you are deploying or another name. Ex: `helm_chart`.
- **Chart name**: Enter the name of the Helm chart for Harness to pull. Don't include the chart version. You will add that in the **Chart Version** setting. Ex: `todolist`.
- **Chart Version**: Enter the version of the chart you want to deploy. This is found in the Chart.yaml `version` label in your chart. You can list all available versions of a chart using the `search repo` command with the `--versions` option. See [helm search repo](https://helm.sh/docs/helm/helm_search_repo) from Helm.
  - If you leave **Chart Version** empty Harness gets the latest chart.
  - If you are going to use a Harness trigger to run this pipeline when a new version is added to your chart repo, select the **Runtime Input** option. When you set up the trigger, you will select this chart and Harness will listen on the repo for new versions. See [Trigger Pipelines on New Helm Chart](../../../platform/11_Triggers/trigger-pipelines-on-new-helm-chart.md). For example, `1.4.1`.
- **Helm Version**: Select the version of Helm used in your chart. See [Helm Version Support Policy](https://helm.sh/docs/topics/version_skew/) from Helm. For example, `Version 2`.
- **Values YAML**: Your chart will have a default values.yaml file in its root folder.
  - If you do not enter a values.yaml in **Values YAML**, Harness uses the default values.yaml file in the root of the chart.
  - If you want to use a different values.yaml file, enter the path to that file.
  - For example, let's imagine a Helm Chart with the following Values YAML files:
  * dev-values.yaml
  * qa-values.yaml
  * prod-values.yaml
  * sample-chart/test-values.yaml

  You can specify the values YAML file based using a path to the file within the retrieved Helm chart.

  If you have additional values YAML files in the chart, and you want to use those to override some settings of the default values.yaml file for this deployment, you can enter the addition values YAML file(s) in **Values YAML**.

  For each additional values YAML file, specify its location within this chart. Enter the location from the root of the chart to the values.yaml file.

  If a values YAML file is located in a folder, enter the path from the root of the chart to the folder and values.yaml.

  The values YAML file(s) must be in this chart. You cannot enter a location to a values YAML file in a chart located somewhere else.If you use multiple files in **Values YAML**, priority is given from the last file to the first file.

  For example, let's say you have 3 files: the default values.yaml, values2.yaml added next, and values3.yaml added last. 
  
  ![](static/deploy-helm-charts-05.png)
  
  All files contain the same key:value pair. 

  The values3.yaml key:value pair overrides the key:value pair of values2.yaml and values.yaml files.

  You can also select **Expression** and use [Harness expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md) in this setting. The resolved expression must be the name of a Values YAML file in the chart. For example, you could create a stage variable for **values4.yaml** named **qa** and then reference it in **Values YAML** like this: `<+stage.variables.qa>`.
- **Skip Resource Versioning**: By default, Harness versions ConfigMaps and secrets deployed into Kubernetes clusters. In some cases, such as when using public manifests or Helm charts, you cannot add the annotation. When you enable **Skip Resource Versioning**, Harness will not perform versioning of ConfigMaps and secrets for the resource. If you have enabled **Skip Resource Versioning** for a few deployments and then disable it, Harness will start versioning ConfigMaps and secrets.
- **Helm Command Flags**: You can use Helm command flags to extend the Helm commands that Harness runs when deploying your Helm chart. Harness will run Helm-specific Helm commands and their flags as part of preprocessing. All the commands you select are run before `helm install/upgrade`.
- **Command Type**: Select the Helm command type you want to use. For example:
  - [Template](https://v2.helm.sh/docs/helm/#helm-template): `helm template` to render the helm template files.
  - [Pull](https://helm.sh/docs/helm/helm_pull/): `helm pull` (v2) to get the Helm chart.
- **Flag**: Add any options for the command. Ex: `--verify`.

Here's an example:

![](./static/deploy-helm-charts-03.png)

If you haven't set up a Harness delegate, you can add one as part of the connector setup. This process is described in [Helm CD Quickstart](../../onboard-cd/cd-quickstarts/helm-cd-quickstart.md) and [Install a Kubernetes Delegate](../../../platform/2_Delegates/install-delegates/overview.md).

Once your Helm chart is added, it appears in the **Manifests** section. For example:

![](./static/deploy-helm-charts-04.png)
