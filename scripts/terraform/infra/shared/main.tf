/*
 * Copyright 2019 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

variable "user" {}
variable "zone" {}
variable "access_key" {}
variable "secret_key" {}
variable "region" {}

variable "generic-instances" {}

resource "aws_key_pair" "dev_test" {
  public_key = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDUHGzV0xBIPblUGtNW8y9MXBNqAv432ooCm3F0EFG/EWb0p+9OxkCJu18WBdrvWDLuJ364wM8ZAQe0h5de/ORg+r8w+W1WihaGi5p0bOs6vWFUMcxQbAMZCunLtfJk98cqOMaY1R34HbvMOiflG05lsJtEK96+g+cK9O0i19IUuIX+YT6r///WOnJ/qMs/P++kBEuvZWhkM2/GFT9F0k5JdBWTERArMNQxOyAJNmSHXFz01vWB8O93UF8m/qX7aeHaxaZFLLvaVu6nENxQbkJQ8PhX+Rr125V8YqbKWj1CiiJbkRHSprSUIcZvOKXzFFnBHiCoBy3thTx1rUUQFYaV george@example.com"
}

resource "aws_security_group" "dev_test" {
  description = "dev_test security group allows ssh for everyone"

  ingress {
    from_port = 22
    to_port = 22
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Create an EC2 instance
resource "aws_instance" "test" {
    # AMI ID for Ubuntu Linux
    ami             = "ami-41e0b93b"
    instance_type   = "t2.micro"
    key_name        = aws_key_pair.dev_test.id
    security_groups = [aws_security_group.dev_test.name]

    tags = {
        Purpose     = "test"
        Name        = "workflow"
        User        = var.user
        Zone        = var.zone
        Department  = "Deployment"
        Project     = "Workflow"
        Owner       = var.user
    }

    count = var.generic-instances
}

output "security_group" {
    value = aws_security_group.dev_test.name
}
