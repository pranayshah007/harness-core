/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.pms.contracts.plan.ConsumerConfig;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.plan.SdkModuleInfo;
import io.harness.pms.contracts.steps.SdkStep;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PmsSdkInstanceKeys")
@Entity(value = "pmsSdkInstances", noClassnameStored = true)
@Document("pmsSdkInstances")
@TypeAlias("pmsSdkInstances")
@HarnessEntity(exportable = false)
@StoreIn(DbAliases.PMS)
public class PmsSdkInstance implements PersistentEntity, UuidAware {
  @Setter @NonFinal @Id @org.mongodb.morphia.annotations.Id String uuid;

  @NotNull @FdUniqueIndex String name;
  Map<String, Set<String>> supportedTypes;
  Map<String, String> staticAliases;
  List<String> sdkFunctors;
  List<SdkStep> supportedSdkSteps;
  List<String> expandableFields;
  List<JsonExpansionInfo> jsonExpansionInfo;

  SdkModuleInfo sdkModuleInfo;

  ConsumerConfig interruptConsumerConfig;
  ConsumerConfig orchestrationEventConsumerConfig;
  ConsumerConfig facilitatorEventConsumerConfig;
  ConsumerConfig nodeStartEventConsumerConfig;
  ConsumerConfig progressEventConsumerConfig;
  ConsumerConfig nodeAdviseEventConsumerConfig;
  ConsumerConfig nodeResumeEventConsumerConfig;
  ConsumerConfig startPlanCreationEventConsumerConfig;

  @Default @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate Long createdAt = System.currentTimeMillis();
  @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate Long lastUpdatedAt;
  @Setter @NonFinal @Version Long version;

  @Default Boolean active = false;
}
