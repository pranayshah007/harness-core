---
title: Add Container Images as Artifacts for Kubernetes Deployments
description: Add container images for your Kubernetes deployments.
sidebar_position: 3
helpdocs_topic_id: 4ifq51cp0i
helpdocs_category_id: qfj6m1k2c4
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic covers adding container image locations as Harness artifacts and referencing them in your Values files.

If a public Docker image location is hardcoded in your Kubernetes manifest (for example, `image: nginx:1.14.2`), then you can simply add the manifest to Harness and the Harness Delegate will pull the image during deployment.

Alternatively, you can add the image location to Harness as an artifact in the **Service Definition**. This allows you to reference the image in your manifests and elsewhere using the Harness expression `<+artifact.image>`.

When you deploy, Harness connects to your repo and you select which image and version/tag to deploy.

With a Harness artifact, you can template your manifests, detaching them from a hardcoded location. This makes your manifests reusable and dynamic.
