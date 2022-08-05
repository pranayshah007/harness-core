#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml

yq 'del(.'server.applicationConnectors.(type==https)')' $CONFIG_FILE

yq -i '.server.adminConnectors="[]"' $CONFIG_FILE

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq 'del(.'logging.appenders.(type==console)')' $CONFIG_FILE
  yq -i '.'logging.appenders.(type==gke-console).stackdriverLogEnabled'="true"' $CONFIG_FILE
else
  yq 'del(.'logging.appenders.(type==gke-console)')' $CONFIG_FILE
fi

if [[ "" != "$LOGGING_LEVEL" ]]; then
    yq -i '.logging.level="$LOGGING_LEVEL"' $CONFIG_FILE
fi

if [[ "" != "$LOGGERS" ]]; then
  IFS=',' read -ra LOGGER_ITEMS <<< "$LOGGERS"
  for ITEM in "${LOGGER_ITEMS[@]}"; do
    LOGGER=`echo $ITEM | awk -F= '{print $1}'`
    LOGGER_LEVEL=`echo $ITEM | awk -F= '{print $2}'`
    yq -i '.logging.loggers.[$LOGGER]="${LOGGER_LEVEL}"' $CONFIG_FILE
  done
fi

if [[ "" != "$SERVER_PORT" ]]; then
  yq -i '.server.applicationConnectors[0].port="$SERVER_PORT"' $CONFIG_FILE
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  yq -i '.server.maxThreads="$SERVER_MAX_THREADS"' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq -i '.eventsConfig.redis.sentinelUrls.[$INDEX]="${REDIS_SENTINEL_URL}"' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq -i '.redisLockConfig.sentinelUrls.[$INDEX]="${REDIS_SENTINEL_URL}"' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  IFS=',' read -ra ALLOWED_ORIGINS <<< "$ALLOWED_ORIGINS"
  INDEX=0
  for ALLOWED_URL in "${ALLOWED_ORIGINS[@]}"; do
    yq -i '.allowedOrigins.[$INDEX]="${ALLOWED_URL}"' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi
