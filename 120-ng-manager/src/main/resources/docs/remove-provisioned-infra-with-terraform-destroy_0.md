---
title: Remove Infra with the Terraform Destroy step
description: Remove any Terraform provisioned infrastructure.
sidebar_position: 5
helpdocs_topic_id: j75xc704c8
helpdocs_category_id: jcu7twh2t6
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to add a Terraform Destroy step to remove any provisioned infrastructure, just like running the `terraform destroy` command. See [destroy](https://www.terraform.io/docs/commands/destroy.html) from Terraform.

The **Terraform Destroy** step is independent of any other Terraform provisioning steps. It's not restricted to removing the infrastructure deployed in its stage. It can remove any infrastructure you've provisioned using Harness.
