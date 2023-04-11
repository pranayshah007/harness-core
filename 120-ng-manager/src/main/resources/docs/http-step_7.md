# Headers

Enter the media type for the message. For example, if you are using the GET method, the headers are used to specify the GET response body message type.

In **Key**, enter `Token`

In **Value**, enter `<+secrets.getValue("aws-playground_AWS_secret_key")>`

Another method:

* **Key**: `variable:`
* **Value**: `var1,var2:var3`

You can copy the key and paste it in the HTTP step **Header** setting. For more information, go to [Add and Manage API Keys](https://developer.harness.io/docs/platform/role-based-access-control/add-and-manage-api-keys/).
