#!/bin/bash -e
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function append_config() {
  CONFIG_KEY=$1
  CONFIG_VALUE=$2
  if [ ! -z "$CONFIG_VALUE" ] ; then
    echo "$CONFIG_KEY: $CONFIG_VALUE" >> config.yml
  fi
}

# 0. Proxy setup
source ./proxy_setup.sh

# 1. Get & execute init script if present
if [ ! -z "$INIT_SCRIPT" ]; then
  echo "#!/bin/bash -e" > init.sh
  echo "$INIT_SCRIPT" >> init.sh
fi

if [ -e init.sh ]; then
    echo "Starting initialization script for delegate"
    source ./init.sh
    if [ $? -eq 0 ];
    then
      echo "Completed executing initialization script"
    else
      echo "Error while executing initialization script. Delegate wont be started."
      exit 1
    fi
fi

# 2. Build config.yml
echo "accountId: $ACCOUNT_ID" >> config.yml
if [ ! -e $ACCOUNT_SECRET ]; then
  echo "delegateToken: $ACCOUNT_SECRET" >> config.yml
else
  echo "delegateToken: $DELEGATE_TOKEN" >> config.yml
fi
echo "managerUrl: $MANAGER_HOST_AND_PORT/api/" >> config.yml
echo "verificationServiceUrl: $MANAGER_HOST_AND_PORT/verification/" >> config.yml
echo "cvNextGenUrl: $MANAGER_HOST_AND_PORT/cv/api/" >> config.yml
echo "logStreamingServiceBaseUrl: $LOG_STREAMING_SERVICE_URL" >> config.yml
echo "heartbeatIntervalMs: 60000" >> config.yml
echo "localDiskPath: /tmp" >> config.yml
echo "maxCachedArtifacts: 2" >> config.yml
echo "pollForTasks: ${POLL_FOR_TASKS:-false}" >> config.yml
echo "grpcServiceEnabled: ${GRPC_SERVICE_ENABLED:-true}" >> config.yml
echo "grpcServiceConnectorPort: ${GRPC_SERVICE_CONNECTOR_PORT:-8080}" >> config.yml
echo "doUpgrade: false" >> config.yml

append_config "clientToolsDownloadDisabled" $CLIENT_TOOLS_DOWNLOAD_DISABLED
append_config "clientCertificateFilePath" $DELEGATE_CLIENT_CERTIFICATE_PATH
append_config "clientCertificateKeyFilePath" $DELEGATE_CLIENT_CERTIFICATE_KEY_PATH
append_config "grpcAuthorityModificationDisabled" ${GRPC_AUTHORITY_MODIFICATION_DISABLED:-false}
# Intended for debugging, has to be set explicitly as its never set in generated yaml.
append_config "trustAllCertificates" ${TRUST_ALL_CERTIFICATES:-false}

# 3. Start the delegate
JAVA_OPTS=${JAVA_OPTS//UseCGroupMemoryLimitForHeap/UseContainerSupport}
exec java $JAVA_OPTS $PROXY_SYS_PROPS -Xmx4096m -XX:+IgnoreUnrecognizedVMOptions -XX:+HeapDumpOnOutOfMemoryError -Xloggc:mygclogfilename.gc -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 -Dcom.sun.jndi.ldap.object.disableEndpointIdentification=true -DLANG=en_US.UTF-8 -jar delegate.jar server config.yml
