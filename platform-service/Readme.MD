# Platform Service

## Overview

Platform Service is an umbrella microservice for 3 modules -
1. Notifications
2. Auditing
3. Resource Group Management

## Local Development with Platform Service

Common setup instructions are present in the root [README.md](https://github.com/harness/harness-core/blob/develop/README.md) of the repository.


## Building, Testing and Releasing Platform Service

Platform service has to be managed in different environments (PR, Pre-QA, QA, Prod, On-Prem). Listed below are the Build and Release Pipelines:

1. PR 
    1. [Feature Build](https://app.harness.io/ng/#/account/vpCkHKsDSxK9_KYfjCTMKA/ci/orgs/default/projects/FEATUREBUILDS/pipelines/PlatformServiceFeatureBuild/pipeline-studio/)
2. Pre-QA
    1. [Build](https://app.harness.io/ng/#/account/vpCkHKsDSxK9_KYfjCTMKA/ci/orgs/default/projects/RELEASEMANAGEMENT/pipelines/PlatformServiceDevelopBuild/pipeline-studio/)
    2. [Deployment](https://app.harness.io/#/account/wFHXHD0RRQWoO8tIZT5YVw/app/-jRbnwPZRoOLj2NEhrbJnQ/pipelines/qcL3cAvPSQe1_nIR_TTCPA/edit)
3. QA
    1. [Release Branch Cut](https://stage.harness.io/ng/#/account/wFHXHD0RRQWoO8tIZT5YVw/ci/orgs/Harness/projects/RELEASEBUILDS/pipelines/CutPlatformServiceReleaseBranch/pipeline-studio/)
    2. [Build](https://stage.harness.io/ng/#/account/wFHXHD0RRQWoO8tIZT5YVw/ci/orgs/Harness/projects/RELEASEBUILDS/pipelines/PlatformServiceSaaSReleaseBuild/pipeline-studio/)
    3. [Deployment](https://stage.harness.io/ng/#/account/wFHXHD0RRQWoO8tIZT5YVw/cd/orgs/Harness/projects/Operations/pipelines/Platform_Service/pipeline-studio/)
4. SAAS
    1. [Deployment](https://stage.harness.io/ng/#/account/wFHXHD0RRQWoO8tIZT5YVw/cd/orgs/Harness/projects/Operations/pipelines/Platform_Service/pipeline-studio/)
5. On-Prem
    1. [Build](https://stage.harness.io/ng/#/account/wFHXHD0RRQWoO8tIZT5YVw/ci/orgs/Harness/projects/RELEASEBUILDS/pipelines/PlatformServiceOnPremReleaseBuild/pipeline-studio/)

    
    