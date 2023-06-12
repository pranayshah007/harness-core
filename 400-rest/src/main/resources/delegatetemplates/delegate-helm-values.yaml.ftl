# This harness-delegate-values.yaml file is compatible with version 1.0.2
# of the harness-delegate helm chart.

# You can download the harness-delegate helm chart at
# https://app.harness.io/storage/harness-download/harness-helm-charts/

# To add Harness helm repo with name harness:
# helm repo add harness https://app.harness.io/storage/harness-download/harness-helm-charts/

# To install the chart with the release name my-release and this values.yaml
# helm install --name my-release harness/harness-delegate -f harness-delegate-values.yaml

# Account Id to which the delegate will be connecting
accountId: ${accountId}

# Secret identifier associated with the account
delegateToken: ${delegateToken}

# Short 6 character identifier of the account
accountIdShort: ${kubernetesAccountLabel}
delegateName: ${delegateName}
deployMode: ${deployMode}
delegateType: ${delegateType}
delegateDockerImage: ${delegateDockerImage}
managerHostAndPort: ${managerHostAndPort}

delegateProfile: "${delegateProfile}"

<#if isImmutable == "false">
watcherStorageUrl: ${watcherStorageUrl}
watcherCheckLocation: ${watcherCheckLocation}
delegateStorageUrl: ${delegateStorageUrl}
delegateCheckLocation: ${delegateCheckLocation}
useCdn: ${useCdn}
jreVersion: ${jreVersion}
<#if useCdn == "true">
cdnUrl: ${cdnUrl}
remoteWatcherUrlCdn: ${remoteWatcherUrlCdn}
</#if>
</#if>
