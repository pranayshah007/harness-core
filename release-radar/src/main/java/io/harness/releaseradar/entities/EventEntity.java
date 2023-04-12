/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.entities;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.releaseradar.beans.Environment;
import io.harness.releaseradar.beans.EventType;
import io.harness.releaseradar.beans.VersioningScheme;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.NotNull;

@Data
@Builder
@StoreIn(DbAliases.RELEASE_RADAR)
@Entity(value = "EventEntity")
@Document("EventEntity")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EventEntity {
  @Id String id;
  @NotNull Environment environment;
  @NotNull EventType eventType;
  @NotNull VersioningScheme versioningScheme;
  @NotNull String buildVersion;
  @NotNull String release;
  @NotNull Long epoch;
  @NotNull String serviceName;
  Long createdAt;
}
