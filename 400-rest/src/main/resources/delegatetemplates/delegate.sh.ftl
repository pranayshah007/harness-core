<#include "common.start.sh.ftl">

if [ -z "$1" ]; then
  echo "This script is not meant to be executed directly. The watcher uses it to manage delegate processes."
  exit 0
fi

ULIM=$(ulimit -n)
echo "ulimit -n is set to $ULIM"
if [[ "$ULIM" == "unlimited" || $ULIM -lt 10000 ]]; then
  echo
  echo "WARNING: ulimit -n is too low ($ULIM). Minimum 10000 required."
  echo
fi

if [[ "$OSTYPE" == darwin* ]]; then
  MEM=$(top -l 1 -n 0 | grep PhysMem | cut -d ' ' -f 2 | cut -d 'G' -f 1)
  if [[ $MEM =~ "M" ]]; then
    MEM=$(($(echo $MEM | cut -d 'M' -f 1)/1024))
  fi
  echo "Memory is $MEM"
  if [[ $MEM -lt 6 ]]; then
    echo
    echo "WARNING: Not enough memory ($MEM). Minimum 6 GB required."
    echo
  fi
else
  MEM=$(free -m | grep Mem | awk '{ print $2 }')
  echo "Memory is $MEM MB"
  if [[ $MEM -lt 6000 ]]; then
    echo
    echo "WARNING: Not enough memory ($MEM MB). Minimum 6 GB required."
    echo
  fi
fi

export MANAGER_HOST_AND_PORT=${managerHostAndPort}
if [ -e proxy.config ]; then
  source proxy.config
  if [[ $PROXY_HOST != "" ]]; then
    echo "Using proxy $PROXY_SCHEME://$PROXY_HOST:$PROXY_PORT"
    if [[ $PROXY_USER != "" ]]; then
      export PROXY_USER
      if [[ "$PROXY_PASSWORD_ENC" != "" ]]; then
        export PROXY_PASSWORD=$(echo $PROXY_PASSWORD_ENC | openssl enc -d -a -des-ecb -K ${hexkey})
      fi
      export PROXY_CURL="-x "$PROXY_SCHEME"://"$PROXY_USER:$(url_encode "$PROXY_PASSWORD")@$PROXY_HOST:$PROXY_PORT
    else
      export PROXY_CURL="-x "$PROXY_SCHEME"://"$PROXY_HOST:$PROXY_PORT
      export http_proxy=$PROXY_SCHEME://$PROXY_HOST:$PROXY_PORT
      export https_proxy=$PROXY_SCHEME://$PROXY_HOST:$PROXY_PORT
    fi
    PROXY_SYS_PROPS="-DproxyScheme=$PROXY_SCHEME -Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT"
  fi

  if [[ $PROXY_MANAGER == "true" || $PROXY_MANAGER == "" ]]; then
    export MANAGER_PROXY_CURL=$PROXY_CURL
  else
