#!/bin/bash -e
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

sudo docker pull ${delegateDockerImage}

sudo docker run -d --restart unless-stopped --hostname="$(hostname -f | head -c 63)" \
-e ACCOUNT_ID=${accountId} \
-e DELEGATE_TOKEN=${delegateToken} \
-e MANAGER_HOST_AND_PORT=${managerHostAndPort} \
-e WATCHER_STORAGE_URL=${watcherStorageUrl} \
-e WATCHER_CHECK_LOCATION=${watcherCheckLocation} \
-e DELEGATE_STORAGE_URL=${delegateStorageUrl} \
-e DELEGATE_CHECK_LOCATION=${delegateCheckLocation} \
-e DELEGATE_NAME= \
-e DELEGATE_PROFILE=${delegateProfile} \
-e DELEGATE_TYPE=${delegateType} \
-e DEPLOY_MODE=${deployMode} \
-e PROXY_HOST= \
-e PROXY_PORT= \
-e PROXY_SCHEME= \
-e PROXY_USER= \
-e PROXY_PASSWORD= \
-e NO_PROXY= \
-e PROXY_MANAGER=true \
-e POLL_FOR_TASKS=false \
-e HELM_DESIRED_VERSION= \
-e CF_PLUGIN_HOME= \
<#if useCdn == "true">
-e CDN_URL=${cdnUrl} \
-e REMOTE_WATCHER_URL_CDN=${remoteWatcherUrlCdn} \
</#if>
-e JRE_VERSION=${jreVersion} \
${delegateDockerImage}
