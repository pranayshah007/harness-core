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
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.SLSAVerificationSummary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(SSCA)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "CdInstanceSummaryKeys")
@StoreIn(DbAliases.SSCA)
@Entity(value = "cdInstanceSummary", noClassnameStored = true)
@Document("cdInstanceSummary")
@TypeAlias("cdInstanceSummary")
@HarnessEntity(exportable = true)
public class CdInstanceSummary implements PersistentEntity, CreatedAtAware {
  @Id private String id;
  @NotNull private String artifactCorrelationId;
  @NotNull private String accountIdentifier;
  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;

  EnvType envType;
  @NotNull String envIdentifier;
  @NotNull String envName;

  @Setter @NonFinal private String lastPipelineName;
  @Setter @NonFinal @NotNull private String lastPipelineExecutionId;
  @Setter @NonFinal @NotNull private String lastPipelineExecutionName;
  @Setter @NonFinal private String sequenceId;

  @Setter @NonFinal @NotNull private String lastDeployedById;
  @Setter @NonFinal @NotNull private String lastDeployedByName;
  @Setter @NonFinal @NotNull private Long lastDeployedAt;
  @Setter @NonFinal private String triggerType;

  @Setter @NonFinal @Valid SLSAVerificationSummary slsaVerificationSummary;
  Set<String> instanceIds;
  @Setter @NonFinal long createdAt;
}
