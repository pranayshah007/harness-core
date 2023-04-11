# Before You Begin

Review [Harness Key Concepts](../../../first-gen/starthere-firstgen/harness-key-concepts.md) to establish a general understanding of Harness.Make sure you have the following set up before you begin this quickstart:

* **GitHub account:** this quickstart uses publicly available manifests and values YAML files, but GitHub requires that you use a GitHub account for fetching files.
* **Azure ACR and AKS Permissions:** make sure you have a Service Principal or Managed Identity you can use to connect Harness to your Azure App registration, and that it has the required permissions:
	+ **ACR:** the **Reader** role must be assigned.
	+ **AKS:** the **Owner** role must be assigned.
	+ For a custom role, see the permissions in [Add a Microsoft Azure Cloud Connector](../../../platform/7_Connectors/add-a-microsoft-azure-connector.md).

* **AKS Cluster:** you'll need a target AKS cluster for the deployment. Ensure your cluster meets the following requirements:
  * **Number of nodes:** 2.
  * **vCPUs, Memory, Disk Size:** 4vCPUs, 16GB memory, 100GB disk. In AKS, the **Standard DS2 v2** machine type is enough for this quickstart.
  * **Networking:** outbound HTTPS for the Harness connection to **app.harness.io**, **github.com**, and **hub.docker.com**. Allow TCP port 22 for SSH.
