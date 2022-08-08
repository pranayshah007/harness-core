#!/usr/bin/env bash
# Copyright 2020 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -e

INFRA_PROPERTY_FILE=inframapping.properties
CONFIG_PROPERTY_FILE=config.properties

source utils.sh

if [[ -z $1 ]]; then
   runtime_dir=$HOME/harness_runtime
   echo "No runtime directory supplied in argument, will default to using the $HOME directory, value=$runtime_dir"
else
  runtime_dir=$1
  echo "Using runtime directory ${runtime_dir}"
fi

if command -v getenforce &> /dev/null; then
   if getenforce | grep -q Enforcing; then
       echo "SE Linux is enabled, please disable or set to permissive mode."
       exit 1
   fi
fi

mkdir -p $runtime_dir


JAVA_HOME=$runtime_dir
MANAGER_DIR=$runtime_dir/manager
VERIFICATION_DIR=$runtime_dir/verification
UI_DIR=$runtime_dir/ui
LE_DIR=$runtime_dir/le
java=$JAVA_HOME/jre/bin/java
NGINX_DIR=$runtime_dir/nginx
export MEMORY=1024
export JAVA_OPTS_MANAGER="-Xms${MEMORY}m -Xmx${MEMORY}m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:managergclogfilename.gc -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8"
export JAVA_OPTS_VERIFICATION="-Xms${MEMORY}m -Xmx${MEMORY}m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:verificationgclogfilename.gc -XX:+UseParallelGC -XX:MaxGCPauseMillis=500 -Dfile.encoding=UTF-8"
export LICENSE_INFO=$(getProperty $CONFIG_PROPERTY_FILE licenseInfo)
export learning_env="on_prem"
DEPLOY_MODE=ONPREM
export DEPLOY_MODE

