---
title: Using Manual Harness Approval Steps in CD Stages
description: This topic describes how to enable Harness User Group(s) to approve or reject a stage at any point in its execution.
sidebar_position: 1
helpdocs_topic_id: 43pzzhrcbv
helpdocs_category_id: bz4zh3b75p
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to enable Harness User Group(s) to approve or reject a stage at any point in its execution.

During deployment, the User Group members use the Harness Manager to approve or reject the deployment manually.

Approvals are usually added in between stage steps to prevent the stage execution from proceeding without an approval.

For example, in a [Kubernetes Blue Green Deployment](../../cd-execution/kubernetes-executions/create-a-kubernetes-blue-green-deployment.md), you might want to add an approval step between the Stage Deployment step, where the new app version is deployed to the staging environment, and the Swap Primary with Stage step, where production traffic is routed to the pods for the new version.

Other approval methods are:

* [Harness Approval Stages](../../../platform/9_Approvals/adding-harness-approval-stages.md): add Approval stages for manual intervention.
* [Adding Jira Approval Stages and Steps](../../../platform/9_Approvals/adding-jira-approval-stages.md): add Jira Approval stages and steps.
* [Adding ServiceNow Approval Steps and Stages](../../../platform/9_Approvals/service-now-approvals.md) for ServiceNow Approval stages and steps.
