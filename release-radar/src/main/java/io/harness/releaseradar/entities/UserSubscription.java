/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.entities;

import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.releaseradar.beans.EventFilter;

import dev.morphia.annotations.Entity;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.RELEASE_RADAR)
@Entity(value = "Subscriptions")
@Document("Subscriptions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSubscription {
  private String slackUserId;
  private String email;
  @NotNull private EventFilter filter;
}
