## Environment Variables

In **Environment Variables**, you can reference additional environment variables in the Terraform script ultimately used by the Terragrunt plan. These are in addition to any variables already in the script.

* Click **Add** and enter a name and value for the environment variable.
    
    For example, the name `TF_LOG` and the value `TRACE`, or the name `ARM_CLIENT_ID` and the value `<+secrets.getValue("account.tg_azure_client_id")>`.

You can use Harness variables and secrets for the name and value.

Environment variables can also be deleted using the Terragrunt Destroy step.
