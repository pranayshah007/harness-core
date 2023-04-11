## AWS Connector

1. Add or select the Harness [AWS Connector](../../../platform/7_Connectors/ref-cloud-providers/aws-connector-settings-reference.md) that will be used for this step. The AWS Connector will include the credentials needed to perform the provisioning.

The credentials required for provisioning depend on what you are provisioning.

For example, if you wanted to give full access to create and manage EKS clusters, you could use a policy like this:


```json
{  
     "Version": "2012-10-17",  
     "Statement": [  
         {  
             "Effect": "Allow",  
             "Action": [  
                 "autoscaling:*",  
                 "cloudformation:*",  
                 "ec2:*",  
                 "eks:*",  
                 "iam:*",  
                 "ssm:*"  
             ],  
             "Resource": "*"  
         }  
     ]  
 }
```
Ensure that the credentials include the `ec2:DescribeRegions` policy described in [AWS Connector](../../../platform/7_Connectors/ref-cloud-providers/aws-connector-settings-reference.md).

See [AWS CloudFormation service role](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-iam-servicerole.html) from AWS.
