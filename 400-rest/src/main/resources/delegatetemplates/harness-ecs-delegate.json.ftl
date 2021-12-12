{
  "containerDefinitions": [
    {
      "portMappings": [
        {
          "hostPort": 8080,
          "protocol": "tcp",
          "containerPort": 8080
        }
      ],
      "cpu": 1,
      "environment": [
        {
          "name": "ACCOUNT_ID",
          "value": "${accountId}"
        },
        {
          "name": "ACCOUNT_SECRET",
          "value": "${accountSecret}"
        },
        {
          "name": "DELEGATE_CHECK_LOCATION",
          "value": "${delegateCheckLocation}"
        },
        {
          "name": "DELEGATE_STORAGE_URL",
          "value": "${delegateStorageUrl}"
        },
        {
          "name": "DELEGATE_TYPE",
          "value": "${delegateType}"
        },
        {
          "name": "DELEGATE_GROUP_NAME",
          "value": "${delegateGroupName}"
        },
        {
          "name": "DELEGATE_GROUP_ID",
          "value": "${delegateGroupId}"
        },
        {
          "name": "DELEGATE_PROFILE",
          "value": "${delegateProfile}"
        },
        {
          "name": "INIT_SCRIPT",
          "value": ""
        },
        {
          "name": "DEPLOY_MODE",
          "value": "${deployMode}"
        },
        {
          "name": "MANAGER_HOST_AND_PORT",
          "value": "${managerHostAndPort}"
        },
        {
          "name": "POLL_FOR_TASKS",
          "value": "false"
        },
        {
          "name": "WATCHER_CHECK_LOCATION",
          "value": "${watcherCheckLocation}"
        },
        {
          "name": "WATCHER_STORAGE_URL",
          "value": "${watcherStorageUrl}"
        },
        {
          "name": "REMOTE_WATCHER_URL_CDN",
          "value": "${remoteWatcherUrlCdn}"
        },
        {
          "name": "CF_PLUGIN_HOME",
          "value": ""
        },
        {
          "name": "USE_CDN",
          "value": "${useCdn}"
        },
        {
          "name": "CDN_URL",
          "value": "${cdnUrl}"
        },
        {
          "name": "JRE_VERSION",
          "value": "${jreVersion}"
        },
        {
          "name": "HELM3_PATH",
          "value": ""
        },
        {
          "name": "HELM_PATH",
          "value": ""
        },
        {
          "name": "CF_CLI6_PATH",
          "value": ""
        },
        {
          "name": "CF_CLI7_PATH",
          "value": ""
        },
        {
          "name": "KUSTOMIZE_PATH",
          "value": ""
        },
        {
          "name": "OC_PATH",
          "value": ""
        },
        {
           "name": "KUBECTL_PATH",
           "value": ""
        },
        {
          "name": "INSTALL_CLIENT_TOOLS_IN_BACKGROUND",
          "value": "true"
        },
        {
          "name": "VERSION_CHECK_DISABLED",
          "value": "${versionCheckDisabled}"
        }
      ],
      "memory": 6144,
      "image": "${delegateDockerImage}",
      "essential": true,
      ${hostnameForDelegate}
      "name": "ecs-delegate"
    }
  ],
  "memory": "6144",
  "requiresCompatibilities": [
    "EC2"
  ],
  ${networkModeForTask}
  "cpu": "1024",
  "family": "harness-delegate-task-spec"
}
