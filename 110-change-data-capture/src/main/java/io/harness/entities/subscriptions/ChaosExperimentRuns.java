/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities.subscriptions;

import static io.harness.annotations.dev.HarnessTeam.CHAOS;

import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CHAOS)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ChaosExperimentRunsKeys", asEnum = true)
@StoreIn(DbAliases.CHAOS)
@Entity(value = "chaosExperimentRuns", noClassnameStored = true)
@Document("chaosExperimentRuns")
@TypeAlias("chaosExperimentRuns")
@ChangeDataCapture(table = "chaos_experiment_runs", dataStore = "chaos", fields = {}, handler = "")
public class ChaosExperimentRuns implements PersistentEntity {
  @Id @dev.morphia.annotations.Id String id;
  String experiment_id;
  String experiment_run_id;
  String infra_id;
  String phase;
  String revision_id;
  @LastModifiedDate Long updated_at;
  String updated_by;
  String notify_id;
  Integer resiliency_score;
  Integer faults_passed;
  Integer faults_failed;
  Integer faults_awaited;
  Integer faults_stopped;
  Integer faults_na;
  Integer total_faults;
  boolean completed;
  boolean is_removed;
  String account_id;
  String org_id;
  String project_id;
  @CreatedDate Long created_at;
}
