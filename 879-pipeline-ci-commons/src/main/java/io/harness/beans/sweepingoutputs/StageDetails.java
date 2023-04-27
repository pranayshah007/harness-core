/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.sweepingoutputs;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.steps.CIRegistry;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CIMANAGER)
@Entity(value = "stageDetails")
@HarnessEntity(exportable = true)
@TypeAlias("StageDetails")
@JsonTypeName("StageDetails")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.sweepingoutputs.StageDetails")
public class StageDetails implements PersistentEntity, UuidAware, ContextElement, AccountAccess {
  private String stageID;
  private String stageRuntimeID;
  private BuildStatusUpdateParameter buildStatusUpdateParameter;
  private List<CIRegistry> registries;
  private long lastUpdatedAt;
  private ExecutionSource executionSource;
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex private String accountId;
}
