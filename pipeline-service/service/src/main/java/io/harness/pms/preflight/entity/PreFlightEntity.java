/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight.entity;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.persistence.UuidAware;
import io.harness.pms.preflight.PreFlightEntityErrorInfo;
import io.harness.pms.preflight.PreFlightStatus;
import io.harness.pms.preflight.connector.ConnectorCheckResponse;
import io.harness.pms.preflight.inputset.PipelineInputResponse;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "PreFlightEntityKeys")
@Entity(value = "preFlightEntity")
@Document("preFlightEntity")
@TypeAlias("preFlightEntity")
@HarnessEntity(exportable = false)
@OwnedBy(HarnessTeam.PIPELINE)
public class PreFlightEntity implements UuidAware {
  @Id @org.mongodb.morphia.annotations.Id String uuid;

  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String pipelineIdentifier;

  @NonNull String pipelineYaml;
  List<PipelineInputResponse> pipelineInputResponse;
  List<ConnectorCheckResponse> connectorCheckResponse;
  PreFlightStatus preFlightStatus;
  PreFlightEntityErrorInfo errorInfo;
  @FdTtlIndex Date validUntil;

  public AutoLogContext autoLogContext() {
    Map<String, String> logContext = new HashMap<>();
    logContext.put(PreFlightEntityKeys.accountIdentifier, accountIdentifier);
    logContext.put(PreFlightEntityKeys.projectIdentifier, projectIdentifier);
    logContext.put(PreFlightEntityKeys.orgIdentifier, orgIdentifier);
    logContext.put(PreFlightEntityKeys.pipelineIdentifier, pipelineIdentifier);
    return new AutoLogContext(logContext, OVERRIDE_NESTS);
  }
}
