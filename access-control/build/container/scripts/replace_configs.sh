#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml

yq -i 'del(.server.applicationConnectors | select(.type == https))' $CONFIG_FILE

yq -i '.server.adminConnectors=[]' $CONFIG_FILE

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders | select(.type == console))' $CONFIG_FILE
  yq -i '(.logging.appenders | select(.type == gke-console) | .stackdriverLogEnabled) = "true"' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders | select(.type == gke-console))' $CONFIG_FILE
fi

if [[ "" != "$LOGGING_LEVEL" ]]; then
    yq -i '.logging.level="env(LOGGING_LEVEL)"' $CONFIG_FILE
fi

if [[ "" != "$LOGGERS" ]]; then
  IFS=',' read -ra LOGGER_ITEMS <<< "$LOGGERS"
  for ITEM in "${LOGGER_ITEMS[@]}"; do
    LOGGER=`echo $ITEM | awk -F= '{print $1}'`
    LOGGER_LEVEL=`echo $ITEM | awk -F= '{print $2}'`
    yq -i '.logging.loggers.env(LOGGER)="env(LOGGER_LEVEL)"' $CONFIG_FILE
  done
fi

if [[ "" != "$SERVER_PORT" ]]; then
  yq -i '.server.applicationConnectors[0].port="env(SERVER_PORT)"' $CONFIG_FILE
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq -i '.server.maxThreads="env(SERVER_MAX_THREADS)"' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq -i '.eventsConfig.redis.sentinelUrls.env(INDEX)="env(REDIS_SENTINEL_URL)"' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq -i '.redisLockConfig.sentinelUrls.env(INDEX)="env(REDIS_SENTINEL_URL)"' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  IFS=',' read -ra ALLOWED_ORIGINS <<< "$ALLOWED_ORIGINS"
  INDEX=0
  for ALLOWED_URL in "${ALLOWED_ORIGINS[@]}"; do
    yq -i '.allowedOrigins.env(INDEX)="env(ALLOWED_URL)"' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi
