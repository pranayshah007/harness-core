---
title: Add Inline Service Files Using File Store
description: This topic show you how to use Kubernetes manifests and other configuration files in the Harness File Store that comes with your Harness account.
sidebar_position: 1
helpdocs_topic_id: oaihv6nry9
helpdocs_category_id: ivpp0y9sdf
helpdocs_is_private: false
helpdocs_is_published: true
---

You can use Kubernetes manifests and other configuration files in Git repos or in the Harness File Store. If you use a Git repo, Harness fetches files from the remote repo during deployment runtime.

If you use the Harness File Store, you can store files in Harness and select them in your Services. At deployment runtime, Harness simply uses the files from the Harness File Store.

File Store lets you share files with your team without managing remote repos.

For Kubernetes, the following configuration files are supported for File Store:

* Kubernetes Manifest
* Helm Chart
* OpenShift Template and OpenShift Param
* Kustomize and Kustomize Patches

Other configuration files for other integrations, such as Azure Web Apps, etc, are supported as those integrations are released.You can access File Store from **Project Setup** in Harness or via the **Add Manifest** step during Pipeline creation.

Before You Begin

* [Kubernetes CD Quickstart](../../onboard-cd/cd-quickstarts/kubernetes-cd-quickstart.md)
* [Kubernetes Deployments Overview](../../cd-advanced/cd-kubernetes-category/kubernetes-deployments-overview.md)
* [Add Kubernetes Manifests](../../cd-advanced/cd-kubernetes-category/define-kubernetes-manifests.md)
* [Add Container Images as Artifacts for Kubernetes Deployments](../../cd-advanced/cd-kubernetes-category/add-artifacts-for-kubernetes-deployments.md)
