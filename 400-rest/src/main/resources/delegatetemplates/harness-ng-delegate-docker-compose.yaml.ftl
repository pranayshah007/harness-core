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
      - MANAGER_HOST_AND_PORT=${managerHostAndPort}
<#if isImmutable == "false">
      - WATCHER_STORAGE_URL=${watcherStorageUrl}
      - WATCHER_CHECK_LOCATION=${watcherCheckLocation}
      - DELEGATE_STORAGE_URL=${delegateStorageUrl}
      - DELEGATE_CHECK_LOCATION=${delegateCheckLocation}
      <#if useCdn == "true">
      - CDN_URL=${cdnUrl}
      - REMOTE_WATCHER_URL_CDN=${remoteWatcherUrlCdn}
      </#if>
<#else>
      - LOG_STREAMING_SERVICE_URL=${logStreamingServiceBaseUrl}
</#if>
      - DEPLOY_MODE=${deployMode}
      - DELEGATE_NAME=${delegateName}
      - NEXT_GEN=true
      - DELEGATE_TYPE=DOCKER
      - DELEGATE_TAGS=${delegateTags}
      - INIT_SCRIPT=echo "Docker delegate init script executed."
# If proxy setting is required, Please refer to
# https://developer.harness.io/docs/platform/delegates/manage-delegates/configure-delegate-proxy-settings for Docker Proxy Settings