function replaceconfigs(){
    mongoUrl=$(getProperty "$runtime_dir/savedState" "mongoUrl")
    loadBalancerUrl=$(getProperty "$runtime_dir/savedState" "loadBalancerUrl")
    API_URL=$loadBalancerUrl
    ALLOWED_ORIGINS=$loadBalancerUrl
    DELEGATE_METADATA_URL=$loadBalancerUrl/storage/wingsdelegates/delegateprod.txt
    FEATURES=$(getProperty "config/manager/manager.properties" "FEATURES")
    jre_version=$(getProperty "config/manager/manager.properties" "jre_version")
    UI_SERVER_URL=$loadBalancerUrl
    CAPSULE_JAR=$(getProperty "config/manager/manager.properties" "CAPSULE_JAR")
    MEMORY=$(getProperty "config/manager/manager.properties" "MEMORY")
    LOGGING_LEVEL=$(getProperty "config/manager/manager.properties" "LOGGING_LEVEL")
    WATCHER_METADATA_URL=$loadBalancerUrl/storage/wingswatchers/watcherprod.txt
    HAZELCAST_PORT=$(getProperty "config/manager/manager.properties" "HAZELCAST_PORT")
    SKIP_LOGS=true

    jwtPasswordSecret=$(getProperty "$runtime_dir/savedState" "jwtPasswordSecret")
    jwtExternalServiceSecret=$(getProperty "$runtime_dir/savedState" "jwtExternalServiceSecret")
    jwtZendeskSecret=$(getProperty "$runtime_dir/savedState" "jwtZendeskSecret")
    jwtMultiAuthSecret=$(getProperty "$runtime_dir/savedState" "jwtMultiAuthSecret")
    jwtSsoRedirectSecret=$(getProperty "$runtime_dir/savedState" "jwtSsoRedirectSecret")
    jwtAuthSecret=$(getProperty "$runtime_dir/savedState" "jwtAuthSecret")
    managerPort=$(getProperty "$runtime_dir/savedState" "managerPort")
    verificationPort=$(getProperty "$runtime_dir/savedState" "verificationPort")
    timescaleDbUrl=$(getProperty "$runtime_dir/savedState" "timescaleDbUrl")
    timescaleDbUserName=$(getProperty "$runtime_dir/savedState" "timescaleDbUserName")
    timescaleDbPassword=$(getProperty "$runtime_dir/savedState" "timescaleDbPassword")
    learningengine_secret=$(getProperty "$runtime_dir/savedState" "learningengine_secret")
    host1=$(getProperty "$runtime_dir/savedState" "host1")
    NGINX_PORT=$(getProperty $runtime_dir/savedState "NGINX_PORT")


    if [[ ! -z "$LOGGING_LEVEL" ]]; then
        sed -i "s|level: INFO|level: ${LOGGING_LEVEL}|" $MANAGER_DIR/config.yml
    fi
    sed -i "s|type: h2|type: http|" $MANAGER_DIR/config.yml
    sed -i "s|port: 9090|port: $managerPort|" $MANAGER_DIR/config.yml

    sed -i 's|keyStorePath: keystore.jks||' $MANAGER_DIR/config.yml
    sed -i 's|keyStorePassword: password||' $MANAGER_DIR/config.yml
    sed -i "s|trustStorePath: \${JAVA_HOME}/lib/security/cacerts||" $MANAGER_DIR/config.yml
    sed -i 's|certAlias: localhost||' $MANAGER_DIR/config.yml
    sed -i 's|validateCerts: false||' $MANAGER_DIR/config.yml

    sed -i 's|keyFilePath: key.pem||' $MANAGER_DIR/config.yml
    sed -i 's|certFilePath: cert.pem||' $MANAGER_DIR/config.yml
    sed -i 's|secure: true||' $MANAGER_DIR/config.yml


    if [[ ! -z "$UI_SERVER_URL" ]]; then
        sed -i "s|url: https://localhost:8000|url: ${UI_SERVER_URL}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$ALLOWED_ORIGINS" ]]; then
        sed -i "s|allowedOrigins: http://localhost:8000|allowedOrigins: ${ALLOWED_ORIGINS}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$MONGO_URI" ]]; then
        sed -i "s|uri: mongodb://localhost:27017/harness|uri: ${mongoUrl}|" $MANAGER_DIR/config.yml
    fi

    sed -i "s|9a3e6eac4dcdbdc41a93ca99100537df||" $MANAGER_DIR/config.yml

    if [[ ! -z "$WATCHER_METADATA_URL" ]]; then
        sed -i "s|watcherMetadataUrl:.*|watcherMetadataUrl: ${WATCHER_METADATA_URL}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$DELEGATE_METADATA_URL" ]]; then
        sed -i "s|delegateMetadataUrl:.*|delegateMetadataUrl: ${DELEGATE_METADATA_URL}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$API_URL" ]]; then
        sed -i "s|http://localhost:8080|${API_URL}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$DEPLOY_MODE" ]]; then
        sed -i "s|deployMode: AWS|deployMode: ${DEPLOY_MODE}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$jwtPasswordSecret" ]]; then
        sed -i "s|a8SGF1CQMHN6pnCJgz32kLn1tebrXnw6MtWto8xI|${jwtPasswordSecret}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$jwtExternalServiceSecret" ]]; then
        sed -i "s|nhUmut2NMcUnsR01OgOz0e51MZ51AqUwrOATJ3fJ|${jwtExternalServiceSecret}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$jwtZendeskSecret" ]]; then
        sed -i "s|RdL7j9ZdCz6TVSHO7obJRS6ywYLJjH8tdfPP39i4MbevKjVo|${jwtZendeskSecret}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$jwtMultiAuthSecret" ]]; then
        sed -i "s|5E1YekVGldTSS5Kt0GHlyWrJ6fJHmee9nXSBssefAWSOgdMwAvvbvJalnYENZ0H0EealN0CxHh34gUCN|${jwtMultiAuthSecret}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$jwtSsoRedirectSecret" ]]; then
        sed -i "s|qY4GXZAlPJQPEV8JCPTNhgmDmnHZSAgorzGxvOY03Xptr8N9xDfAYbwGohr2pCRLfFG69vBQaNpeTjcV|${jwtSsoRedirectSecret}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$jwtAuthSecret" ]]; then
        sed -i "s|dOkdsVqdRPPRJG31XU0qY4MPqmBBMk0PTAGIKM6O7TGqhjyxScIdJe80mwh5Yb5zF3KxYBHw6B3Lfzlq|${jwtAuthSecret}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$FEATURES" ]]; then
        sed -i "s|featuresEnabled:|featuresEnabled: ${FEATURES}|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$timescaleDbUrl" ]]; then
        sed -i "s|timescaledbUrl: \"\"|timescaledbUrl: $timescaleDbUrl|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$timescaleDbUserName" ]]; then
        sed -i "s|timescaledbUsername: \"\"|timescaledbUsername: $timescaleDbUserName|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$timescaleDbPassword" ]]; then
        sed -i "s|timescaledbPassword: \"\"|timescaledbPassword: $timescaleDbPassword|" $MANAGER_DIR/config.yml
    fi

    if [[ ! -z "$LOGGING_LEVEL" ]]; then
    sed -i "s|level: INFO|level: ${LOGGING_LEVEL}|" $VERIFICATION_DIR/verification-config.yml
    fi
    sed -i "s|type: h2|type: http|" $VERIFICATION_DIR/verification-config.yml

    sed -i 's|keyStorePath: keystore.jks||' $VERIFICATION_DIR/verification-config.yml
    sed -i 's|keyStorePassword: password||' $VERIFICATION_DIR/verification-config.yml
    sed -i "s|trustStorePath: \${JAVA_HOME}/lib/security/cacerts||" $VERIFICATION_DIR/verification-config.yml
    sed -i 's|certAlias: localhost||' $VERIFICATION_DIR/verification-config.yml
    sed -i 's|validateCerts: false||' $VERIFICATION_DIR/verification-config.yml
    sed -i "s|port: 7070|port: $verificationPort|" $VERIFICATION_DIR/verification-config.yml

    sed -i "s|managerUrl: https://localhost:9090/api/|managerUrl: ${API_URL}/api/|" $VERIFICATION_DIR/verification-config.yml

    sed -i "s|uri: mongodb://localhost:27017/harness|uri: ${mongoUrl}|" $VERIFICATION_DIR/verification-config.yml

    sed -i "s|<\!-- apiurl -->|<script>window.apiUrl = '$API_URL/api'</script>|" $UI_DIR/static/index.html
}

