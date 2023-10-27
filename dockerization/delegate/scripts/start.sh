#!/bin/bash -e
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function jar_app_version() {
  JAR=$1
  if unzip -l $JAR | grep -q io/harness/versionInfo.yaml
  then
    VERSION=$(unzip -c $JAR io/harness/versionInfo.yaml | grep "^version " | cut -d ":" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
  fi

  if [ -z "$VERSION" ]
  then
    if unzip -l $JAR | grep -q main/resources-filtered/versionInfo.yaml
    then
      VERSION=$(unzip -c $JAR main/resources-filtered/versionInfo.yaml | grep "^version " | cut -d ":" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
    fi
  fi

  if [ -z "$VERSION" ]
  then
    if unzip -l $JAR | grep -q BOOT-INF/classes/main/resources-filtered/versionInfo.yaml
    then
      VERSION=$(unzip -c $JAR BOOT-INF/classes/main/resources-filtered/versionInfo.yaml | grep "^version " | cut -d ":" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
    fi
  fi

  if [ -z "$VERSION" ]
  then
    VERSION=$(unzip -c $JAR META-INF/MANIFEST.MF | grep Application-Version | cut -d "=" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
  fi
  echo $VERSION
}

# url-encodes a given input string - used to encode the proxy password for curl commands.
# Note:
#   - We implement the functionality ourselves to avoid dependencies on new packages.
#   - We encode a superset of the characters defined in the specification, which is explicitly
#     allowed: https://www.ietf.org/rfc/rfc1738.txt
url_encode () {
    local input=$1
    for (( i=0; i<${#input}; i++ )); do
        local c=${input:$i:1}
        case $c in
            [a-zA-Z0-9-_\.\!\*]) printf "$c" ;;
            *) printf '%%%02X' "'$c"
        esac
    done
}

JVM_URL_BASE_PATH=$DELEGATE_STORAGE_URL
if [[ $DEPLOY_MODE == "KUBERNETES" ]]; then
  JVM_URL_BASE_PATH=$JVM_URL_BASE_PATH/public/shared
fi

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

if [[ $KUBERNETES_SERVICE_HOST != "" ]]; then
  if [[ $NO_PROXY == "" ]]; then
    export NO_PROXY=$KUBERNETES_SERVICE_HOST
  else
    export NO_PROXY="$NO_PROXY,$KUBERNETES_SERVICE_HOST"
  fi
fi

DOCKER_PROXY_SECRET_FILE="/run/secrets/proxy.config"
if [ "$DELEGATE_TYPE" == "DOCKER" ] && [ -e "$DOCKER_PROXY_SECRET_FILE" ]; then
  echo "Docker delegate: copy proxy config mounted at ""$DOCKER_PROXY_SECRET_FILE"
  cp "$DOCKER_PROXY_SECRET_FILE" 'proxy.config'
fi

if [ ! -e proxy.config ]; then
  echo "PROXY_HOST='$PROXY_HOST'" > proxy.config
  echo "PROXY_PORT='$PROXY_PORT'" >> proxy.config
  echo "PROXY_SCHEME='$PROXY_SCHEME'" >> proxy.config
  echo "PROXY_USER='$PROXY_USER'" >> proxy.config
  echo "PROXY_PASSWORD='${PROXY_PASSWORD//"'"/"'\\''"}'" >> proxy.config
  echo "NO_PROXY='$NO_PROXY'" >> proxy.config
  echo "PROXY_MANAGER='${PROXY_MANAGER:-true}'" >> proxy.config
fi

source proxy.config
if [[ $PROXY_HOST != "" ]]; then
  echo "Using $PROXY_SCHEME proxy $PROXY_HOST:$PROXY_PORT"
  if [[ $PROXY_USER != "" ]]; then
    export PROXY_USER
    export PROXY_PASSWORD
    echo "using proxy auth config"
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
  HOST_AND_PORT_ARRAY=(${MANAGER_HOST_AND_PORT//:/ })
  MANAGER_HOST="${HOST_AND_PORT_ARRAY[1]}"
  MANAGER_HOST="${MANAGER_HOST:2}"
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

echo $PROXY_SYS_PROPS

if [ ! -z "$INIT_SCRIPT" ]; then
  echo "#!/bin/bash -e" > init.sh
  echo "$INIT_SCRIPT" >> init.sh
fi

if [ -e init.sh ]; then
    echo "Starting initialization script for delegate"
    CURRENT_WORKING_DIRECTORY=$(pwd)
    source ./init.sh
    #if user does set -e, then revert that
    set +e
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

ACCOUNT_STATUS=$(curl $MANAGER_PROXY_CURL -ks $MANAGER_HOST_AND_PORT/api/account/$ACCOUNT_ID/status | cut -d ":" -f 3 | cut -d "," -f 1 | cut -d "\"" -f 2)
if [[ $ACCOUNT_STATUS == "DELETED" ]]; then
  rm -rf *
  touch __deleted__
  while true; do sleep 60s; done
fi

DESIRED_VERSION=$HELM_DESIRED_VERSION
if [[ $DESIRED_VERSION != "" ]]; then
  export DESIRED_VERSION
  echo "Installing Helm $DESIRED_VERSION ..."
  curl $PROXY_CURL -#k https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get | bash
  helm init --client-only
fi

WATCHER_CURRENT_VERSION=$(jar_app_version watcher.jar)
echo "The current watcher version is $WATCHER_CURRENT_VERSION"

WATCHER_VERSION=$(echo $WATCHER_CURRENT_VERSION | cut -d "." -f3)

echo "using JRE11 with watcher $WATCHER_VERSION"
JRE_DIR="jdk-11.0.19+7-jre"
JVM_URL=$JVM_URL_BASE_PATH/jre/openjdk-11.0.19+7/OpenJDK11U-jre_x64_linux_hotspot_11.0.19+7.tar.gz

JRE_BINARY=$JRE_DIR/bin/java

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

echo "accountId: $ACCOUNT_ID" > config-watcher.yml
# delegateToken is a replacement of accountSecret. There is a possibility where pod is running with older yaml,
# where ACCOUNT_SECRET is present in env variable, prefer using ACCOUNT_SECRET in those scenarios.
if [ ! -e $ACCOUNT_SECRET ]; then
  echo "delegateToken: $ACCOUNT_SECRET" >> config-watcher.yml
else
  echo "delegateToken: $DELEGATE_TOKEN" >> config-watcher.yml
fi

echo "managerUrl: $MANAGER_HOST_AND_PORT/api/" >> config-watcher.yml
echo "doUpgrade: true" >> config-watcher.yml
echo "upgradeCheckLocation: $WATCHER_STORAGE_URL/$WATCHER_CHECK_LOCATION" >> config-watcher.yml
echo "upgradeCheckIntervalSeconds: 1200" >> config-watcher.yml
echo "delegateCheckLocation: $DELEGATE_STORAGE_URL/$DELEGATE_CHECK_LOCATION" >> config-watcher.yml

rm -f -- *.bak

export HOSTNAME
export CAPSULE_CACHE_DIR="$DIR/.cache"

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
fi
