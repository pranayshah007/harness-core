---
title: Run Kubernetes Jobs
description: This topic describes how to define Kubernetes Jobs in Harness Services add then execute them using the Apply step.
sidebar_position: 6
helpdocs_topic_id: onwcu7pian
helpdocs_category_id: uj8bqz9j0q
helpdocs_is_private: false
helpdocs_is_published: true
---

In this topic, we will show you how to execute a Job in a Harness Kubernetes deployment as part of the main deployment.

Kubernetes [Jobs](https://kubernetes.io/docs/concepts/workloads/controllers/jobs-run-to-completion/) create one or more pods to carry out commands. For example, a calculation or a backup operation.

In a Harness Kubernetes CD stage, you define Jobs in the Service **Manifests**. Next you add the **Apply** step to your Harness Workflow to execute the Job.

Typically, Jobs are not part of the main deployment. You can exclude them from the main deployment and simply call them at any point in the stage using the Apply step. For steps on ignoring the Job as part of the main deployment and executing it separately, see [Deploy Manifests Separately using Apply Step](deploy-manifests-using-apply-step.md).In this topic:
