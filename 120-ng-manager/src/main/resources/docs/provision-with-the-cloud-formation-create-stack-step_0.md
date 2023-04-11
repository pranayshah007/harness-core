---
title: Provision with the CloudFormation Create Stack Step
description: Provision using the CloudFormation Create Stack step.
sidebar_position: 3
helpdocs_topic_id: pq58d0llyx
helpdocs_category_id: 31zj6kgnsg
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to provision resources in a CD stage's deployment infrastructure using the CloudFormation **Create Stack** step.

You use the CloudFormation **Create Stack** step in a CD stage's **Execution** section as part of the deployment process. The **Create Stack** step runs the CloudFormation template and supporting files that you supply inline or from your repos (Git, AWS S3). Harness provisions the CloudFormation stack defined in the template as part of the stage's **Execution**.

You can also use **Create Stack** in the **Infrastructure** section of a CD stage. You can even map the CloudFormation template outputs to the target infrastructure in **Infrastructure**. 

During deployment, Harness first provisions the target deployment infrastructure and then the stage's Execution steps deploy to the provisioned infrastructure. 

For steps on this process, see [Provision Target Deployment Infra Dynamically with CloudFormation](../../cd-infrastructure/cloudformation-infra/provision-target-deployment-infra-dynamically-with-cloud-formation.md).
