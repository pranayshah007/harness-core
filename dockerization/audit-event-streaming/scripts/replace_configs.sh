#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/audit-event-streaming-config.yml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

write_mongo_params() {
  IFS='&' read -ra PARAMS <<< "$2"
  for PARAM_PAIR in "${PARAMS[@]}"; do
    NAME=$(cut -d= -f 1 <<< "$PARAM_PAIR")
    VALUE=$(cut -d= -f 2 <<< "$PARAM_PAIR")
    export VALUE; export ARG1=$1; export NAME; yq -i '.env(ARG1).params.env(NAME)=env(VALUE)' $CONFIG_FILE
  done
}

write_mongo_hosts_and_ports() {
  IFS=',' read -ra HOST_AND_PORT <<< "$2"
  for INDEX in "${!HOST_AND_PORT[@]}"; do
    HOST=$(cut -d: -f 1 <<< "${HOST_AND_PORT[$INDEX]}")
    PORT=$(cut -d: -f 2 -s <<< "${HOST_AND_PORT[$INDEX]}")

    export HOST; export ARG1=$1; export INDEX; yq -i '.env(ARG1).[env(INDEX)].host=env(HOST)' $CONFIG_FILE
    if [[ "" != "$PORT" ]]; then
      export PORT; export ARG1=$1; export INDEX; yq -i '.env(ARG1).[env(INDEX)].port=env(PORT)' $CONFIG_FILE
    fi
  done
}

echo "MONGO_URI="$MONGO_URI

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI; yq -i '.auditDbConfig.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_MAX_DOCUMENT_LIMIT" ]]; then
  export MONGO_MAX_DOCUMENT_LIMIT; yq -i '.auditDbConfig.maxDocumentsToBeFetched=env(MONGO_MAX_DOCUMENT_LIMIT)' $CONFIG_FILE
fi


if [[ "" != "$EVENT_COLLECTION_BATCH_JOB_CRON" ]]; then
  export EVENT_COLLECTION_BATCH_JOB_CRON; yq -i '.jobCommonConfig.eventCollectionBatchJob.cron=env(EVENT_COLLECTION_BATCH_JOB_CRON)' $CONFIG_FILE
fi

if [[ "" != "$BATCH_CURSOR_SIZE" ]]; then
  export BATCH_CURSOR_SIZE; yq -i '.jobCommonConfig.batchConfig.cursorBatchSize=env(BATCH_CURSOR_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$BATCH_LIMIT" ]]; then
  export BATCH_LIMIT; yq -i '.jobCommonConfig.batchConfig.limit=env(BATCH_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$BATCH_MAX_RETRIES" ]]; then
  export BATCH_MAX_RETRIES; yq -i '.jobCommonConfig.batchConfig.maxRetries=env(BATCH_MAX_RETRIES)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_CLIENT_BASEURL" ]]; then
  export AUDIT_CLIENT_BASEURL; yq -i '.auditClientConfig.baseUrl=env(AUDIT_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_CLIENT_BASEURL" ]]; then
  export NG_MANAGER_CLIENT_BASEURL; yq -i '.ngManagerClientConfig.baseUrl=env(NG_MANAGER_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$GRPC_MANAGER_TARGET" ]]; then
  export GRPC_MANAGER_TARGET; yq -i '.delegateServiceGrpcConfig.target=env(GRPC_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$GRPC_MANAGER_AUTHORITY" ]]; then
  export GRPC_MANAGER_AUTHORITY; yq -i '.delegateServiceGrpcConfig.authority=env(GRPC_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$PLATFORM_SECRET" ]]; then
  export PLATFORM_SECRET; yq -i '.serviceSecrets.platformServiceSecret=env(PLATFORM_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.serviceSecrets.ngManagerServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi
