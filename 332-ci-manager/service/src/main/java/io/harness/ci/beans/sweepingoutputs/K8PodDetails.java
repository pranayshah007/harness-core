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
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CIMANAGER)
@Entity(value = "podDetails")
@HarnessEntity(exportable = true)
@TypeAlias("k8PodDetails")
@JsonTypeName("k8PodDetails")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.sweepingoutputs.K8PodDetails")
public class K8PodDetails implements PersistentEntity, UuidAware, ContextElement, AccountAccess {
  private String namespace; // Don't use it, it will be removed soon
  private String stageID;
  private String stageName;
  private String clusterName; // Don't use it, it will be removed soon
  private long lastUpdatedAt;
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex private String accountId;
}
