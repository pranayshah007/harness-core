#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

ACCOUNT_PROPERTY_FILE=accountdetails.properties
INFRA_PROPERTY_FILE=inframapping.properties
CONFIG_PROPERTY_FILE=config.properties
VERSION_PROPERTY_FILE=version.properties

source ./utils.sh

if [[ -z $1 ]]; then
  runtime_dir=$HOME/harness_runtime
  echo "No runtime directory supplied in argument, will default to the home directory, value=$runtime_dir"
else
  runtime_dir=$1
  echo "Using runtime directory $runtime_dir"
fi

echo "# Reading account details from $ACCOUNT_PROPERTY_FILE"
accountName=$(getProperty "$ACCOUNT_PROPERTY_FILE" "AccountName")
companyName=$(getProperty $ACCOUNT_PROPERTY_FILE "CompanyName")
adminEmail=$(getProperty $ACCOUNT_PROPERTY_FILE "AdminEmail")

echo "Reading Infra mapping from $INFRA_PROPERTY_FILE"
host1=$(getProperty "$INFRA_PROPERTY_FILE" "HOST1_IP_ADDRESS")

echo "Reading config mapping from $CONFIG_PROPERTY_FILE"
mongodbUserName=$(getProperty "$CONFIG_PROPERTY_FILE" "mongodbUserName" | base64 --decode)
mongodbPassword=$(getProperty "$CONFIG_PROPERTY_FILE" "mongodbPassword" | base64 --decode)
licenseInfo=$(getProperty "$CONFIG_PROPERTY_FILE" "licenseInfo")

echo "#######Account details start #############"
echo "AccountName="$accountName
echo "CompanyName="$companyName
echo "AdminEmail="$adminEmail
echo "#######Account details end #############"

printf "\n"

echo "#######Infrastructure details start #############"
echo "host1="$host1
echo "#######Infrastructure details end #############"
printf "\n"

rm -rf config
mkdir -p $runtime_dir
cp -Rf config_template config

newinstallation=false

if [[ $(checkIfFileExists "$runtime_dir/savedState") -eq 1 ]]; then
  echo "No state found, creating a new savedState"
  newinstallation=true
  account_secret=$(generateRandomString)
  jwtPasswordSecret=$(generateRandomStringOfLength 80)
  jwtExternalServiceSecret=$(generateRandomStringOfLength 80)
  jwtZendeskSecret=$(generateRandomStringOfLength 80)
  jwtMultiAuthSecret=$(generateRandomStringOfLength 80)
  jwtSsoRedirectSecret=$(generateRandomStringOfLength 80)
  accountKey=$(generateRandomStringOfLength 22)

  echo "account_secret"=$account_secret >>"$runtime_dir/savedState"
  echo "jwtPasswordSecret"=$jwtPasswordSecret >>"$runtime_dir/savedState"
  echo "jwtExternalServiceSecret="$jwtExternalServiceSecret >>"$runtime_dir/savedState"
  echo "jwtZendeskSecret="$jwtZendeskSecret >>"$runtime_dir/savedState"
  echo "jwtMultiAuthSecret="$jwtMultiAuthSecret >>"$runtime_dir/savedState"
  echo "jwtSsoRedirectSecret="$jwtSsoRedirectSecret >>"$runtime_dir/savedState"
  echo "accountKey="$accountKey >>"$runtime_dir/savedState"
  echo "host1="$host1 >>"$runtime_dir/savedState"
  echo "accountName="$accountName >>"$runtime_dir/savedState"
  echo "companyName="$companyName >>"$runtime_dir/savedState"
  echo "adminEmail="$adminEmail >>"$runtime_dir/savedState"
else
  echo "Reading configuration from the savedState in ${runtime_dir}"
fi

