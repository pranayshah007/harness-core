---
title: Add a Custom Artifact Source for CD
description: This topic show you how to use a Harness Custom Artifact to fetch a JSON payload of the artifacts from your repo and then reference the artifact version to use in your deployment.
sidebar_position: 3
helpdocs_topic_id: hnqkhh7gut
helpdocs_category_id: ivpp0y9sdf
helpdocs_is_private: false
helpdocs_is_published: true
---

:::note

Currently, this feature is behind the feature flag `CUSTOM_ARTIFACT_NG`. Contact [Harness Support](mailto:support@harness.io) to enable the feature.Harness includes artifact sources for the most common repositories, such as GCR, ECR, Nexus, Artifactory, and any Docker registry such as Docker Hub.

:::

For cases where you are using a custom artifact repo, you can use the Custom Artifact repository type. The Custom Artifact uses a shell script to fetch a JSON payload of the artifacts from your repo, and then you can reference the artifact version to use in your deployment. You can also reference any metadata in the payload.

This topic described how to use the Custom Artifact repository type in your Harness Service and how to reference its artifact information from the JSON payload.
