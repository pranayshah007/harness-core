---
title: Secure Shell (SSH) deployment tutorial
description: Deploy to any platform using SSH.
sidebar_position: 8
helpdocs_topic_id: mpx2y48ovx
helpdocs_category_id: c9j6jejsws
helpdocs_is_private: false
helpdocs_is_published: true
---

You can use Secure Shell (SSH) to deploy your artifacts to hosts located in Microsoft Azure, AWS, or any platform-agnostic Physical Data Center (PDC).

This deployment is called Traditional because it uses Secure Shell scripts and a traditional runtime environment as opposed to containers and orchestration mechanisms, such as those in the Kubernetes Tutorial.

This tutorial shows you how to run a SSH deployment in Harness by setting up a Secure Shell Service and deploying artifacts from Artifactory to a target host in AWS. You will use a Canary deployment strategy.

For Secure Shell, you can access artifacts from **Jenkins**, **Artifactory**, or **Custom**. If you select **Custom**, you will need to provide a Bash script.

The **Execution Strategies** supported for Secure Shell include **Blank Canvas**, **Basic**, **Rolling**, and **Canary**.

The supported artifact package types include JAR, TAR, WAR, RPM and ZIP.

[Harness File Store](../../cd-services/cd-services-general/add-inline-manifests-using-file-store.md) should be enabled if you want to upload Config files from the file store.
