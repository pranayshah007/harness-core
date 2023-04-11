# AWS policy requirements

When you set up a Harness AWS connector to connect Harness with your AWS account, the AWS IAM role must have the following policies.

<details>
<summary>AmazonEC2FullAccess</summary>

- **Policy Name:** [AmazonEC2FullAccess](https://us-east-1.console.aws.amazon.com/iam/home#/policies/arn:aws:iam::aws:policy/AmazonEC2FullAccess).
- **Policy ARN:** arn:aws:iam::aws:policy/AmazonEC2FullAccess.
- **Description:** Provides full access to Amazon EC2 via the AWS Management Console.

Policy JSON:

```json
{  
  "Version": "2012-10-17",  
  "Statement": [  
    {  
      "Action": "ec2:*",  
      "Effect": "Allow",  
      "Resource": "*"  
    },  
    {  
      "Effect": "Allow",  
      "Action": "elasticloadbalancing:*",  
      "Resource": "*"  
    },  
    {  
      "Effect": "Allow",  
      "Action": "cloudwatch:*",  
      "Resource": "*"  
    },  
    {  
      "Effect": "Allow",  
      "Action": "autoscaling:*",  
      "Resource": "*"  
    }  
  ]  
}  
```
</details>

<details>
<summary>DescribeRegions</summary>

:::note

The [DescribeRegions](https://docs.aws.amazon.com/AWSEC2/latest/APIReference/API_DescribeRegions.html) action is required for all AWS IAM roles used in Harness AWS connectors regardless of what AWS service you are using.

:::

Harness needs a policy with the `DescribeRegions` action so that it can list the available regions for you when you define your target architecture.

Create a [Customer Managed Policy](https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_managed-vs-inline.html#customer-managed-policies), add the `DescribeRegions` action to list those regions, and add that to any role used by the Cloud Provider.

```json
{  
    "Version": "2012-10-17",  
    "Statement": [  
        {  
            "Sid": "VisualEditor0",  
            "Effect": "Allow",  
            "Action": "ec2:DescribeRegions",  
            "Resource": "*"  
        }  
    ]  
}
```

</details>

