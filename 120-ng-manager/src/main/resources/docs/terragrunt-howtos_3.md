# Important: Install Terraform and Terragrunt on delegates

Terraform and Terragrunt must be installed on the Harness delegates used for deployment. You can install Terraform and Terragrunt manually or use the `INIT_SCRIPT` environment variable in the Delegate YAML.

For steps on using the `INIT_SCRIPT` environment variable, see [Build custom delegate images with third-party tools](/docs/platform/2_Delegates/install-delegates/build-custom-delegate-images-with-third-party-tools.md).

Here's an example of the installation script to install Terraform and Terragrunt:

```bash