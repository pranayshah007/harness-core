/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.notification;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.mongo.MongoConfig;
import io.harness.notification.SeedDataConfiguration;
import io.harness.notification.SmtpConfig;
import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Value
@Builder(toBuilder = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationServiceConfiguration {
  @JsonProperty("mongo") @ConfigSecret MongoConfig mongoConfig;
  @JsonProperty("smtp") @ConfigSecret private SmtpConfig smtpConfig;
  @JsonProperty("seedDataConfiguration") private SeedDataConfiguration seedDataConfiguration;
  @JsonProperty("delegateServiceGrpcConfig") private GrpcClientConfig delegateServiceGrpcConfig;
}
