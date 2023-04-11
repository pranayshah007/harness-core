---
title: Ignore a Manifest File During Deployment
description: Apply manifests separately using the Harness Apply step.
sidebar_position: 5
helpdocs_topic_id: jyv7jbr8pg
helpdocs_category_id: qfj6m1k2c4
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to ignore manifests for the primary deployment and apply them separately using the Apply step.

You might have manifest files for resources that you do not want to deploy as part of the main deployment.

Instead, you can tell Harness to ignore these files and then apply them separately using the Harness [Apply step](../../cd-technical-reference/cd-k8s-ref/kubernetes-apply-step.md). Or you can simply ignore them and deploy them later.
