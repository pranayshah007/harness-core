<#macro mutable>
        - name: DELEGATE_TOKEN
          value: ${delegateToken}
        - name: WATCHER_STORAGE_URL
          value: ${watcherStorageUrl}
        - name: WATCHER_CHECK_LOCATION
          value: ${watcherCheckLocation}
        - name: DELEGATE_STORAGE_URL
          value: ${delegateStorageUrl}
        - name: DELEGATE_CHECK_LOCATION
          value: ${delegateCheckLocation}
        - name: HELM_DESIRED_VERSION
          value: ""
        <#if useCdn == "true">
        - name: CDN_URL
          value: "${cdnUrl}"
        - name: REMOTE_WATCHER_URL_CDN
          value: "${remoteWatcherUrlCdn}"
        </#if>
        - name: JRE_VERSION
          value: ${jreVersion}
        - name: HELM3_PATH
          value: ""
        - name: HELM_PATH
          value: ""
        - name: KUSTOMIZE_PATH
          value: ""
        - name: KUBECTL_PATH
          value: ""
        - name: POLL_FOR_TASKS
          value: "false"
        - name: ENABLE_CE
          value: "${enableCE}"
        - name: PROXY_HOST
          value: ""
        - name: PROXY_PORT
          value: ""
        - name: PROXY_SCHEME
          value: ""
        - name: NO_PROXY
          value: ""
        - name: PROXY_MANAGER
          value: "true"
        - name: PROXY_USER
          valueFrom:
            secretKeyRef:
              name: ${delegateName}-proxy
              key: PROXY_USER
        - name: PROXY_PASSWORD
          valueFrom:
            secretKeyRef:
              name: ${delegateName}-proxy
              key: PROXY_PASSWORD
        - name: GRPC_SERVICE_ENABLED
          value: "${grpcServiceEnabled}"
        - name: GRPC_SERVICE_CONNECTOR_PORT
          value: "${grpcServiceConnectorPort}"
        - name:
</#macro>
<#macro immutable>
        - name: CLIENT_TOOLS_DOWNLOAD_DISABLED
          value: "true"
        - name: LOG_STREAMING_SERVICE_URL
          value: "${logStreamingServiceBaseUrl}"
        - name: DELEGATE_RESOURCE_THRESHOLD
          value: ""
        - name: DYNAMIC_REQUEST_HANDLING
          value: "${dynamicHandlingOfRequestEnabled}"
</#macro>
<#macro cgSpecific>
        - name: DELEGATE_PROFILE
          value: "${delegateProfile}"
</#macro>
<#macro cgImmutableSpecific>
        - name: DELEGATE_GROUP_NAME
          value: "${delegateGroupName}"
</#macro>
<#macro ngSpecific>
        - name: DELEGATE_DESCRIPTION
          value: "${delegateDescription}"
        - name: DELEGATE_TAGS
          value: "${delegateTags}"
        - name: NEXT_GEN
          value: "true"
</#macro>
<#macro common>
        - name: JAVA_OPTS
          value: "-Xms64M"
        - name: ACCOUNT_ID
          value: ${accountId}
        - name: MANAGER_HOST_AND_PORT
          value: ${managerHostAndPort}
        - name: DEPLOY_MODE
          value: ${deployMode}
        - name: DELEGATE_NAME
          value: ${delegateName}
        - name: DELEGATE_TYPE
          value: "${delegateType}"
        - name: DELEGATE_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: INIT_SCRIPT
          value: ""
</#macro>