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
      - DELEGATE_TOKEN=${delegateToken}
<#if isImmutable == "false">
      - MANAGER_HOST_AND_PORT=${managerHostAndPort}
      - WATCHER_STORAGE_URL=${watcherStorageUrl}
      - WATCHER_CHECK_LOCATION=${watcherCheckLocation}
      - DELEGATE_STORAGE_URL=${delegateStorageUrl}
      - DELEGATE_CHECK_LOCATION=${delegateCheckLocation}
      <#if useCdn == "true">
      - CDN_URL=${cdnUrl}
      - REMOTE_WATCHER_URL_CDN=${remoteWatcherUrlCdn}
      </#if>
</#if>
      - DEPLOY_MODE=${deployMode}
      - DELEGATE_NAME=${delegateName}
      - NEXT_GEN=true
      - DELEGATE_DESCRIPTION=${delegateDescription}
      - DELEGATE_TYPE=DOCKER
      - DELEGATE_TAGS=${delegateTags}
      - PROXY_MANAGER=true
      - INIT_SCRIPT=echo "Docker delegate init script executed."
