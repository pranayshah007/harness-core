/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigrationTrackRespPayload;
import io.harness.ngmigration.beans.NGSkipDetail;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.SkippedExpressionDetail;

import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveSummaryDTO extends MigrationTrackRespPayload {
  private Map<NGMigrationEntityType, EntityMigratedStats> stats;
  private List<ImportError> errors;
  private List<MigratedDetails> alreadyMigratedDetails;
  private List<MigratedDetails> successfullyMigratedDetails;
  private List<NGSkipDetail> skipDetails;
  private List<SkippedExpressionDetail> skippedExpressions;

  @BsonIgnore @JsonIgnore private transient List<NGYamlFile> ngYamlFiles;
}