learningengine_secret=$(getProperty "$runtime_dir/savedState" "learningengine_secret")
account_secret=$(getProperty "$runtime_dir/savedState" "account_secret")
jwtPasswordSecret=$(getProperty "$runtime_dir/savedState" "jwtPasswordSecret")
jwtExternalServiceSecret=$(getProperty "$runtime_dir/savedState" "jwtExternalServiceSecret")
jwtZendeskSecret=$(getProperty "$runtime_dir/savedState" "jwtZendeskSecret")
jwtMultiAuthSecret=$(getProperty "$runtime_dir/savedState" "jwtMultiAuthSecret")
jwtSsoRedirectSecret=$(getProperty "$runtime_dir/savedState" "jwtSsoRedirectSecret")
accountKey=$(getProperty "$runtime_dir/savedState" "accountKey")
host1=$(getProperty "$runtime_dir/savedState" "host1")
accountKey=$(getProperty "$runtime_dir/savedState" "accountKey")
sshUser=$(getProperty "$runtime_dir/savedState" "sshUser")
sshKeyPath=$(getProperty "$runtime_dir/savedState" "sshKeyPath")
accountName=$(getProperty "$runtime_dir/savedState" "accountName")
companyName=$(getProperty "$runtime_dir/savedState" "companyName")
adminEmail=$(getProperty "$runtime_dir/savedState" "adminEmail")

sanitizedhost1="$(echo $host1 | sed -e 's|\.|\\.|g')"

echo "#######MongoDB details start #############"

##MONGO related properties###
harness_db=$(getProperty "config_template/mongo/mongoconfig.properties" "harness_application_db")
mongodb_port=$(getProperty "config_template/mongo/mongoconfig.properties" "mongodb_port")
mongodb_data_dir=$(getProperty "config_template/mongo/mongoconfig.properties" "mongodb_data_dir")
mongodb_sys_log_file=$(getProperty "config_template/mongo/mongoconfig.properties" "mongodb_sys_log_file")
mongodb_sys_log_dir=$(getProperty "config_template/mongo/mongoconfig.properties" "mongodb_sys_log_dir")
replicaset_name=$(getProperty "config_template/mongo/mongoconfig.properties" "replicaset_name")
admin_db=$(getProperty "config_template/mongo/mongoconfig.properties" "admin_db")
admin_user_role=$(getProperty "config_template/mongo/mongoconfig.properties" "admin_user_role")

echo "HarnessDB="$harness_db
echo "MongoPort="$mongodb_port
echo "#######MongoDB details end #############"
printf "\n"

timescaledb_username=$(getProperty "config_template/timescale/timescale.properties" "TIMESCALE_USER")
timescale_db=$(getProperty "config_template/timescale/timescale.properties" "TIMESCALE_DB")
timescaledb_password=$(getProperty "config_template/timescale/timescale.properties" "TIMESCALE_PASSWORD")
timescaledb_port=$(getProperty "config_template/timescale/timescale.properties" "TIMESCALE_PORT")

echo "Created config folder from config_template"

$(replace "COMPANYNAME" "$companyName")
$(replace "ACCOUNTNAME" "$accountName")
$(replace "ACCOUNT_SECRET_KEY" "$account_secret")
$(replace "LEARNING_ENGINE_SECRET" "$learningengine_secret")
$(replace "kmpySmUISimoRrJL6NL73w" $accountKey)
$(replace "EMAIL" $adminEmail)
$(replace "HARNESSDB" $harness_db)
$(replace "MONGODB_PORT" $mongodb_port)

proxyPort=$(getProperty "config_template/proxy/proxyconfig.properties" "proxy_port")
managerport=$(getProperty "config_template/manager/manager.properties" "manager_port")
grpcPort=$(getProperty "config_template/manager/manager.properties" "GRPC_PORT")
uiport=$(getProperty "config_template/ui/ui.properties" "ui_port")
verificationport=$(getProperty "config_template/verification/verification_service.properties" "verification_port")
MANAGER1=$host1:$managerport
VERIFICATION1=$host1:$verificationport
UI1=$host1:$uiport
WWW_DIR_LOCATION=$runtime_dir/data/proxy/www
STORAGE_DIR_LOCATION=$WWW_DIR_LOCATION/data/storage
PROXY_VERSION=$(getProperty "version.properties" "PROXY_VERSION")
MONGO_VERSION=$(getProperty "version.properties" "MONGO_VERSION")
TIMESCALE_VERSION=$(getProperty "version.properties" "TIMESCALE_VERSION")
LOAD_BALANCER_URL=http://$host1:$proxyPort
MONGO_URI=mongodb://$mongodbUserName:$mongodbPassword@$host1:$mongodb_port/harness?authSource=admin
TIMESCALEDB_URI=jdbc:postgresql://$host1:$timescaledb_port/harness
mkdir -p $STORAGE_DIR_LOCATION

