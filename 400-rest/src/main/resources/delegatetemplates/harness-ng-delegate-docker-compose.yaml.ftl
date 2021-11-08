version: "3.7"
services:
  harness-ng-delegate:
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: "${delegateCpu}"
          memory: ${delegateXmx}M
    image: ${delegateDockerImage}
    environment:
      - ACCOUNT_ID=${accountId}
      - ACCOUNT_SECRET=${accountSecret}
<#if isImmutable == "false">
      - MANAGER_HOST_AND_PORT=${managerHostAndPort}
      - WATCHER_STORAGE_URL=${watcherStorageUrl}
      - WATCHER_CHECK_LOCATION=${watcherCheckLocation}
      - REMOTE_WATCHER_URL_CDN=${remoteWatcherUrlCdn}
      - DELEGATE_STORAGE_URL=${delegateStorageUrl}
      - DELEGATE_CHECK_LOCATION=${delegateCheckLocation}
      - USE_CDN=${useCdn}
      - CDN_URL=${cdnUrl}
</#if>
      - DEPLOY_MODE=${deployMode}
      - DELEGATE_NAME=${delegateName}
      - NEXT_GEN=true
      - DELEGATE_DESCRIPTION=${delegateDescription}
      - DELEGATE_TYPE=DOCKER
      - DELEGATE_TAGS=${delegateTags}
      - DELEGATE_ORG_IDENTIFIER=${delegateOrgIdentifier}
      - DELEGATE_PROJECT_IDENTIFIER=${delegateProjectIdentifier}
      - PROXY_MANAGER=true
      - VERSION_CHECK_DISABLED=${versionCheckDisabled}
      - INIT_SCRIPT=echo "Docker delegate init script executed."
