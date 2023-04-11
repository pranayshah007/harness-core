---
title: Provision Target Deployment Infra Dynamically with Terraform
description: This topic show you how to dynamically provision the target deployment infrastructure at runtime using the Terraform Plan and Apply steps.
sidebar_position: 1
helpdocs_topic_id: uznls2lvod
helpdocs_category_id: y5cc950ks3
helpdocs_is_private: false
helpdocs_is_published: true
---

:::info

Dynamic provisioning is only supported in [Service and Environments v1](../../onboard-cd/upgrading/upgrade-cd-v2). Dynamic provisioning will be added to Service and Environments v2 soon. Until then, you can create a stage to provision the target infrastructure and then a subsequent stage to deploy to that provisioned infrastructure.

:::

This topic describes how to provision a CD stage's target deployment infrastructure using the **Terraform Plan** and **Apply** steps.

You use the Terraform steps to run the Terraform script and supporting files from your repo. Harness uses the files to create the infrastructure that your Pipeline will deploy to.

Next, you map the script outputs Harness requires to target the provisioned infrastructure, such as namespace.

During deployment, Harness provisions the target deployment infrastructure and then the stage's Execution steps deploy to the provisioned infrastructure.

To provision non-target infrastructure, add the Terraform Plan and Apply steps to the stage **Execution** section instead of the **Infrastructure** section.
