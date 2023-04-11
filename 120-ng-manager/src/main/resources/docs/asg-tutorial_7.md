### Artifact AMI overrides config files AMIs

You can specify an AMI in the launch template used in the Harness service, but it will be overridden by the AMI specified in the Harness service **Artifacts** section.

The AMI specified in the Harness service **Artifacts** section is the same as selecting an AMI in the **Create launch template** wizard in the AWS console or `ImageId` in in the [create-launch-template](https://docs.aws.amazon.com/cli/latest/reference/ec2/create-launch-template.html) CLI command.  
