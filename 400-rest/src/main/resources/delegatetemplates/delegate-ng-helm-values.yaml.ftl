# This harness-delegate-values.yaml file is compatible harness-delegate-ng helm chart

# You can download the harness-delegate helm chart at
# https://app.harness.io/storage/harness-download/harness-helm-charts/

# To add Harness helm repo with name harness:
# helm repo add harness https://app.harness.io/storage/harness-download/harness-helm-charts/

# To install the chart with the release name my-release and this values.yaml
# helm install --name my-release harness/harness-delegate-ng -f harness-delegate-values.yaml

# Account Id to which the delegate will be connecting
accountId: ${accountId}

# Secret identifier associated with the account
delegateToken: ${delegateToken}

delegateName: ${delegateName}
delegateDockerImage: ${delegateDockerImage}
managerEndpoint: ${managerHostAndPort}

# Mention tags that will be used to identify delegate
tags: "${delegateTags}"
description: "${delegateDescription}"

# Specify access for delegate, CLUSTER_ADMIN, CLUSTER_VIEWER and NAMESPACE_ADMIN are valid entries.
k8sPermissionsType: ${k8sPermissionsType}

# Resource Configuration
replicas: ${delegateReplicas}
cpu: ${delegateCpu}
memory: ${delegateRam}

# Need to run something specific before delegate starts, enter your script in initScripts.
initScript: ""

# Specify JAVA_OPTS
javaOpts: "-Xms64M"

logStreamingServiceBaseUrl: "${logStreamingServiceBaseUrl}"