/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.metrics.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.morphia.annotations.Entity;
import java.time.Instant;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "AccountActivityMetricsTrackerKeys")
@StoreIn(DbAliases.AUDITS)
@Entity(value = "AccountActivityMetricsTracker", noClassnameStored = true)
@Document("AccountActivityMetricsTracker")
@TypeAlias("AccountActivityMetricsTracker")
@JsonInclude(NON_NULL)
public class AccountActivityMetricsTracker {
  @Id @dev.morphia.annotations.Id String id;
  Instant startTime;
  Instant endTime;
  @NotNull Instant lastPublishTime;
}