<#noparse>
    HOST_AND_PORT_ARRAY=(${MANAGER_HOST_AND_PORT//:/ })
    MANAGER_HOST="${HOST_AND_PORT_ARRAY[1]}"
    MANAGER_HOST="${MANAGER_HOST:2}"
</#noparse>
    echo "No proxy for Harness manager at $MANAGER_HOST"
    if [[ $NO_PROXY == "" ]]; then
      NO_PROXY=$MANAGER_HOST
    else
      NO_PROXY="$NO_PROXY,$MANAGER_HOST"
    fi
  fi

  if [[ $NO_PROXY != "" ]]; then
    echo "No proxy for domain suffixes $NO_PROXY"
    export no_proxy=$NO_PROXY
    SYSTEM_PROPERTY_NO_PROXY=`echo $NO_PROXY | sed "s/\,/|*/g"`
    PROXY_SYS_PROPS=$PROXY_SYS_PROPS" -Dhttp.nonProxyHosts=*$SYSTEM_PROPERTY_NO_PROXY"
  fi
fi

if [ ! -d $JRE_DIR -o ! -e $JRE_BINARY ]; then
  echo "Downloading JRE packages..."
  JVM_TAR_FILENAME=$(basename "$JVM_URL")
  curl $MANAGER_PROXY_CURL -#kLO $JVM_URL
  echo "Extracting JRE packages..."
  rm -rf $JRE_DIR
  tar xzf $JVM_TAR_FILENAME
  rm -f $JVM_TAR_FILENAME
fi

export DEPLOY_MODE=${deployMode}

if [[ $DEPLOY_MODE != "KUBERNETES" ]]; then
  echo "Checking Delegate latest version..."
  DELEGATE_STORAGE_URL=${delegateStorageUrl}
  REMOTE_DELEGATE_LATEST=$(curl $MANAGER_PROXY_CURL -ks $DELEGATE_STORAGE_URL/${delegateCheckLocation})
  REMOTE_DELEGATE_URL=$DELEGATE_STORAGE_URL/$(echo $REMOTE_DELEGATE_LATEST | cut -d " " -f2)
  REMOTE_DELEGATE_VERSION=$(echo $REMOTE_DELEGATE_LATEST | cut -d " " -f1)

  if [ ! -e delegate.jar ]; then
    echo "Downloading Delegate $REMOTE_DELEGATE_VERSION ..."
    curl $MANAGER_PROXY_CURL -#k $REMOTE_DELEGATE_URL -o delegate.jar
  else
    DELEGATE_CURRENT_VERSION=$(jar_app_version delegate.jar)
    if [[ $REMOTE_DELEGATE_VERSION != $DELEGATE_CURRENT_VERSION ]]; then
      echo "The current version $DELEGATE_CURRENT_VERSION is not the same as the expected remote version $REMOTE_DELEGATE_VERSION"
      echo "Downloading Delegate $REMOTE_DELEGATE_VERSION ..."
      mkdir -p backup.$DELEGATE_CURRENT_VERSION
      cp delegate.jar backup.$DELEGATE_CURRENT_VERSION
      curl $MANAGER_PROXY_CURL -#k $REMOTE_DELEGATE_URL -o delegate.jar
    fi
  fi
fi

if [ -z $CLIENT_TOOLS_DOWNLOAD_DISABLED ]; then
  export CLIENT_TOOLS_DOWNLOAD_DISABLED=false
fi

if [ -z $INSTALL_CLIENT_TOOLS_IN_BACKGROUND ]; then
  export INSTALL_CLIENT_TOOLS_IN_BACKGROUND=true
fi

if [ ! -e config-delegate.yml ]; then
  echo "accountId: ${accountId}" > config-delegate.yml
fi
test "$(tail -c 1 config-delegate.yml)" && `echo "" >> config-delegate.yml`
<#if delegateToken??>
if ! `grep delegateToken config-delegate.yml > /dev/null`; then
  echo "delegateToken: ${delegateToken}" >> config-delegate.yml
fi
<#else>
if ! `grep delegateToken config-delegate.yml > /dev/null`; then
  echo "delegateToken: ${accountSecret}" >> config-delegate.yml
fi
</#if>
if ! `grep dynamicHandlingOfRequestEnabled config-delegate.yml > /dev/null`; then
  echo "dynamicHandlingOfRequestEnabled: ${dynamicHandlingOfRequestEnabled}" >> config-delegate.yml
fi
if ! `grep managerUrl config-delegate.yml > /dev/null`; then
  echo "managerUrl: ${managerHostAndPort}/api/" >> config-delegate.yml
fi
if ! `grep verificationServiceUrl config-delegate.yml > /dev/null`; then
  echo "verificationServiceUrl: ${managerHostAndPort}/verification/" >> config-delegate.yml
elif [[ "$(grep verificationServiceUrl config-delegate.yml | cut -d ' ' -f 2)" != "${managerHostAndPort}/verification/" ]]; then
  sed -i.bak "s|^verificationServiceUrl:.*$|verificationServiceUrl: ${managerHostAndPort}/verification/|" config-delegate.yml
fi
if ! `grep cvNextGenUrl config-delegate.yml > /dev/null`; then
  echo "cvNextGenUrl: ${managerHostAndPort}/cv/api/" >> config-delegate.yml
elif [[ "$(grep cvNextGenUrl config-delegate.yml | cut -d ' ' -f 2)" != "${managerHostAndPort}/cv/api/" ]]; then
  sed -i.bak "s|^cvNextGenUrl:.*$|cvNextGenUrl: ${managerHostAndPort}/cv/api/|" config-delegate.yml
fi
if ! `grep watcherCheckLocation config-delegate.yml > /dev/null`; then
  echo "watcherCheckLocation: ${watcherStorageUrl}/${watcherCheckLocation}" >> config-delegate.yml
elif [[ "$(grep watcherCheckLocation config-delegate.yml | cut -d ' ' -f 2)" != "${watcherStorageUrl}/${watcherCheckLocation}" ]]; then
  sed -i.bak "s|^watcherCheckLocation:.*$|watcherCheckLocation: ${watcherStorageUrl}/${watcherCheckLocation}|" config-delegate.yml
fi
if ! `grep heartbeatIntervalMs config-delegate.yml > /dev/null`; then
  echo "heartbeatIntervalMs: 60000" >> config-delegate.yml
fi
if ! `grep doUpgrade config-delegate.yml > /dev/null`; then
  echo "doUpgrade: true" >> config-delegate.yml
fi
if ! `grep localDiskPath config-delegate.yml > /dev/null`; then
  echo "localDiskPath: /tmp" >> config-delegate.yml
fi
if ! `grep maxCachedArtifacts config-delegate.yml > /dev/null`; then
  echo "maxCachedArtifacts: 2" >> config-delegate.yml
fi
if ! `grep pollForTasks config-delegate.yml > /dev/null`; then
  if [ "$DEPLOY_MODE" == "ONPREM" ]; then
      echo "pollForTasks: true" >> config-delegate.yml
  else
      echo "pollForTasks: false" >> config-delegate.yml
  fi
fi

if ! `grep useCdn config-delegate.yml > /dev/null`; then
  echo "useCdn: ${useCdn}" >> config-delegate.yml
elif [[ "$(grep useCdn config-delegate.yml | cut -d ' ' -f 2)" != "${useCdn}" ]]; then
  sed -i.bak "s|^useCdn:.*$|useCdn: ${useCdn}|" config-delegate.yml
fi
<#if useCdn == "true">
if ! `grep cdnUrl config-delegate.yml > /dev/null`; then
  echo "cdnUrl: ${cdnUrl}" >> config-delegate.yml
elif [[ "$(grep cdnUrl config-delegate.yml | cut -d ' ' -f 2)" != "${cdnUrl}" ]]; then
  sed -i.bak "s|^cdnUrl:.*$|cdnUrl: ${cdnUrl}|" config-delegate.yml
fi
</#if>
<#if managerTarget??>
if ! `grep managerTarget config-delegate.yml > /dev/null`; then
  echo "managerTarget: ${managerTarget}" >> config-delegate.yml
elif [[ "$(grep managerTarget config-delegate.yml | cut -d ' ' -f 2)" != "${managerTarget}" ]]; then
  sed -i.bak "s|^managerTarget:.*$|managerTarget: ${managerTarget}|" config-delegate.yml
fi
if ! `grep managerAuthority config-delegate.yml > /dev/null`; then
  echo "managerAuthority: ${managerAuthority}" >> config-delegate.yml
elif [[ "$(grep managerAuthority config-delegate.yml | cut -d ' ' -f 2)" != "${managerAuthority}" ]]; then
  sed -i.bak "s|^managerAuthority:.*$|managerAuthority: ${managerAuthority}|" config-delegate.yml
fi
</#if>

if ! `grep grpcServiceEnabled config-delegate.yml > /dev/null`; then
  echo "grpcServiceEnabled: $GRPC_SERVICE_ENABLED" >> config-delegate.yml
elif [[ "$(grep grpcServiceEnabled config-delegate.yml | cut -d ' ' -f 2)" != "$GRPC_SERVICE_ENABLED" ]]; then
  sed -i.bak "s|^grpcServiceEnabled:.*$|grpcServiceEnabled: $GRPC_SERVICE_ENABLED|" config-delegate.yml
fi

if ! `grep grpcServiceConnectorPort config-delegate.yml > /dev/null`; then
  echo "grpcServiceConnectorPort: $GRPC_SERVICE_CONNECTOR_PORT" >> config-delegate.yml
elif [[ "$(grep grpcServiceConnectorPort config-delegate.yml | cut -d ' ' -f 2)" != "$GRPC_SERVICE_CONNECTOR_PORT" ]]; then
  sed -i.bak "s|^grpcServiceConnectorPort:.*$|grpcServiceConnectorPort: $GRPC_SERVICE_CONNECTOR_PORT|" config-delegate.yml
fi

if ! `grep logStreamingServiceBaseUrl config-delegate.yml > /dev/null`; then
  echo "logStreamingServiceBaseUrl: ${logStreamingServiceBaseUrl}" >> config-delegate.yml
elif [[ "$(grep logStreamingServiceBaseUrl config-delegate.yml | cut -d ' ' -f 2)" != "${logStreamingServiceBaseUrl}" ]]; then
  sed -i.bak "s|^logStreamingServiceBaseUrl:.*$|logStreamingServiceBaseUrl: ${logStreamingServiceBaseUrl}|" config-delegate.yml
fi

if ! `grep clientToolsDownloadDisabled config-delegate.yml > /dev/null`; then
  echo "clientToolsDownloadDisabled: $CLIENT_TOOLS_DOWNLOAD_DISABLED" >> config-delegate.yml
fi

if ! `grep installClientToolsInBackground config-delegate.yml > /dev/null`; then
  echo "installClientToolsInBackground: $INSTALL_CLIENT_TOOLS_IN_BACKGROUND" >> config-delegate.yml
fi

if [ ! -z "$KUSTOMIZE_PATH" ] && ! `grep kustomizePath config-delegate.yml > /dev/null` ; then
  echo "kustomizePath: $KUSTOMIZE_PATH" >> config-delegate.yml
fi

if [ ! -z "$OC_PATH" ] && ! `grep ocPath config-delegate.yml > /dev/null` ; then
  echo "ocPath: $OC_PATH" >> config-delegate.yml
fi

if [ ! -z "$KUBECTL_PATH" ] && ! `grep kubectlPath config-delegate.yml > /dev/null` ; then
  echo "kubectlPath: $KUBECTL_PATH" >> config-delegate.yml
fi

if [ ! -z "$CF_CLI6_PATH" ] && ! `grep cfCli6Path config-delegate.yml > /dev/null` ; then
  echo "cfCli6Path: $CF_CLI6_PATH" >> config-delegate.yml
fi

if [ ! -z "$CF_CLI7_PATH" ] && ! `grep cfCli7Path config-delegate.yml > /dev/null` ; then
  echo "cfCli7Path: $CF_CLI7_PATH" >> config-delegate.yml
fi

rm -f -- *.bak

export SCM_VERSION=${scmVersion}

<#if delegateName??>
export DELEGATE_NAME=${delegateName}
</#if>
<#if delegateProfile??>
export DELEGATE_PROFILE=${delegateProfile}
</#if>
<#if delegateType??>
export DELEGATE_TYPE=${delegateType}
</#if>

export HOSTNAME
export CAPSULE_CACHE_DIR="$DIR/.cache"

if [[ ! -z $INSTRUMENTATION ]]; then
  export JRE_BINARY=$JDK_BINARY
fi

if [ ! -e alpn-boot-8.1.13.v20181017.jar -a -n "$ALPN_BOOT_JAR_URL" ]; then
  curl $MANAGER_PROXY_CURL -ks $ALPN_BOOT_JAR_URL -o alpn-boot-8.1.13.v20181017.jar
  ALPN_CMD="-Xbootclasspath/p:alpn-boot-8.1.13.v20181017.jar"
else
  ALPN_CMD=""
fi
# Strip JAVA_OPTS that is not recognized by JRE11
<#noparse>
JAVA_OPTS=${JAVA_OPTS//UseCGroupMemoryLimitForHeap/UseContainerSupport}
</#noparse>
if [[ $DEPLOY_MODE == "KUBERNETES" ]]; then
  echo "Starting delegate - version $2 with java $JRE_BINARY"
  $JRE_BINARY $INSTRUMENTATION $PROXY_SYS_PROPS $OVERRIDE_TMP_PROPS -DACCOUNT_ID="${accountId}" -DMANAGER_HOST_AND_PORT="${managerHostAndPort}" -Ddelegatesourcedir="$DIR" ${delegateXmx} -XX:+IgnoreUnrecognizedVMOptions -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -Xloggc:mygclogfilename.gc -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 -Dcom.sun.jndi.ldap.object.disableEndpointIdentification=true -DLANG=en_US.UTF-8 $JAVA_OPTS $ALPN_CMD -jar $2/delegate.jar config-delegate.yml watched $1
else
  echo "Starting delegate - version $REMOTE_DELEGATE_VERSION with java $JRE_BINARY"
  $JRE_BINARY $INSTRUMENTATION $PROXY_SYS_PROPS $OVERRIDE_TMP_PROPS -DACCOUNT_ID="${accountId}" -DMANAGER_HOST_AND_PORT="${managerHostAndPort}" -Ddelegatesourcedir="$DIR" ${delegateXmx} -XX:+IgnoreUnrecognizedVMOptions -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -Xloggc:mygclogfilename.gc -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 -Dcom.sun.jndi.ldap.object.disableEndpointIdentification=true -DLANG=en_US.UTF-8 $JAVA_OPTS $ALPN_CMD -jar delegate.jar config-delegate.yml watched $1
fi

sleep 3
if `pgrep -f "\-Ddelegatesourcedir=$DIR"> /dev/null`; then
  echo "Delegate started"
else
  echo "Failed to start Delegate."
  echo "$(tail -n 30 delegate.log)"
fi )
