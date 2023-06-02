<#include "common.start.sh.ftl">

ULIM=$(ulimit -n)
if [[ "$ULIM" == "unlimited" || $ULIM -lt 10000 ]]; then
  echo
  echo "WARNING: ulimit -n is too low ($ULIM)"
  echo
  echo "Run the following command to set it to 10000 or greater:"
  echo
  echo "ulimit -n 10000"
  echo
  echo "Continuing in 15 seconds. Ctrl-C to quit."
  sleep 15s
  echo
fi

if [[ "$OSTYPE" == darwin* ]]; then
  MEM=$(top -l 1 -n 0 | grep PhysMem | cut -d ' ' -f 2 | cut -d 'G' -f 1)
  if [[ $MEM =~ "M" ]]; then
    MEM=$(($(echo $MEM | cut -d 'M' -f 1)/1024))
  fi
  if [[ $MEM -lt 6 ]]; then
    echo
    echo "WARNING: Not enough memory ($MEM). Minimum 6 GB required."
    echo
    echo "Continuing in 15 seconds. Ctrl-C to quit."
    sleep 15s
    echo
  fi
else
  MEM=$(free -m | grep Mem | awk '{ print $2 }')
  if [[ $MEM -lt 6000 ]]; then
    echo
    echo "WARNING: Not enough memory ($MEM MB). Minimum 6 GB required."
    echo
    echo "Continuing in 15 seconds. Ctrl-C to quit."
    sleep 15s
    echo
  fi
fi

export MANAGER_HOST_AND_PORT=${managerHostAndPort}
if [[ -e proxy.config ]]; then
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

if [ -s init.sh ]; then
    echo "Starting initialization script for delegate"
    CURRENT_WORKING_DIRECTORY=$(pwd)
    source ./init.sh
    cd "$CURRENT_WORKING_DIRECTORY"
    if [ $? -eq 0 ];
    then
      echo "Completed executing initialization script"
    else
      echo "Error while executing initialization script. Delegate will not start."
      exit 1
    fi
fi

if [[ "$OSTYPE" == linux* ]]; then
  touch /tmp/exec-test.sh && chmod +x /tmp/exec-test.sh
  /tmp/exec-test.sh
  if [ ! $? -eq 0 ]; then
    echo "/tmp is mounted noexec. Overriding tmpdir"
    export OVERRIDE_TMP_PROPS="-Djava.io.tmpdir=$DIR/tmp"
    export WATCHER_JAVA_OPTS
    export JAVA_OPTS
  fi
fi

ACCOUNT_STATUS=$(curl $MANAGER_PROXY_CURL -ks ${managerHostAndPort}/api/account/${accountId}/status | cut -d ":" -f 3 | cut -d "," -f 1 | cut -d "\"" -f 2)
if [[ $ACCOUNT_STATUS == "DELETED" ]]; then
  rm README.txt delegate.sh proxy.config start.sh stop.sh
  touch __deleted__
  exit 0
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

if [ ! -d $JRE_DIR  -o ! -e $JRE_BINARY ]; then
echo "No JRE available. Exiting."
exit 1
fi

export DEPLOY_MODE=${deployMode}

<#if isOnPrem == "false">
REMOTE_WATCHER_VERSION=${watcherJarVersion}
REMOTE_WATCHER_URL=${remoteWatcherUrlCdn}/openjdk-8u242/${watcherJarVersion}/watcher.jar
<#else>
echo "Checking Watcher latest version..."
WATCHER_STORAGE_URL=${watcherStorageUrl}
REMOTE_WATCHER_LATEST=$(curl $MANAGER_PROXY_CURL -ks $WATCHER_STORAGE_URL/${watcherCheckLocation})
REMOTE_WATCHER_URL=$WATCHER_STORAGE_URL/$(echo $REMOTE_WATCHER_LATEST | cut -d " " -f2)
REMOTE_WATCHER_VERSION=$(echo $REMOTE_WATCHER_LATEST | cut -d " " -f1)
</#if>

