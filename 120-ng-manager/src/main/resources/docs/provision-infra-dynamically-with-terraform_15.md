## Configuration File Repository

**Configuration File Repository** is where the Terraform script and files you want to use are located.

Here, you'll add a connection to the Terraform script repo.

1. Click **Specify Config File** or edit icon.
   
   The **Terraform Config File Store** settings appear.
2. Click the provider where your files are hosted.
   
   ![](./static/provision-infra-dynamically-with-terraform-02.png)
3. Select or create a Connector for your repo. For steps, see [Connect to a Git Repo](../../../platform/7_Connectors/connect-to-code-repo.md) or [Artifactory Connector Settings Reference](../../../platform/7_Connectors/ref-cloud-providers/artifactory-connector-settings-reference.md) (see **Artifactory with Terraform Scripts and Variable Definitions (.tfvars) Files**).

If you're simply experimenting, you can use [HashiCorp's Kubernetes repo](https://github.com/hashicorp/terraform-provider-kubernetes/tree/main/_examples/gke).
