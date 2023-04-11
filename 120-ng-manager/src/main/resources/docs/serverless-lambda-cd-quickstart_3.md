# Before you begin

Review [Harness Key Concepts](../../../first-gen/starthere-firstgen/harness-key-concepts.md) to establish a general understanding of Harness.* **GitHub account:** this quickstart uses a publicly available serverless.yaml file, but GitHub requires that you use a GitHub account for fetching files.
* **Harness Delegate with Serverless installed:** the Harness Delegate is a worker process that performs all deployment tasks. For this quickstart, we'll install a Kubernetes delegate in your own cluster.
	+ You can use a cluster hosted on a cloud platform or run one in minikube using Docker Desktop locally. The installation steps are the same.
	+ The Delegate pod(s) must have Serverless installed. We'll add the Serverless installation script using the delegate environment variable `INIT_SCRIPT` to the delegate YAML file later in this quickstart.
* **AWS User account with required policy:** Serverless deployments require an AWS User with specific AWS permissions, as described in [AWS Credentials](https://www.serverless.com/framework/docs/providers/aws/guide/credentials) from Serverless.com. To create the AWS User, do the following:
	+ Log into your AWS account and go to the Identity & Access Management (IAM) page.
	+ Click **Users**, and then **Add user**. Enter a name. Enable **Programmatic access** by clicking the checkbox. Click **Next** to go to the **Permissions** page. Do one of the following:
		- **Full Admin Access:** click on **Attach existing policies directly**. Search for and select **AdministratorAccess** then click **Next: Review**. Check to make sure everything looks good and click **Create user**.
		- **Limited Access:** click on **Create policy**. Select the **JSON** tab, and add the JSON using the following code from the [Serverless gist](https://gist.github.com/ServerlessBot/7618156b8671840a539f405dea2704c8): IAMCredentials.json
	
	```json
	{  
	    "Statement": [  
	        {  
	            "Action": [  
	                "apigateway:*",  
	                "cloudformation:CancelUpdateStack",  
	                "cloudformation:ContinueUpdateRollback",  
	                "cloudformation:CreateChangeSet",  
	                "cloudformation:CreateStack",  
	                "cloudformation:CreateUploadBucket",  
	                "cloudformation:DeleteStack",  
	                "cloudformation:Describe*",  
	                "cloudformation:EstimateTemplateCost",  
	                "cloudformation:ExecuteChangeSet",  
	                "cloudformation:Get*",  
	                "cloudformation:List*",  
	                "cloudformation:UpdateStack",  
	                "cloudformation:UpdateTerminationProtection",  
	                "cloudformation:ValidateTemplate",  
	                "dynamodb:CreateTable",  
	                "dynamodb:DeleteTable",  
	                "dynamodb:DescribeTable",  
	                "dynamodb:DescribeTimeToLive",  
	                "dynamodb:UpdateTimeToLive",  
	                "ec2:AttachInternetGateway",  
	                "ec2:AuthorizeSecurityGroupIngress",  
	                "ec2:CreateInternetGateway",  
	                "ec2:CreateNetworkAcl",  
	                "ec2:CreateNetworkAclEntry",  
	                "ec2:CreateRouteTable",  
	                "ec2:CreateSecurityGroup",  
	                "ec2:CreateSubnet",  
	                "ec2:CreateTags",  
	                "ec2:CreateVpc",  
	                "ec2:DeleteInternetGateway",  
	                "ec2:DeleteNetworkAcl",  
	                "ec2:DeleteNetworkAclEntry",  
	                "ec2:DeleteRouteTable",  
	                "ec2:DeleteSecurityGroup",  
	                "ec2:DeleteSubnet",  
	                "ec2:DeleteVpc",  
	                "ec2:Describe*",  
	                "ec2:DetachInternetGateway",  
	                "ec2:ModifyVpcAttribute",  
	                "events:DeleteRule",  
	                "events:DescribeRule",  
	                "events:ListRuleNamesByTarget",  
	                "events:ListRules",  
	                "events:ListTargetsByRule",  
	                "events:PutRule",  
	                "events:PutTargets",  
	                "events:RemoveTargets",  
	                "iam:AttachRolePolicy",  
	                "iam:CreateRole",  
	                "iam:DeleteRole",  
	                "iam:DeleteRolePolicy",  
	                "iam:DetachRolePolicy",  
	                "iam:GetRole",  
	                "iam:PassRole",  
	                "iam:PutRolePolicy",  
	                "iot:CreateTopicRule",  
	                "iot:DeleteTopicRule",  
	                "iot:DisableTopicRule",  
	                "iot:EnableTopicRule",  
	                "iot:ReplaceTopicRule",  
	                "kinesis:CreateStream",  
	                "kinesis:DeleteStream",  
	                "kinesis:DescribeStream",  
	                "lambda:*",  
	                "logs:CreateLogGroup",  
	                "logs:DeleteLogGroup",  
	                "logs:DescribeLogGroups",  
	                "logs:DescribeLogStreams",  
	                "logs:FilterLogEvents",  
	                "logs:GetLogEvents",  
	                "logs:PutSubscriptionFilter",  
	                "s3:GetBucketLocation",  
	                "s3:CreateBucket",  
	                "s3:DeleteBucket",  
	                "s3:DeleteBucketPolicy",  
	                "s3:DeleteObject",  
	                "s3:DeleteObjectVersion",  
	                "s3:GetObject",  
	                "s3:GetObjectVersion",  
	                "s3:ListAllMyBuckets",  
	                "s3:ListBucket",  
	                "s3:PutBucketNotification",  
	                "s3:PutBucketPolicy",  
	                "s3:PutBucketTagging",  
	                "s3:PutBucketWebsite",  
	                "s3:PutEncryptionConfiguration",  
	                "s3:PutObject",  
	                "sns:CreateTopic",  
	                "sns:DeleteTopic",  
	                "sns:GetSubscriptionAttributes",  
	                "sns:GetTopicAttributes",  
	                "sns:ListSubscriptions",  
	                "sns:ListSubscriptionsByTopic",  
	                "sns:ListTopics",  
	                "sns:SetSubscriptionAttributes",  
	                "sns:SetTopicAttributes",  
	                "sns:Subscribe",  
	                "sns:Unsubscribe",  
	                "states:CreateStateMachine",  
	                "states:DeleteStateMachine"  
	            ],  
	            "Effect": "Allow",  
	            "Resource": "*"  
	        }  
	    ],  
	    "Version": "2012-10-17"  
	}
	```
	The `s3:GetBucketLocation` action is required for a custom S3 bucket only.
	- View and copy the API Key and Secret to a temporary place. You'll need them when setting up the Harness AWS Connector later in this quickstart.