if [ ! -e watcher.jar ]; then
  echo "Downloading Watcher $REMOTE_WATCHER_VERSION ..."
  curl $MANAGER_PROXY_CURL -#k $REMOTE_WATCHER_URL -o watcher.jar
else
  WATCHER_CURRENT_VERSION=$(echo $(jar_app_version watcher.jar) | cut -d "." -f3)
  if [[ $REMOTE_WATCHER_VERSION != $WATCHER_CURRENT_VERSION ]]; then
    echo "The current version $WATCHER_CURRENT_VERSION is not the same as the expected remote version $REMOTE_WATCHER_VERSION"
    echo "Downloading Watcher $REMOTE_WATCHER_VERSION ..."
    mkdir -p watcherBackup.$WATCHER_CURRENT_VERSION
    cp watcher.jar watcherBackup.$WATCHER_CURRENT_VERSION
    curl $MANAGER_PROXY_CURL -#k $REMOTE_WATCHER_URL -o watcher.jar
  fi
fi

if [ -e config-watcher.yml ]; then
  rm config-watcher.yml
fi

echo "accountId: ${accountId}" > config-watcher.yml
echo "delegateToken: ${delegateToken}" >> config-watcher.yml
echo "managerUrl: ${managerHostAndPort}/api/" >> config-watcher.yml
echo "doUpgrade: true" >> config-watcher.yml
echo "upgradeCheckLocation: ${watcherStorageUrl}/${watcherCheckLocation}" >> config-watcher.yml
echo "upgradeCheckIntervalSeconds: 1200" >> config-watcher.yml
echo "delegateCheckLocation: ${delegateStorageUrl}/${delegateCheckLocation}" >> config-watcher.yml
echo "fileHandlesMonitoringEnabled: false" >> config-watcher.yml
echo "fileHandlesMonitoringIntervalInMinutes: 15" >> config-watcher.yml
echo "fileHandlesLogsRetentionInMinutes: 1440" >> config-watcher.yml

rm -f -- *.bak

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
export DELEGATE_STORAGE_URL=${delegateStorageUrl}

# Strip JAVA_OPTS that is not recognized by JRE11
<#noparse>
WATCHER_JAVA_OPTS=${WATCHER_JAVA_OPTS//UseCGroupMemoryLimitForHeap/UseContainerSupport}
</#noparse>

if [[ $1 == "upgrade" ]]; then
  echo "Upgrade"
  WATCHER_CURRENT_VERSION=$(jar_app_version watcher.jar)
  mkdir -p watcherBackup.$WATCHER_CURRENT_VERSION
  cp watcher.jar watcherBackup.$WATCHER_CURRENT_VERSION
  $JRE_BINARY $PROXY_SYS_PROPS $OVERRIDE_TMP_PROPS -Dwatchersourcedir="$DIR" -Xmx192m -XX:+HeapDumpOnOutOfMemoryError -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 $WATCHER_JAVA_OPTS -jar watcher.jar config-watcher.yml upgrade $2
else
  if `pgrep -f "\-Dwatchersourcedir=$DIR"> /dev/null`; then
    echo "Watcher already running"
  else
    nohup $JRE_BINARY $PROXY_SYS_PROPS $OVERRIDE_TMP_PROPS -Dwatchersourcedir="$DIR" -Xmx192m -XX:+HeapDumpOnOutOfMemoryError -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8 $WATCHER_JAVA_OPTS -jar watcher.jar config-watcher.yml >nohup-watcher.out 2>&1 &
    sleep 5
    if `pgrep -f "\-Dwatchersourcedir=$DIR"> /dev/null`; then
      echo "Watcher started"
    else
      echo "Failed to start Watcher."
      echo "$(tail -n 30 watcher.log)"
      exit 1
    fi
  fi
fi ) 2>&1 | tee -a logs/log_clean.log && sed '/######################################################################## 100.0%/d' logs/log_clean.log >> logs/startscript.log
rm logs/log_clean.log
