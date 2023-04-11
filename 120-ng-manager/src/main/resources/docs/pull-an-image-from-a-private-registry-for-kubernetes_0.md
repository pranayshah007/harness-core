---
title: Pull an Image from a Private Registry for Kubernetes
description: This topic describes how to pull an image from a private registry and use the Docker credentials file.
sidebar_position: 6
helpdocs_topic_id: o1gf8jslsq
helpdocs_category_id: qfj6m1k2c4
helpdocs_is_private: false
helpdocs_is_published: true
---

This topic describes how to pull an image from a private registry and use the Docker credentials file.

Typically, if the Docker image you are deploying is in a private registry, Harness has access to that registry using the credentials set up in the Harness [Connector](/docs/category/connectors).

If some cases, your Kubernetes cluster might not have the permissions needed to access a private Docker registry. For these cases, the values.yaml or manifest file in Service Definition **Manifests** section must use the `dockercfg` parameter.

If the Docker image is added in the Service Definition **Artifacts** section, then you reference it like this: `dockercfg: <+artifact.imagePullSecret>`.

This key will import the credentials from the Docker credentials file in the artifact.

