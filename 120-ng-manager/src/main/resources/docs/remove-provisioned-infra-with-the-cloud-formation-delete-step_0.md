---
title: Remove Provisioned Infra with the CloudFormation Delete Step
description: Remove a provisioned stack or any resources created by CloudFormation.
sidebar_position: 5
helpdocs_topic_id: mmzimok6vp
helpdocs_category_id: 31zj6kgnsg
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to use the CloudFormation **Delete Stack** step to remove a stack provisioned using the CloudFormation **Create Stack** step in a Harness Continuous Delivery stage or any resources created by CloudFormation.

You use the CloudFormation **Create Stack** step in a CD stage's **Execution** section as part of the deployment process. The **Create Stack** step runs the CloudFormation template and supporting files that you supply inline or from your repos (Git, AWS S3). Harness provisions the CloudFormation stack defined in the template as part of the stage's **Execution**.

You can remove the stack you provisioned using the **Create Stack** step by using a subsequent **Delete Stack** step in the same stage, or in any Pipeline in the Harness Project. You simply need to ensure that the **Create Stack** and **Delete Stack** steps use the same **Provision Identifier**.

You can also use **Create Stack** and **Delete Stack** in the **Infrastructure** section of a CD stage. You can even map the CloudFormation template outputs to the target infrastructure in **Infrastructure**. During deployment, Harness first provisions the target deployment infrastructure and then the stage's Execution steps deploy to the provisioned infrastructure. For steps on this process, see [Provision Target Deployment Infra Dynamically with CloudFormation](../../cd-infrastructure/cloudformation-infra/provision-target-deployment-infra-dynamically-with-cloud-formation.md).