function createMongoFiles() {
  echo "##### Setting up directories for mongodb######## "
  chmod 666 config/mongo/mongod.conf
  chmod 666 config/mongo/add_first_user.js
  chmod 666 config/mongo/add_learning_engine_secret.js
  chmod 666 config/mongo/publish_version.js

  mkdir -p $runtime_dir/mongo/$mongodb_sys_log_dir
  mkdir -p $runtime_dir/mongo/$mongodb_data_dir
  touch $runtime_dir/mongo/$mongodb_sys_log_dir/$mongodb_sys_log_file
  echo "Creating file : "$runtime_dir/mongo/$mongodb_sys_log_dir/$mongodb_sys_log_file
  mv config/mongo/mongod.conf $runtime_dir/mongo
  mkdir -p $runtime_dir/mongo/scripts
  mv config/mongo/add_first_user.js $runtime_dir/mongo/scripts
  mv config/mongo/add_learning_engine_secret.js $runtime_dir/mongo/scripts
  mv config/mongo/publish_version.js $runtime_dir/mongo/scripts

  chown -R 999 $runtime_dir/mongo/*
  chmod 777 $runtime_dir/mongo/$mongodb_data_dir
}

function checkAndCreateMongoFiles() {
  if [ ! -d "$runtime_dir/mongo" ]; then
    createMongoFiles
  else
    echo "##### Mongo directory already present checking for files in it ######## "
    if [ "$(ls -A $runtime_dir/mongo)" ]; then
      echo "##### Mongo directory is non-empty hence skipping creation of mongo files ######## "
    else
      echo "##### Mongo direcotry is empty hence creating mongo files ######## "
      createMongoFiles
    fi
  fi
}

function setUpTimeScaleDB() {
  echo "#############################Setting up TimeScale DB ##########################################"
  printf "\n\n\n\n"
  docker run -d --security-opt=no-new-privileges --read-only --rm -p $timescaledb_port:5432 --name harness-timescaledb -v $runtime_dir/timescaledb/data:/var/lib/postgresql/data:Z -v /var/run/postgresql --tmpfs /tmp:rw -e POSTGRES_USER=$timescaledb_username -e POSTGRES_DB=$timescale_db -e POSTGRES_PASSWORD=$timescaledb_password timescale/timescaledb:$TIMESCALE_VERSION

  if [[ $(checkDockerImageRunning "harness-timescaledb") -eq 1 ]]; then
    echo "TimescaleDB is not running"
  fi

}

function setUpMongoDBFirstTime() {
  echo "################################Starting up MongoDB for First Time ################################"

  printf "\n\n\n\n"

  docker run -d --security-opt=no-new-privileges --read-only --rm -p $mongodb_port:$mongodb_port --name mongoContainer -v $runtime_dir/mongo/data/db:/data/db:Z -v $runtime_dir/mongo/scripts:/scripts:Z --tmpfs /var/run:rw --tmpfs /tmp:rw harness/mongo:$MONGO_VERSION --port $mongodb_port

  mongoContainerId=$(docker ps -q -f name=mongoContainer)

  if [[ "${mongoContainerId}" == "" ]]; then
    echo "MongoContainer did not start"
    exit 1
  else
    echo "MongoContainer started with ID="$mongoContainerId
  fi

  echo "Sleeping for 5 seconds before proceeding"
  sleep 5

  docker exec mongoContainer mongo --port $mongodb_port admin --eval "db.createUser({user: '$mongodbUserName', pwd: '$mongodbPassword', roles:[{role:'$admin_user_role',db:'admin'}]});"

  echo "Stopping MongoDB.... will restart it again "

}

function setUpMongoDB() {

  echo "################################Starting up MongoDB################################"
  mv config/mongo/mongod.conf $runtime_dir/mongo
  sed -i -e "s+LOG_PATH+${runtime_dir}/mongo/log/mongod.log+g" $runtime_dir/mongo/mongod.conf
  mkdir -p $runtime_dir/mongo/log
  touch $runtime_dir/mongo/log/mongod.log
  chown -R 999 $runtime_dir/mongo/*
  chmod -R 777 $runtime_dir/mongo

  docker run -d --security-opt=no-new-privileges --read-only --rm -p $mongodb_port:$mongodb_port --name mongoContainer -v "$runtime_dir/mongo/mongod.conf":/etc/mongod.conf:Z -v $runtime_dir/mongo/data/db:/data/db:Z -v $runtime_dir/mongo/scripts:/scripts:Z --tmpfs $runtime_dir/mongo/log:rw --tmpfs /var/run:rw --tmpfs /tmp:rw harness/mongo:$MONGO_VERSION -f /etc/mongod.conf

  mongoContainerId=$(docker ps -q -f name=mongoContainer)

  if [[ "${mongoContainerId}" == "" ]]; then
    echo "MongoContainer did not start"
    exit 1
  else
    echo "MongoContainer started with ID="$mongoContainerId
  fi

  echo "Sleeping for 5 seconds before proceeding"
  sleep 5

}

function printSpaceWarning() {
  percent=$(df -P . | awk 'NR > 1 {print $5+0}')
  if [[ "$((percent))" -gt 90 ]]; then
    echo "WARNING: More than 90 percent of disk space used. Clean up some space to avoid interruptions."
  fi
}

function checkSpaceMoreThan() {
  available=$(df -P . | awk 'NR > 1 {print $4+0}')
  expected=$1

  if [[ "$(($available))" -gt expected ]]; then
    echo 0
  else
    echo -1
  fi
}

function seedMongoDB() {
  echo "################################Seeding MongoDB with data ################################"

  docker exec mongoContainer bash -c "mongo  mongodb://$mongodbUserName:$mongodbPassword@$host1:$mongodb_port/?authSource=admin < /scripts/add_first_user.js"

  docker exec mongoContainer bash -c "mongo  mongodb://$mongodbUserName:$mongodbPassword@$host1:$mongodb_port/?authSource=admin < /scripts/add_learning_engine_secret.js"

  stopDockerContainer "mongoContainer"

}

function populateEnvironmentVariablesFromMongo() {
  echo "################################ Populating  learning engine secret from db ################################"

  echo $MANAGER1
  until $(curl --silent --output /dev/null --fail http://$MANAGER1/api/version); do
    echo "Manger not up yet. Sleeping for 30 seconds"
    sleep 30s
  done

  learningengine_secret=$(docker exec mongoContainer bash -c "mongo mongodb://$mongodbUserName:$mongodbPassword@$host1:$mongodb_port/harness?authSource=admin --quiet --eval \"db.getCollection('serviceSecrets').findOne({'serviceType' : 'LEARNING_ENGINE'}, {'serviceSecret' : 1, _id: 0}).serviceSecret\" ")
  echo "Learning engine secret "$learningengine_secret
  echo "learningengine_secret"=$learningengine_secret >>"$runtime_dir/savedState"
}

function setUpProxy() {
  echo "################################ Setting up proxy ################################"

  docker run --security-opt=no-new-privileges --read-only -d --name harness-proxy --rm -p $proxyPort:7143 -e MANAGER1=$MANAGER1 -e VERIFICATION1=$VERIFICATION1 -e UI1=$UI1 -v $WWW_DIR_LOCATION:/www:Z -v /etc/nginx/conf.d -v /var/run --tmpfs /var/cache/nginx:rw --tmpfs /tmp:rw harness/proxy-signed:$PROXY_VERSION
  sleep 5

  if [[ $(checkDockerImageRunning "harness-proxy") -eq 1 ]]; then
    echo "Harness Proxy is not running"
  fi
}

function setupManager() {
  echo "################################ Setting up Manager ################################"

  ALLOWED_ORIGINS=$LOAD_BALANCER_URL
  DELEGATE_METADATA_URL=$LOAD_BALANCER_URL/storage/wingsdelegates/delegateprod.txt
  SERVER_PORT=$managerport
  TCP_HOSTS_DETAILS=127.0.0.1:$(getProperty "config_template/manager/manager.properties" "HAZELCAST_PORT")
  FEATURES=$(getProperty "config_template/manager/manager.properties" "FEATURES")
  jre_version=$(getProperty "config_template/manager/manager.properties" "jre_version")
  UI_SERVER_URL=$LOAD_BALANCER_URL
  CAPSULE_JAR=$(getProperty "config_template/manager/manager.properties" "CAPSULE_JAR")
  MEMORY=$(getProperty "config_template/manager/manager.properties" "MEMORY")
  LOGGING_LEVEL=$(getProperty "config_template/manager/manager.properties" "LOGGING_LEVEL")
  DEPLOY_MODE=ONPREM
  WATCHER_METADATA_URL=$LOAD_BALANCER_URL/storage/wingswatchers/watcherprod.txt
  HAZELCAST_PORT=$(getProperty "config_template/manager/manager.properties" "HAZELCAST_PORT")
  managerVersion=$(getProperty "version.properties" "MANAGER_VERSION")
  DELEGATE_SERVICE_TARGET=127.0.0.1:$grpcPort
  DELEGATE_SERVICE_AUTHORITY=default-authority.harness.io

  #    echo $LOAD_BALANCER_URL
  #    echo $ALLOWED_ORIGINS
  #    echo $DELEGATE_METADATA_URL
  #    echo $MONGO_URI
  #    echo $SERVER_PORT
  #    echo $TCP_HOSTS_DETAILS
  #    echo $FEATURES
  #    echo $jre_version
  #    echo $UI_SERVER_URL
  #    echo $NEWRELIC_ENV
  #    echo $CAPSULE_JAR
  #    echo $NEWRELIC_LICENSE_KEY
  #    echo $MEMORY
  #    echo $LOGGING_LEVEL
  #    echo $DEPLOY_MODE

  mkdir -p $runtime_dir/manager/logs
  chmod -R 777 $runtime_dir/manager

  docker run -d --security-opt=no-new-privileges --read-only --rm -p $SERVER_PORT:$SERVER_PORT --name harnessManager -e LOGGING_LEVEL=$LOGGING_LEVEL -e MEMORY=$MEMORY -e WATCHER_METADATA_URL=$WATCHER_METADATA_URL -e LICENSE_INFO=$licenseInfo -e ALLOWED_ORIGINS=$ALLOWED_ORIGINS -e CAPSULE_JAR=$CAPSULE_JAR -e DELEGATE_METADATA_URL=$DELEGATE_METADATA_URL -e HZ_CLUSTER_NAME=docker-manager-onprem -e SERVER_PORT=$SERVER_PORT -e UI_SERVER_URL=$UI_SERVER_URL -e MONGO_URI="$MONGO_URI" -e DEPLOY_MODE=$DEPLOY_MODE -e TCP_HOSTS_DETAILS=$TCP_HOSTS_DETAILS -e CIDR=127.0.0.1 -e API_URL=$LOAD_BALANCER_URL -e HAZELCAST_PORT=$HAZELCAST_PORT -e jwtPasswordSecret=$jwtPasswordSecret -e jwtExternalServiceSecret=$jwtExternalServiceSecret -e jwtZendeskSecret=$jwtZendeskSecret -e jwtMultiAuthSecret=$jwtMultiAuthSecret -e jwtSsoRedirectSecret=$jwtSsoRedirectSecret -e FEATURES=$FEATURES -e SKIP_LOGS=true -e TIMESCALEDB_URI=$TIMESCALEDB_URI -e TIMESCALEDB_USERNAME=$timescaledb_username -e TIMESCALEDB_PASSWORD=$timescaledb_password -e DELEGATE_SERVICE_TARGET=$DELEGATE_SERVICE_TARGET -e DELEGATE_SERVICE_AUTHORITY=$DELEGATE_SERVICE_AUTHORITY -v $runtime_dir/manager/logs:/opt/harness/logs:Z -v /tmp -v /opt/harness harness/manager-signed:$managerVersion

  sleep 10

  if [[ $(checkDockerImageRunning "harnessManager") -eq 1 ]]; then
    echo "Harness Manager is not running"
  fi

}

function setUpVerificationService() {
  echo "################################ Setting up Verification Service ################################"
  verificationServiceVersion=$(getProperty "version.properties" "VERIFICATION_SERVICE_VERSION")
  env=$(getProperty "version.properties" "ENV")
  mkdir -p $runtime_dir/verification/logs
  chmod -R 777 $runtime_dir/verification

  docker run -d --security-opt=no-new-privileges --read-only --rm --name verificationService -e MANAGER_URL=$LOAD_BALANCER_URL/api/ -e MONGO_URI="$MONGO_URI" -e ENV=$env -e VERIFICATION_PORT=$verificationport -p $verificationport:$verificationport -v $runtime_dir/verification/logs:/opt/harness/logs:Z -v /tmp -v /opt/harness harness/verification-service-signed:$verificationServiceVersion
  if [[ $(checkDockerImageRunning "verificationService") -eq 1 ]]; then
    echo "Verification service is not running"
  fi

}

function backupMongo() {
  echo "################################ Taking Database Dump ################################"

  printSpaceWarning
  spaceAvailableOkay=$(checkSpaceMoreThan 5242880) # more than 5 Gigs
  if [[ "${spaceAvailableOkay}" -eq 0 ]]; then
    echo "[BACKUP] ✓ Enough space to backup"
  else
    df -Ph .
    echo "[BACKUP] ✘ Not enough space to backup. Skipping backup."
    return 1
  fi

  mongoContainerId=$(docker ps -q -f name=mongoContainer)

  if [[ "${mongoContainerId}" == "" ]]; then
    echo "[BACKUP] No running mongo container to backup."
  else
    CONTAINER_MONGODUMP_PATH="/data/db/backup"

    docker exec mongoContainer bash -c "mongodump --host localhost --port $mongodb_port --username $mongodbUserName --password $mongodbPassword -o $CONTAINER_MONGODUMP_PATH --quiet"

    if [[ $? -eq 0 ]]; then
      echo "[BACKUP] ✓ Backup Successful"
      ts=$(date +'%Y-%m-%d-%Hh-%Mm-%Ss')
      backupPath="$HOME/mongodump-backups/$ts"
      mkdir -p "$backupPath"

      echo "[BACKUP] Copying Backup to HOME directory. Source: ${runtime_dir}/mongo/data/db/backup . Destination: $backupPath"
      # ${runtime_dir}/mongo/data/db is mounted on to container
      cp -r ${runtime_dir}/mongo/data/db/backup "$backupPath"
    else
      echo "[BACKUP] ✘ Failed"
      exit 1
    fi

    if [[ "$(which realpath)" == "" || "$(which tac)" == "" ]]; then
      echo "[BACKUP] Could not clean up old backups in $HOME/mongodump-backups directory. Delete them manually."
    else
      echo "[BACKUP] Cleaning up old backups."

      # tac reverses the output
      # tail +3 selects lines _after_ first 2 lines, so we want to keep 2 most recent backups
      dirToDelete=$(ls -ltr "$HOME/mongodump-backups/" | grep -v "total" | awk '{print $NF}' | tac | tail -n +3 | head -1)
      while [[ ${dirToDelete} != "" ]]; do
        echo "  Removing: $HOME/mongodump-backups/${dirToDelete}"
        rm -rf "$HOME/mongodump-backups/${dirToDelete}/"
        dirToDelete=$(ls -ltr "$HOME/mongodump-backups/" | grep -v "total" | awk '{print $NF}' | tac | tail -n +3 | head -1)
      done

    fi
  fi

}

function setupUI() {
  echo "################################ Setting up UI ################################"
  ui_version=$(getProperty "version.properties" "UI_VERSION")
  UI_PORT=$(getProperty "config_template/ui/ui.properties" "ui_port")
  docker run -d --security-opt=no-new-privileges --read-only --name harness_ui -p $UI_PORT:8080 --rm -e API_URL="$LOAD_BALANCER_URL" -e HARNESS_ENABLE_EXTERNAL_SCRIPTS_PLACEHOLDER=false -v /tmp -v /opt/ui/static harness/ui-signed:$ui_version
  if [[ $(checkDockerImageRunning "harness_ui") -eq 1 ]]; then
    echo "Harness UI is not running"
  fi

}

function setUpLearningEngine() {
  echo "################################ Setting up Learning Engine ################################"
  learningEngineVersion=$(getProperty "version.properties" "LEARNING_ENGINE_VERSION")
  https_port=$(getProperty "config_template/learning_engine/learning_engine.properties" "https_port")
  docker run -d --security-opt=no-new-privileges --read-only --rm --name learningEngine -e learning_env=on_prem -e https_port=$https_port -e server_url=$LOAD_BALANCER_URL -e service_secret=$learningengine_secret -v /tmp -v /home/harness harness/learning-engine-onprem-signed:$learningEngineVersion
  if [[ $(checkDockerImageRunning "learningEngine") -eq 1 ]]; then
    echo "LearningEngine is not running"
  fi

}

function setupDelegateJars() {
  echo "################################ Setting up Delegate Jars ################################"

  DELEGATE_VERSION=$(getProperty "version.properties" "DELEGATE_VERSION")
  WATCHER_VERSION=$(getProperty "version.properties" "WATCHER_VERSION")

  mkdir -p $STORAGE_DIR_LOCATION/wingsdelegates/jre/8u191/
  mkdir -p $STORAGE_DIR_LOCATION/wingsdelegates/jre/openjdk-8u242/
  cp images/jre-8u191-*.gz $STORAGE_DIR_LOCATION/wingsdelegates/jre/8u191/
  cp images/jre_x64_*.gz $STORAGE_DIR_LOCATION/wingsdelegates/jre/openjdk-8u242/

  mkdir -p $STORAGE_DIR_LOCATION/wingsdelegates/tools/alpn/release/8.1.13.v20181017/
  cp images/alpn-boot-8.1.13.v20181017.jar $STORAGE_DIR_LOCATION/wingsdelegates/tools/alpn/release/8.1.13.v20181017/

  rm -rf ${STORAGE_DIR_LOCATION}/wingsdelegates/jobs/deploy-prod-delegate/*
  mkdir -p ${STORAGE_DIR_LOCATION}/wingsdelegates/jobs/deploy-prod-delegate/${DELEGATE_VERSION}
  cp images/delegate.jar ${STORAGE_DIR_LOCATION}/wingsdelegates/jobs/deploy-prod-delegate/${DELEGATE_VERSION}/

  echo "1.0.${DELEGATE_VERSION} jobs/deploy-prod-delegate/${DELEGATE_VERSION}/delegate.jar" >delegateprod.txt
  cp delegateprod.txt ${STORAGE_DIR_LOCATION}/wingsdelegates/
  rm -rf delegateprod.txt

  rm -rf ${STORAGE_DIR_LOCATION}/wingswatchers/jobs/deploy-prod-watcher/*
  mkdir -p ${STORAGE_DIR_LOCATION}/wingswatchers/jobs/deploy-prod-watcher/${WATCHER_VERSION}
  cp images/watcher.jar ${STORAGE_DIR_LOCATION}/wingswatchers/jobs/deploy-prod-watcher/${WATCHER_VERSION}/

  echo "1.0.${WATCHER_VERSION} jobs/deploy-prod-watcher/${WATCHER_VERSION}/watcher.jar" >watcherprod.txt
  cp watcherprod.txt ${STORAGE_DIR_LOCATION}/wingswatchers/
  rm -rf watcherprod.txt

}

function setupClientUtils() {
  echo "################################ Setting up Client Utils ################################"

  echo "Copying kubectl go-template helm chartmuseum tf-config-inspect oc and scm"

  for platform in linux darwin; do
    for kubectlversion in v1.13.2 v1.19.2; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/kubernetes-release/release/$kubectlversion/bin/${platform}/amd64/
      cp images/kubectl/${platform}/$kubectlversion/kubectl ${STORAGE_DIR_LOCATION}/harness-download/kubernetes-release/release/$kubectlversion/bin/${platform}/amd64/
    done

    for gotemplateversion in v0.4.2; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/snapshot-go-template/release/$gotemplateversion/bin/${platform}/amd64/
      cp images/go-template/${platform}/$gotemplateversion/go-template ${STORAGE_DIR_LOCATION}/harness-download/snapshot-go-template/release/$gotemplateversion/bin/${platform}/amd64/
    done

    for harnessPywinrmVersion in v0.1-dev v0.2-dev v0.3-dev v0.4-dev; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/snapshot-harness-pywinrm/release/$harnessPywinrmVersion/bin/${platform}/amd64/
      cp images/harness-pywinrm/${platform}/$harnessPywinrmVersion/harness-pywinrm ${STORAGE_DIR_LOCATION}/harness-download/snapshot-harness-pywinrm/release/$harnessPywinrmVersion/bin/${platform}/amd64/
    done

    for helmversion in v2.13.1 v3.1.2 v3.8.0; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-helm/release/$helmversion/bin/${platform}/amd64/
      cp images/helm/${platform}/$helmversion/helm ${STORAGE_DIR_LOCATION}/harness-download/harness-helm/release/$helmversion/bin/${platform}/amd64/
    done

    for chartmuseumversion in v0.8.2 v0.12.0; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-chartmuseum/release/$chartmuseumversion/bin/${platform}/amd64/
      cp images/chartmuseum/${platform}/$chartmuseumversion/chartmuseum ${STORAGE_DIR_LOCATION}/harness-download/harness-chartmuseum/release/$chartmuseumversion/bin/${platform}/amd64/
    done

    for tfConfigInspectVersion in v1.0 v1.1 v1.2; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-terraform-config-inspect/"$tfConfigInspectVersion"/${platform}/amd64/
      cp images/tf-config-inspect/${platform}/"$tfConfigInspectVersion"/terraform-config-inspect ${STORAGE_DIR_LOCATION}/harness-download/harness-terraform-config-inspect/"$tfConfigInspectVersion"/${platform}/amd64/
    done

    for ocversion in v4.2.16; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-oc/release/$ocversion/bin/${platform}/amd64/
      cp images/oc/${platform}/$ocversion/oc ${STORAGE_DIR_LOCATION}/harness-download/harness-oc/release/$ocversion/bin/${platform}/amd64/
    done

    for scmVersion in 91e2a39c; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-scm/release/$scmVersion/bin/${platform}/amd64/
      cp images/scm/${platform}/$scmVersion/scm ${STORAGE_DIR_LOCATION}/harness-download/harness-scm/release/$scmVersion/bin/${platform}/amd64/
    done

    for kustomizeVersion in v3.5.4 v4.0.0; do
      mkdir -p ${STORAGE_DIR_LOCATION}/harness-download/harness-kustomize/release/$kustomizeVersion/bin/${platform}/amd64/
      cp images/kustomize/${platform}/$kustomizeVersion/kustomize ${STORAGE_DIR_LOCATION}/harness-download/harness-kustomize/release/$kustomizeVersion/bin/${platform}/amd64/
    done
  done
}

function publishVersion() {
  VERSION=1.0.$(getProperty "version.properties" "DELEGATE_VERSION")
  docker exec mongoContainer bash -c "mongo  mongodb://$mongodbUserName:$mongodbPassword@$host1:$mongodb_port/$harness_db?authSource=admin --eval \"var version='$VERSION'\" /scripts/publish_version.js"
}

function loadDockerImages() {
  docker load --input images/proxy.tar
  docker load --input images/manager.tar
  docker load --input images/verification_service.tar
  docker load --input images/ui.tar
  docker load --input images/mongo.tar
  docker load --input images/learning_engine.tar
  docker load --input images/timescale.tar
}

function startUp() {
  loadDockerImages
  if [[ ${newinstallation} == "true" ]]; then
    setUpMongoDBFirstTime
    stopDockerContainer "mongoContainer"
  else
    echo "Not seeding Mongo, existing installation found "
  fi

  setUpMongoDB
  setUpTimeScaleDB
  setUpProxy
  setupManager

  if [[ ${newinstallation} == "true" ]]; then
    populateEnvironmentVariablesFromMongo
  fi

  setUpVerificationService
  setupUI
  setUpLearningEngine
  setupDelegateJars
  setupClientUtils
  publishVersion
}

function cleanupAfterStart() {
  echo "################################Cleaning up after start ################################"
  rm -rf config
}
upgrade42=false
if [[ ${newinstallation} == "false" ]]; then
  backupMongo
  ####checkMongoUpgrade
fi
stopContainers
startUp

if [[ ${upgrade42} == "true" ]]; then
  setCompatibility42
fi

cleanupAfterStart
echo "Server is running at http://${host1}:${proxyPort}"
