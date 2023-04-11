---
title: Map Argo projects to Harness GitOps Projects
description: This topic describes how to manage multiple Argo CD projects within one Harness Project.
sidebar_position: 6
helpdocs_topic_id: gzw782fcqz
helpdocs_category_id: 013h04sxex
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to add and manage multiple Argo CD projects within one Harness Project.

When you install a Harness GitOps Agent, Harness can import your existing Argo CD entities into Harness GitOps. We call this Bring Your Own Argo CD (BYOA).

With a non-BYOA setup, Harness installs Argo CD for you when you install a Harness GitOps Agent. For more information, go to [Install a Harness GitOps Agent](install-a-harness-git-ops-agent.md).

In addition, when you install the Harness GitOps Agent in your existing Argo CD cluster, you can map Argo CD projects to Harness Projects. Harness will import all the Argo CD project entities (applications, clusters, repos, etc) and create them in Harness automatically.

Also, whenever new entities are created in mapped Argo CD projects, they are added to Harness automatically.
