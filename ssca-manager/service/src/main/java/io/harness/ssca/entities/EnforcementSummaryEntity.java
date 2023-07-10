/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.entities;

import static io.harness.annotations.dev.HarnessTeam.SSCA;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.ssca.beans.Artifact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@OwnedBy(SSCA)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "EnforcementSummaryEntityKeys")
@StoreIn(DbAliases.SSCA)
@Entity(value = "enforcementResultSummary", noClassnameStored = true)
@Document("enforcementResultSummary")
@TypeAlias("enforcementResultSummary")
@HarnessEntity(exportable = true)
public class EnforcementSummaryEntity implements PersistentEntity {
  Artifact artifact;
  @Field("enforcementid") String enforcementId;
  @Field("orchestrationid") String orchestrationId;
  @Field("denylistviolationcount") int denyListViolationCount;
  @Field("allowlistviolationcount") int allowListViolationCount;
  String status;
}
