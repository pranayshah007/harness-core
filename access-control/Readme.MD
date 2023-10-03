# Access Control

## Overview
Codeowner
Access Control is built to provide intra-account fine grained Access Control for multiple principals (User, User Groups and Service Accounts) on multiple resources (Pipelines, Secrets, etc). It has been designed based on Role Based Access Control (RBAC) while keeping in mind the hierarchical structure of Harness NG. The model is explained in detail here - [Access Control Model](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/1291649236/NG+Access+Control+Model).

Access Control Service is a separate microservice in Harness. The service boundaries for Access Control are explained here - [Access Control Service Boundaries](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/21011857501/Access+Control+Service+Boundary#Access-Control-Service-Boundary). As a microservice, access control is dependent on other services as well as some common libraries. The dependencies are mentioned here - [Access Control Dependencies](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/21017133469/Access+Control+Dependencies). A detailed technical specification on the broad internals of the service can be found here - [Access Control Technical Specification](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/21060943873/NG+Access+Control+Technical+Specification).

## Local Development with Access Control

Common setup instructions are present in the root [README.md](https://github.com/harness/harness-core/blob/develop/README.md) of the repository.
Once you have successfully completed the above steps, please follow the steps mentioned in this document - [Local Development with Access Control](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/1588036900/Local+development+with+Access+Control).

## Onboarding new Resources and Permissions

Please follow the following guide to 
 - [Onboard new permissions and managed roles](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/21060812826/NG+Access+Control+-+Developer+Guide)
 - [Onboard new Resources in Resource Group](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/21060943873/NG+Access+Control+Technical+Specification)

The proces to maintain permissions is explained here - [Permissions Management](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/21060812887/Permissions+Management)

## Building, Testing and Releasing Access Control

The details about building, testing and releasing Access Control is mentioned here - [Building, Testing and Releasing Access Control](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/21060911193/Building%2C+Testing+and+Releasing+Access+Control).

## Incident and Operations Playbook

The details about different dashboards and alerts are mentioned here - [Access Control Ops Playbook](https://harness.atlassian.net/wiki/spaces/PLATFORM/pages/1822359624/Access+Control+Ops+Playbook)
