---
title: WinRM deployment tutorial
description: Deploy to any platform using WinRM.
sidebar_position: 9
helpdocs_topic_id: l8795ji7u3
helpdocs_category_id: c9j6jejsws
helpdocs_is_private: false
helpdocs_is_published: true
---


You can use Windows Remote Management (WinRM) to deploy your artifacts to hosts located in Microsoft Azure, AWS, or any platform-agnostic Physical Data Center (PDC). Harness connects to your target Windows instances using the WinRM protocol and executes PowerShell commands to deploy your artifact.

In this tutorial, we will deploy a .zip file to an AWS EC2 Windows instance using Harness. We will pull a publicly-available .zip file from Artifactory and deploy it to an EC2 Windows instance in your AWS account by using the Basic execution strategy.

Harness connects to your target Windows instances using the WinRM protocol and executes PowerShell commands to deploy your artifact.

For WinRM, you can access artifacts from **Jenkins**, **Artifactory**, **Custom**, **Nexus** and **Amazon S3**.

The **Execution Strategies** supported for WinRM include **Blank Canvas**, **Basic**, **Rolling**, and **Canary**.

Supported security protocols for WinRM include Kerberos and Windows New Technology LAN Manager (NTLM).

[Harness File Store](../../cd-services/cd-services-general/add-inline-manifests-using-file-store.md) should be enabled if you want to upload Config files from the file store.
