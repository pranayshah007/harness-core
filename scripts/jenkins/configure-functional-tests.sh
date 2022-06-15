# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function replace_text() {
  search_string=$1
  replace_string=$2
  file_path=$3
  sed -i "s|$search_string|$replace_string|" $file_path
}

replace_text "uri: mongodb://localhost:27017/harness" "uri: ${MONGO_URI}" 360-cg-manager/config.yml
replace_text "disabledCaches: \[\]" "disabledCaches: \["userPermissionCache", "primary_userPermissionCache"\]" 360-cg-manager/config.yml
replace_text "uri: http://localhost:9200" "uri: ${ELASTICSEARCH_URI}" 360-cg-manager/config.yml
replace_text "indexSuffix: _default" "indexSuffix: ${ELASTICSEARCH_INDEX_SUFFIX}" 360-cg-manager/config.yml
replace_text "searchEnabled: false" "searchEnabled: true" 360-cg-manager/config.yml
replace_text "sentinel: false" "sentinel: true" 360-cg-manager/config.yml
replace_text "masterName: \"test\"" "masterName: \"${REDIS_MASTER_NAME}\"" 360-cg-manager/config.yml
replace_text "envNamespace: \"\"" "envNamespace: ${TIMESTAMP}" 360-cg-manager/config.yml
replace_text "\"redis://redis1:26379\"" "\"${REDIS_SENTINEL1}\"" 360-cg-manager/config.yml
replace_text "\"redis://redis2:26379\"" "\"${REDIS_SENTINEL2}\"" 360-cg-manager/config.yml
replace_text "\"redis://redis3:26379\"" "\"${REDIS_SENTINEL3}\"" 360-cg-manager/config.yml
replace_text "distributedLockImplementation: MONGO" "distributedLockImplementation: REDIS" 360-cg-manager/config.yml

replace_text "uri: mongodb://localhost:27017/harness" "uri: ${MONGO_URI}" 350-event-server/event-service-config.yml
replace_text "uri: mongodb://localhost:27017/harness" "uri: ${MONGO_URI}" 280-batch-processing/batch-processing-config.yml
replace_text "uri: mongodb://localhost:27017/events" "uri: ${MONGO_URI}_events" 280-batch-processing/batch-processing-config.yml
replace_text "uri: mongodb://localhost:27017/harness" "uri: ${MONGO_URI}" 210-command-library-server/command-library-server-config.yml
replace_text "uri: mongodb://localhost:27017/harnessci" "uri: ${MONGO_URI}_ci" 310-ci-manager/ci-manager-config.yml
replace_text "uri: mongodb://localhost:27017/harness" "uri: ${MONGO_URI}" 310-ci-manager/ci-manager-config.yml

CONFIG_FILES=(360-cg-manager/config.yml 350-event-server/event-service-config.yml 280-batch-processing/batch-processing-config.yml 210-command-library-server/command-library-server-config.yml 310-ci-manager/ci-manager-config.yml)

for config_file in ${CONFIG_FILES[@]}; do
  mongo_uri_count=$(grep -P "^\s*uri: mongodb://.*:27017" $config_file | grep -v localhost:27017 | wc -l | awk '{print $1}')
  good_mongo_uri_count=$(grep -P "^\s*uri: mongodb://.*:27017/functional_test_\d{10}(_[a-zA-Z]+)?(_[a-zA-Z]+)?$" $config_file | grep -v localhost:27017 | wc -l | awk '{print $1}')
  echo "$config_file - Mongo URI count: $mongo_uri_count | Good Mongo URI count: $good_mongo_uri_count"
  if [[ $mongo_uri_count != $good_mongo_uri_count ]]; then
    echo "Invalid Mongo URIs found in $config_file, please follow the format - functional_test_<epoch> or functional_test_<epoch>_sometext"
    exit 1
  fi
done
