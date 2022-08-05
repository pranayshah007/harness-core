#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/event-service-config.yml

# Remove the TLS connector (as ingress terminates TLS)
yq 'del(.connectors[0])' $CONFIG_FILE

if [[ "" != "$MONGO_URI" ]]; then
  yq -i '.harness-mongo.uri="$MONGO_URI"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_READ_PREF_NAME" ]]; then
  yq -i '.harness-mongo.readPref.name="$MONGO_READ_PREF_NAME"' $CONFIG_FILE
fi

if [[ "" != "$MONGO_READ_PREF_TAGS" ]]; then
  IFS=',' read -ra TAG_ITEMS <<< "$MONGO_READ_PREF_TAGS"
  for ITEM in "${TAG_ITEMS[@]}"; do
    TAG_NAME=$(echo $ITEM | awk -F= '{print $1}')
    TAG_VALUE=$(echo $ITEM | awk -F= '{print $2}')
    yq -i '.harness-mongo.readPref.tagSet.[$TAG_NAME]="$TAG_VALUE"' $CONFIG_FILE
  done
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.harness-mongo.indexManagerMode="$MONGO_INDEX_MANAGER_MODE"' $CONFIG_FILE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq -i '.events-mongo.indexManagerMode="$EVEMTS_MONGO_INDEX_MANAGER_MODE"' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  yq -i '.events-mongo.uri="$EVENTS_MONGO_URI"' $CONFIG_FILE
fi
