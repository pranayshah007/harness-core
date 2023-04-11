### Permissions

Ensure that the AWS IAM user account you use in the AWS Connector has the following policy.

<details>
<summary>Pull from ECR policy</summary>

* **Policy Name:** `AmazonEC2ContainerRegistryReadOnly`
* **Policy ARN:** `arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly`
* **Description:** `Provides read-only access to Amazon EC2 Container Registry repositories.`
* **Policy JSON:**

```
{
  "Version": "2012-10-17",
  "Statement": [
      {
              "Effect": "Allow",
              "Action": [
                  "ecr:GetAuthorizationToken",
                  "ecr:BatchCheckLayerAvailability",
                  "ecr:GetDownloadUrlForLayer",
                  "ecr:GetRepositoryPolicy",
                  "ecr:DescribeRepositories",
                  "ecr:ListImages",
                  "ecr:DescribeImages",
                  "ecr:BatchGetImage"
              ],
              "Resource": "*"
      }
  ]
}
```

</details>

</details>


