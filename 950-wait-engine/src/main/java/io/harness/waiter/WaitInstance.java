/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter;

import static java.time.Duration.ofDays;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents which waiter is waiting on which correlation Ids and callback to execute when done.
 */
@Value
@Builder
@FieldNameConstants(innerTypeName = "WaitInstanceKeys")
@Document("waitInstances")
@TypeAlias("waitInstances")
@Entity(value = "waitInstances", noClassnameStored = true)
@HarnessEntity(exportable = false)
@OwnedBy(HarnessTeam.DEL)
@StoreIn(DbAliases.ALL)
public class WaitInstance implements WaitEngineEntity {
  public static final Duration TTL = ofDays(90);

  @Id @org.springframework.data.annotation.Id String uuid;
  @FdIndex List<String> correlationIds;
  @FdIndex List<String> waitingOnCorrelationIds;
  String publisher;

  NotifyCallback callback;
  long callbackProcessingAt;

  ProgressCallback progressCallback;

  @Default @FdTtlIndex @NonFinal @Wither Date validUntil = Date.from(OffsetDateTime.now().plus(TTL).toInstant());

  // Timeout instance will expire after this duration if 0 it will never expire
  Duration timeout;
  @NonFinal @Wither String timeoutInstanceId;
}
