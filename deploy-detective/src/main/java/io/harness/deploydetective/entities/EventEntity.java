/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.deploydetective.entities;

import io.harness.annotations.StoreIn;
import io.harness.deploydetective.beans.Environment;
import io.harness.deploydetective.beans.EventType;
import io.harness.deploydetective.beans.VersioningScheme;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.DEPLOY_DETECTIVE)
@Entity(value = "EventEntity")
@Document("EventEntity")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EventEntity {
  @NotNull Environment environment;
  @NotNull EventType eventType;
  @NotNull VersioningScheme versioningScheme;
  @NotNull String buildVersion;
  @NotNull String release;
  @NotNull Long epoch;
  @NotNull String serviceName;
}
