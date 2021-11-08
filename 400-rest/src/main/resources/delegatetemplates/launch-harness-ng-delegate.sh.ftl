#!/bin/bash -e

docker run -d --restart unless-stopped --memory=${delegateXmx} --cpus=${delegateCpu} --hostname=$(hostname -f | head -c 63) \
-e ACCOUNT_ID=${accountId} \
-e ACCOUNT_SECRET=${accountSecret} \
-e MANAGER_HOST_AND_PORT=${managerHostAndPort} \
-e WATCHER_STORAGE_URL=${watcherStorageUrl} \
-e WATCHER_CHECK_LOCATION=${watcherCheckLocation} \
-e REMOTE_WATCHER_URL_CDN=${remoteWatcherUrlCdn} \
-e DELEGATE_STORAGE_URL=${delegateStorageUrl} \
-e DELEGATE_CHECK_LOCATION=${delegateCheckLocation} \
-e DEPLOY_MODE=${deployMode} \
-e DELEGATE_NAME=${delegateName} \
-e NEXT_GEN=true \
-e DELEGATE_DESCRIPTION=${delegateDescription} \
-e DELEGATE_TYPE=DOCKER \
-e DELEGATE_TAGS=${delegateTags} \
-e DELEGATE_ORG_IDENTIFIER=${delegateOrgIdentifier} \
-e DELEGATE_PROJECT_IDENTIFIER=${delegateProjectIdentifier} \
-e PROXY_HOST= \
-e PROXY_PORT= \
-e PROXY_SCHEME= \
-e PROXY_USER= \
-e PROXY_PASSWORD= \
-e NO_PROXY= \
-e PROXY_MANAGER=true \
-e INIT_SCRIPT= \
-e POLL_FOR_TASKS=false \
-e HELM_DESIRED_VERSION= \
-e USE_CDN=${useCdn} \
-e CDN_URL=${cdnUrl} \
-e JRE_VERSION=${jreVersion} \
-e HELM3_PATH= \
-e HELM_PATH= \
-e KUSTOMIZE_PATH= \
-e KUBECTL_PATH= \
-e ENABLE_CE=${enableCE} \
-e VERSION_CHECK_DISABLED=${versionCheckDisabled}
${delegateDockerImage}
