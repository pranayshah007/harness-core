## CloudFormation Parameter Files

You can use CloudFormation parameters files to specify input parameters for the stack.

This is the same as using the AWS CloudFormation CLI `create-stack` option `--parameters` and a JSON parameters file:


```
aws cloudformation create-stack --stackname startmyinstance  
--template-body file:///some/local/path/templates/startmyinstance.json  
--parameters https://your-bucket-name.s3.amazonaws.com/params/startmyinstance-parameters.json
```

Where the JSON file contains parameters such as these:

```json
[  
  {  
    "ParameterKey": "KeyPairName",  
    "ParameterValue": "MyKey"  
  },   
  {  
    "ParameterKey": "InstanceType",  
    "ParameterValue": "m1.micro"  
  }  
]
```

1. In **Cloud Formation Parameter Files**, click **Add**.
2. In **Parameter File Connector**, select your Git platform, and the select or add a Git Connector. See [Code Repo Connectors](https://newdocs.helpdocs.io/category/xyexvcc206) for steps on adding a Git Connector.
   
   For AWS S3, see [Add an AWS Connector](../../../platform/7_Connectors/add-aws-connector.md).
3. In **Parameter File Details**, enter the following:

   + **Identifier:** enter an Identifier for the file. This is just a name that indicates what the parameters are for.
   + **Repo Name:** if the Git Connector does not have the repo path, enter it here.
   + **Git Fetch Type:** select **Latest from Branch** or use a Git commit Id or tag.
   + **Parameter File Details:** enter the path to the file from the root of the repo. To add multiple files, click **Add Path File**.

Here's an example:

![](./static/provision-target-deployment-infra-dynamically-with-cloud-formation-03.png)
