---
title: Provision Target Deployment Infra Dynamically with CloudFormation
description: Provision a CD stage's target deployment infra using CloudFormation.
sidebar_position: 1
helpdocs_topic_id: 6jfl7i6a5u
helpdocs_category_id: mlqlmg0tww
helpdocs_is_private: false
helpdocs_is_published: true
---

:::info

Dynamic provisioning is only supported in [Service and Environments v1](../../onboard-cd/upgrading/upgrade-cd-v2). Dynamic provisioning will be added to Service and Environments v2 soon. Until then, you can create a stage to provision the target infrastructure and then a subsequent stage to deploy to that provisioned infrastructure.

:::

This topic describes how to provision a CD stage's deployment infrastructure resources using the CloudFormation **Create Stack**, **Delete Stack**, and **Rollback Stack** steps.

You can use these steps in a CD stage's **Execution** section as part of the deployment process, but this topic describes how to use them in the **Infrastructure** section to provision resources before deployment.

You use the CloudFormation **Create Stack** step to run a CloudFormation template and supporting files inline or from your repos (Git, AWS S3).

When you use **Create Stack** in **Infrastructure**, you also have the option to map the CloudFormation outputs and target the provisioned infrastructure. During deployment, Harness first provisions the target deployment infrastructure and then the stage's Execution steps deploy to the provisioned infrastructure.

To provision non-target infrastructure resources, add the CloudFormation Create Stack step to the stage **Execution** section instead of the **Infrastructure** section.### Before You Begin

* [CloudFormation Provisioning with Harness](../../cd-advanced/cloudformation-howto/cloud-formation-provisioning-with-harness.md)
