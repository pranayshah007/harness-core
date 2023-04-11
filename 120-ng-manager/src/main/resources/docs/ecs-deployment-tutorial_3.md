# Set up AWS IAM

Create the Container instance IAM role. If you've used ECS before, you probably already have this set up.

This is usually named **ecsInstanceRole** and it's used in your Task Definitions. For example:  

```yaml
ipcMode:  
executionRoleArn: arn:aws:iam::1234567890:role/ecsInstanceRole  
containerDefinitions:  
...
```

Ensure this role exists. See [Amazon ECS Instance Role](https://docs.aws.amazon.com/batch/latest/userguide/instance_IAM_role.html) from AWS.  

Ensure the role has the `AmazonEC2ContainerServiceforEC2Role` attached.  

Verify that the **Trust Relationship** for the role is configured correctly. For more information, see [How do I troubleshoot the error “ECS was unable to assume the role” when running the Amazon ECS tasks?](https://aws.amazon.com/premiumsupport/knowledge-center/ecs-unable-to-assume-role/) from AWS.  

```json
{  
    "Version": "2012-10-17",  
    "Statement": [  
        {  
            "Sid": "",  
            "Effect": "Allow",  
            "Principal": {  
                "Service": "ecs-tasks.amazonaws.com"  
            },  
            "Action": "sts:AssumeRole"  
        }  
    ]  
}
```

Create a custom managed policy for Harness. This policy allows Harness to perform ECS tasks. You will attach it to the IAM User you use to connect Harness with AWS.  

The custom managed policy should have the following permissions:  

```json
 {  
    "Version": "2012-10-17",  
    "Statement": [  
        {  
            "Effect": "Allow",  
            "Action": [  
                "ecr:DescribeRepositories",  
                "ecs:ListClusters",  
                "ecs:ListServices",  
                "ecs:DescribeServices",  
                "ecr:ListImages",  
		"ecr:DescribeImages",
                "ecs:RegisterTaskDefinition",  
                "ecs:CreateService",  
                "ecs:ListTasks",  
                "ecs:DescribeTasks",  
                "ecs:DeleteService",  
                "ecs:UpdateService",  
                "ecs:DescribeContainerInstances",  
                "ecs:DescribeTaskDefinition",  
                "application-autoscaling:DescribeScalableTargets",  
                "application-autoscaling:DescribeScalingPolicies",  
                "iam:ListRoles",  
                "iam:PassRole"  
            ],  
            "Resource": "*"  
        }  
    ]  
}
```

When you do your own deployments, you might need additional permissions added to this policy. It depends on your Task Definition. Alternately, you can use [AmazonECS\_FullAccess](https://docs.amazonaws.cn/en_us/AmazonECS/latest/developerguide/security-iam-awsmanpol.html).

Create or edit an IAM user and attach the following policies in **Permissions**:

1. The custom managed policy you create above. In this example, we'll name this policy **HarnessECS**.
2. **AmazonEC2ContainerServiceRole** (`arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceRole`)
3. **AmazonEC2ContainerServiceforEC2Role** (`arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role`)  
	  
	When you're done, the IAM User Permissions should look like this:![](./static/ecs-deployment-tutorial-36.png)
4. Generate an access key for the IAM user for connecting Harness to AWS ECS. In the IAM User, click **Security credentials** and then in **Access keys** create and save the key. You will use the Access key ID and Secret access key in the Harness AWS Connector later.

Now that you have all the AWS credentials configured, you can create or select an ECS cluster to use for your deployment.