function disableJSFeatures() {

    UI_DISABLED_FEATURES_FILE="config/ui/disabledFeatures.properties"

    if [ -f "${UI_DISABLED_FEATURES_FILE}" ]
    then
      echo "${UI_DISABLED_FEATURES_FILE} found."

      while IFS='=' read -r key value
      do
        if [[ -n "${value}" ]];
        then
           echo "value for ${key} is ${value} hence corresponding feature will be disabled"
           sed -i "s|${key}|${value}|"  "$UI_DIR/static/index.html"
        else
            echo "value for ${key} is empty hence corresponding feature will not be disabled"
        fi

      done < "${UI_DISABLED_FEATURES_FILE}"
      unset IFS

    else
      echo "${UI_DISABLED_FEATURES_FILE} not found."
    fi
}

function stopAllServices(){
    if [[ `pkill -9 java` ]];then
        echo "All Services stopped"
    fi

    if [[ `pkill -9 python` ]];then
        echo "All Services stopped"
    fi
}

function startManager(){
    echo "###################### Starting Manager##################################"
    mkdir -p $MANAGER_DIR/logs
    $java $JAVA_OPTS_MANAGER -jar $MANAGER_DIR/rest-capsule.jar $MANAGER_DIR/config.yml > $MANAGER_DIR/logs/portal.log 2>&1 &
}

function startVerification(){
    echo "###################### Starting Verification ##################################"
    mkdir -p $VERIFICATION_DIR/logs
    $java $JAVA_OPTS_VERIFICATION -jar $VERIFICATION_DIR/verification-capsule.jar $VERIFICATION_DIR/verification-config.yml > $VERIFICATION_DIR/logs/verification.log 2>&1 &
}

function generateNginxScript(){
    mkdir -p $NGINX_DIR
    cp scripts/harness_nginx.conf $NGINX_DIR/
    echo $runtime_dir
    replace RUNTIME_DIR $runtime_dir harness_nginx.conf $NGINX_DIR
    replace MANAGER_PORT $managerPort harness_nginx.conf $NGINX_DIR
    replace VERIFICATION_PORT $verificationPort harness_nginx.conf $NGINX_DIR
    replace NGINX_PORT $NGINX_PORT harness_nginx.conf $NGINX_DIR

    if [[ ${newinstallation} == "true" ]];then
        echo "############Ngnix conf file is located at $NGINX_DIR, please add it to your nginx service and restart the nginx service########"
    else
        echo "Existing installation found, please make sure nginx is running with the config located at $NGINX_DIR"
    fi
}


function generateSeedData(){
   echo "########################Seeding mongo with seed data for first time install#################################"
   learningengine_secret=$1
   echo "Creating seed data script"
   mkdir -p output
   cp scripts/add_seed_data.js output/

   replace LEARNING_ENGINE_SECRET $learningengine_secret add_seed_data.js output
   mongo $MONGO_URI<output/add_seed_data.js

}

function checkIfMongoIsRunning(){
    SERVICE=mongo
    if ps ax | grep -v grep | grep $SERVICE > /dev/null
    then
        echo "$SERVICE service running, everything is fine"
    else
        echo "$SERVICE is not running"
        exit 1
    fi
}

