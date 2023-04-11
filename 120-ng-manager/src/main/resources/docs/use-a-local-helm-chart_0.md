---
title: Use a local Helm Chart
description: This topic describes how to use a Helm chart installed on the Harness Delegate disk.
sidebar_position: 2
helpdocs_topic_id: j5xrnxl5yz
helpdocs_category_id: xot6u3ge9d
helpdocs_is_private: false
helpdocs_is_published: true
---

Harness supports Helm charts stored in a remote Helm Chart Repository, such as ChartMuseum. In some cases, you might be deploying the same Helm chart and version to many clusters/namespaces in parallel. This can cause many identical downloads and performance issues.

To support this use case, Harness includes the option of using a local chart installed on the Harness Delegate local disk.

Harness will check for the existence of the Helm chart on the local Delegate disk, and then proceed to download from the remote repo only if the chart is not found.

Using a local Helm chart eliminates identical downloads and their related performance issues.

New to Helm deployments in Harness? See [Helm Chart Deployment Tutorial](../../onboard-cd/cd-quickstarts/helm-cd-quickstart.md) and [Native Helm Deployment Tutorial](../../onboard-cd/cd-quickstarts/native-helm-quickstart.md). For extensive details, see [Deploy Helm Charts](deploy-helm-charts.md).
