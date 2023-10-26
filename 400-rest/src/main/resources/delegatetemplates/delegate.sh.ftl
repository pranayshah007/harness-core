<#include "common.start.sh.ftl">

if [ -z "$1" ]; then
  echo "This script is not meant to be executed directly. The watcher uses it to manage delegate processes."
  exit 0
fi

if [ -e config-delegate.yml ]; then
  rm config-delegate.yml
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

if [ -z $CLIENT_TOOLS_DOWNLOAD_DISABLED ]; then
  export CLIENT_TOOLS_DOWNLOAD_DISABLED=false
fi

if [ -z $INSTALL_CLIENT_TOOLS_IN_BACKGROUND ]; then
  export INSTALL_CLIENT_TOOLS_IN_BACKGROUND=true
fi

echo "accountId: ${accountId}" > config-delegate.yml

<#if delegateToken??>
echo "delegateToken: ${delegateToken}" >> config-delegate.yml
<#else>
echo "delegateToken: ${accountSecret}" >> config-delegate.yml
</#if>
echo "dynamicHandlingOfRequestEnabled: ${dynamicHandlingOfRequestEnabled}" >> config-delegate.yml
echo "managerUrl: ${managerHostAndPort}/api/" >> config-delegate.yml
echo "verificationServiceUrl: ${managerHostAndPort}/verification/" >> config-delegate.yml
echo "cvNextGenUrl: ${managerHostAndPort}/cv/api/" >> config-delegate.yml
echo "watcherCheckLocation: ${watcherStorageUrl}/${watcherCheckLocation}" >> config-delegate.yml
echo "heartbeatIntervalMs: 50000" >> config-delegate.yml
echo "doUpgrade: true" >> config-delegate.yml
echo "localDiskPath: /tmp" >> config-delegate.yml
echo "maxCachedArtifacts: 2" >> config-delegate.yml

if [ "$DEPLOY_MODE" == "ONPREM" ]; then
    echo "pollForTasks: true" >> config-delegate.yml
elif [[ ! -z "$POLL_FOR_TASKS" ]]; then
    echo "pollForTasks: $POLL_FOR_TASKS" >> config-delegate.yml
else
    echo "pollForTasks: false" >> config-delegate.yml
fi

echo "useCdn: ${useCdn}" >> config-delegate.yml
<#if useCdn == "true">
echo "cdnUrl: ${cdnUrl}" >> config-delegate.yml
</#if>
<#if managerTarget??>
echo "managerTarget: ${managerTarget}" >> config-delegate.yml
echo "managerAuthority: ${managerAuthority}" >> config-delegate.yml
</#if>

echo "grpcServiceEnabled: $GRPC_SERVICE_ENABLED" >> config-delegate.yml
echo "grpcServiceConnectorPort: $GRPC_SERVICE_CONNECTOR_PORT" >> config-delegate.yml
echo "logStreamingServiceBaseUrl: ${logStreamingServiceBaseUrl}" >> config-delegate.yml
echo "clientToolsDownloadDisabled: $CLIENT_TOOLS_DOWNLOAD_DISABLED" >> config-delegate.yml
echo "installClientToolsInBackground: $INSTALL_CLIENT_TOOLS_IN_BACKGROUND" >> config-delegate.yml

if [ ! -z "$KUSTOMIZE_PATH" ] ; then
  echo "kustomizePath: $KUSTOMIZE_PATH" >> config-delegate.yml
fi

if [ ! -z "$OC_PATH" ] ; then
  echo "ocPath: $OC_PATH" >> config-delegate.yml
fi

if [ ! -z "$KUBECTL_PATH" ] ; then
  echo "kubectlPath: $KUBECTL_PATH" >> config-delegate.yml
fi

if [ ! -z "$HELM3_PATH" ] ; then
  echo "helm3Path: $HELM3_PATH" >> config-delegate.yml
fi

if [ ! -z "$HELM_PATH" ] ; then
  echo "helmPath: $HELM_PATH" >> config-delegate.yml
fi

if [ ! -z "$CF_CLI6_PATH" ] ; then
  echo "cfCli6Path: $CF_CLI6_PATH" >> config-delegate.yml
fi

if [ ! -z "$CF_CLI7_PATH" ] ; then
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
echo "Starting delegate - version $2 with java $JRE_BINARY"
$JRE_BINARY $INSTRUMENTATION $PROXY_SYS_PROPS $OVERRIDE_TMP_PROPS -DACCOUNT_ID="${accountId}" -DMANAGER_HOST_AND_PORT="${managerHostAndPort}" -Ddelegatesourcedir="$DIR" ${delegateXmx} -XX:+IgnoreUnrecognizedVMOptions -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 -Dcom.sun.jndi.ldap.object.disableEndpointIdentification=true -DLANG=en_US.UTF-8 --illegal-access=debug --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED --add-opens java.base/java.nio.file=ALL-UNNAMED --add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.xml/com.sun.org.apache.xpath.internal=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-exports java.xml/com.sun.org.apache.xerces.internal.parsers=ALL-UNNAMED --add-exports java.base/sun.nio.ch=ALL-UNNAMED $JAVA_OPTS $ALPN_CMD -jar $2/delegate.jar config-delegate.yml watched $1

sleep 3
if `pgrep -f "\-Ddelegatesourcedir=$DIR"> /dev/null`; then
  echo "Delegate started"
else
  echo "Failed to start Delegate."
  echo "$(tail -n 30 delegate.log)"
fi )
