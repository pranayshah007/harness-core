---
title: Deploy manifests separately using Apply step
description: Deploy secondary workloads separately.
sidebar_position: 4
helpdocs_topic_id: 00el61pzok
helpdocs_category_id: uj8bqz9j0q
helpdocs_is_private: false
helpdocs_is_published: true
---

By default, the Harness Kubernetes Rolling, Canary, and Blue Green steps will deploy all of the resources you have set up in the Service Definition **Manifests** section.

In some cases, you might have resources in **Manifests** that you do not want to deploy as part of the main deployment, but want to apply as another step in the stage.

For example, you might want to deploy an additional resource only after Harness has verified the deployment of the main resources in the **Manifests** section.

CD stages include an **Apply** step that allows you to deploy any resource you have set up in the **Manifests** section.
