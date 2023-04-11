## Template File

You can add your template in the following ways:

* **Inline:** just enter the template in **Template File**. You can use CloudFormation-compliant JSON or YAML.
* **AWS S3:** enter the URL of the S3 bucket containing the template file. This can be a public or private URL. If you use a private URL, the AWS credentials in the **AWS Connector** setting are used for authentication. Ensure that the credentials include the **AmazonS3ReadOnlyAccess** policy and the `ec2:DescribeRegions` policy described in [AWS Connector](../../../platform/7_Connectors/ref-cloud-providers/aws-connector-settings-reference.md).
* **Remote:** select a Git repo where you template is located. You'll add or select a Harness Git Connector for the repo. See [Code Repo Connectors](https://newdocs.helpdocs.io/category/xyexvcc206).
