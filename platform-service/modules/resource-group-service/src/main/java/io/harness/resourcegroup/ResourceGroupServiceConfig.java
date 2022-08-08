/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.AccessControlAdminClientConfiguration;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.DistributedLockImplementation;
import io.harness.mongo.MongoConfig;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(PL)
public class ResourceGroupServiceConfig {
  @JsonProperty("enableResourceGroup") boolean enableResourceGroup;
  @JsonProperty("mongo") @ConfigSecret MongoConfig mongoConfig;
  @JsonProperty("resourceClients") @ConfigSecret ResourceClientConfigs resourceClientConfigs;
  @JsonProperty("redis") @ConfigSecret RedisConfig redisConfig;
  @JsonProperty("redisLockConfig") @ConfigSecret RedisConfig redisLockConfig;
  @JsonProperty("accessControlAdminClient")
  @ConfigSecret
  AccessControlAdminClientConfiguration accessControlAdminClientConfiguration;
  @JsonProperty("auditClientConfig") ServiceHttpClientConfig auditClientConfig;
  @JsonProperty(value = "enableAudit") boolean enableAudit;
  @JsonProperty("distributedLockImplementation") DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("exportMetricsToStackDriver") boolean exportMetricsToStackDriver;
  @JsonProperty("hostname") String hostname;
  @JsonProperty("basePathPrefix") String basePathPrefix;
}