function copyToRuntimeDirectory(){
   cp -R manager $runtime_dir/
   cp -R verification $runtime_dir/
   if [[ $(checkIfFileExists "$runtime_dir/jre") -eq 1 ]]; then
       cp -R jre* $runtime_dir/
       chmod +x $runtime_dir/jre/bin/*
   else
      echo "Not copying JRE files since already present"
   fi
   cp -R ui $runtime_dir/
   cp -R storage $runtime_dir/
   cp -R le $runtime_dir/
   echo "Copying to runtime directories completed"
}


function publishVersion(){
    DELEGATE_VERSION=$(getProperty "version.properties" "DELEGATE_VERSION")
    MONGO_URI=$(getProperty "inframapping.properties" "MONGO_URI")
    echo "Publishing version $DELEGATE_VERSION to database"
    cp scripts/publish_version.js output/
    replace VERSION 1.0.$DELEGATE_VERSION publish_version.js output
    #cat output/publish_version.js
    mongo "$MONGO_URI" < output/publish_version.js

}

function startLE(){
  le_port=$(getProperty "config/learning_engine/learning_engine.properties" "https_port")
  verificationPort=$(getProperty "config/verification/verification_service.properties" "verification_port")
  cd $LE_DIR/splunk_pyml
  mkdir -p $LE_DIR/logs
  touch $LE_DIR/logs/le.log
  ./run_le_onprem_vanilla.sh --https_port $le_port --server_url http://localhost:$verificationPort  --service_secret $learningengine_secret > $LE_DIR/logs/le.log 2>&1 &
  cd -
}

newinstallation=false
host1=$(getProperty $INFRA_PROPERTY_FILE "HOST1_IP_ADDRESS")

if [[ $(checkIfFileExists "$runtime_dir/savedState") -eq 1 ]]; then
    echo "No state found, creating a new savedState"
    newinstallation=true
    learningengine_secret=$(generateRandomStringOfLength 32)
    jwtPasswordSecret=$(generateRandomStringOfLength 80)
    jwtExternalServiceSecret=$(generateRandomStringOfLength 80)
    jwtZendeskSecret=$(generateRandomStringOfLength 80)
    jwtMultiAuthSecret=$(generateRandomStringOfLength 80)
    jwtSsoRedirectSecret=$(generateRandomStringOfLength 80)
    jwtAuthSecret=$(generateRandomStringOfLength 80)
    MONGO_URI=$(getProperty $INFRA_PROPERTY_FILE "MONGO_URI")
    NGINX_PORT=$(getProperty $INFRA_PROPERTY_FILE "NGINX_PORT")
    LOAD_BALANCER_URL=http://$(getProperty $INFRA_PROPERTY_FILE "HOST1_IP_ADDRESS"):$NGINX_PORT
    managerPort=$(getProperty "config/manager/manager.properties" "manager_port")
    verificationPort=$(getProperty "config/verification/verification_service.properties" "verification_port")
    timescaleDbUrl=$(getProperty $INFRA_PROPERTY_FILE "TIMESCALEDB_URL")
    timescaleDbUserName=$(getProperty $INFRA_PROPERTY_FILE "TIMESCALEDB_USERNAME")
    timescaleDbPassword=$(getProperty $INFRA_PROPERTY_FILE "TIMESCALEDB_PASSWORD")

    echo "learningengine_secret"=$learningengine_secret > "$runtime_dir/savedState"
    echo "jwtPasswordSecret"=$jwtPasswordSecret >> "$runtime_dir/savedState"
    echo "jwtExternalServiceSecret="$jwtExternalServiceSecret >> "$runtime_dir/savedState"
    echo "jwtZendeskSecret="$jwtZendeskSecret >> "$runtime_dir/savedState"
    echo "jwtMultiAuthSecret="$jwtMultiAuthSecret >> "$runtime_dir/savedState"
    echo "jwtSsoRedirectSecret="$jwtSsoRedirectSecret >> "$runtime_dir/savedState"
    echo "jwtAuthSecret="$jwtAuthSecret >> "$runtime_dir/savedState"
    echo "accountKey="$accountKey >> "$runtime_dir/savedState"
    echo "host1="$host1 >> "$runtime_dir/savedState"
    echo "mongoUrl="$MONGO_URI >> "$runtime_dir/savedState"
    echo "loadBalancerUrl="$LOAD_BALANCER_URL >> "$runtime_dir/savedState"
    echo "managerPort="$managerPort >> "$runtime_dir/savedState"
    echo "verificationPort="$verificationPort >> "$runtime_dir/savedState"
    echo "NGINX_PORT="$NGINX_PORT >> "$runtime_dir/savedState"
    echo "timescaleDbUrl="$timescaleDbUrl >> "$runtime_dir/savedState"
    echo "timescaleDbUserName="$timescaleDbUserName >> "$runtime_dir/savedState"
    echo "timescaleDbPassword="$timescaleDbPassword >> "$runtime_dir/savedState"


else
    echo "Reading configuration from the savedState in ${runtime_dir}"
fi



checkIfMongoIsRunning
stopAllServices
copyToRuntimeDirectory
replaceconfigs
disableJSFeatures
startManager
startVerification
startLE
generateNginxScript
publishVersion

if [[ ${newinstallation} == "true" ]];then
       generateSeedData $learningengine_secret
else
   echo "Not seeding Mongo,existing installation found "
fi
