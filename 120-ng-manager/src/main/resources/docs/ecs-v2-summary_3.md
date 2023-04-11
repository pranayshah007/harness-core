# ECS basics

Official AWS ECS docs explain ECS concepts in detail, but let's review a few important points:

- An ECS Task is the smallest deployable entity in ECS.
  - A Task Definition is the configuration for a task. It contains task information such as the container definition, image, etc.

- An ECS service is an entity that manages a group of the same tasks.
  - An ECS service generally contains information about load balancing, task count, task placement strategies across availability zones, etc.

- ECS deeply integrates with many other AWS native services such as AWS ECR, App Mesh, Cloud Formation, etc.
