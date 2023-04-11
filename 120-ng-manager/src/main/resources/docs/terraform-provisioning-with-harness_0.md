---
title: Terraform Provisioning with Harness
description: Use Terraform as part of your deployment process.
sidebar_position: 2
helpdocs_topic_id: boug6e884h
helpdocs_category_id: jcu7twh2t6
helpdocs_is_private: false
helpdocs_is_published: true
---

:::info

Dynamic provisioning is only supported in [Service and Environments v1](https://developer.harness.io/docs/continuous-delivery/onboard-cd/upgrading/upgrade-cd-v2). Dynamic provisioning will be added to Service and Environments v2 soon. Until then, you can create a stage to provision the target infrastructure and then a subsequent stage to deploy to that provisioned infrastructure.

:::

This topic describes how to use Terraform to provision infrastructure as part of your deployment process.

Harness can provision any resource that is supported by a Terraform [provider or plugin](https://www.terraform.io/docs/configuration/providers.html).

:::note

Looking for how-tos? See [Terraform how-tos](terraform-how-tos.md).

:::
