<#macro mutable>
        - name: WATCHER_STORAGE_URL
          value: ${watcherStorageUrl}
        - name: WATCHER_CHECK_LOCATION
          value: ${watcherCheckLocation}
        - name: REMOTE_WATCHER_URL_CDN
          value: ${remoteWatcherUrlCdn}
        - name: DELEGATE_STORAGE_URL
          value: ${delegateStorageUrl}
        - name: DELEGATE_CHECK_LOCATION
          value: ${delegateCheckLocation}
        - name: HELM_DESIRED_VERSION
          value: ""
        - name: USE_CDN
          value: "${useCdn}"
        - name: CDN_URL
          value: ${cdnUrl}
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
</#macro>
<#macro immutable>
        - name: CLIENT_TOOLS_DOWNLOAD_DISABLED
          value: "true"
</#macro>
<#macro cgSpecific>
        - name: DELEGATE_PROFILE
          value: "${delegateProfile}"
</#macro>
<#macro ngSpecific>
        - name: DELEGATE_DESCRIPTION
          value: "${delegateDescription}"
        - name: DELEGATE_TAGS
          value: "${delegateTags}"
        - name: DELEGATE_ORG_IDENTIFIER
          value: "${delegateOrgIdentifier}"
        - name: DELEGATE_PROJECT_IDENTIFIER
          value: "${delegateProjectIdentifier}"
        - name: INIT_SCRIPT
          value: ""
        - name: NEXT_GEN
          value: "true"
</#macro>
<#macro common>
        - name: JAVA_OPTS
          value: "-Xms64M"
        - name: ACCOUNT_ID
          value: ${accountId}
        - name: ACCOUNT_SECRET
          value: ${accountSecret}
        - name: MANAGER_HOST_AND_PORT
          value: ${managerHostAndPort}
        - name: DEPLOY_MODE
          value: ${deployMode}
        - name: DELEGATE_NAME
          value: ${delegateName}
        - name: DELEGATE_TYPE
          value: "${delegateType}"
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
        - name: POLL_FOR_TASKS
          value: "false"
        - name: ENABLE_CE
          value: "${enableCE}"
        - name: GRPC_SERVICE_ENABLED
          value: "${grpcServiceEnabled}"
        - name: GRPC_SERVICE_CONNECTOR_PORT
          value: "${grpcServiceConnectorPort}"
        - name: VERSION_CHECK_DISABLED
          value: "${versionCheckDisabled}"
        - name: DELEGATE_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
</#macro>
