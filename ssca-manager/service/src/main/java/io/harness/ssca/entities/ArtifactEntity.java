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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import java.time.Instant;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@OwnedBy(SSCA)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "ArtifactEntityKeys")
@StoreIn(DbAliases.SSCA)
@Entity(value = "sscaArtefact", noClassnameStored = true)
@Document("sscaArtefact")
@TypeAlias("sscaArtefact")
@HarnessEntity(exportable = true)
public class ArtifactEntity implements PersistentEntity {
  @Id String id;
  @Field("artifactid") String artifactId;
  @Field("orchestrationid") String orchestrationId;
  @NotEmpty String url;
  String name;
  String type;
  String tag;
  @Field("accountid") String accountId;
  @Field("orgid") String orgId;
  @Field("projectid") String projectId;
  @Field("pipelineexecutionid") String pipelineExecutionId;
  @Field("pipelineid") String pipelineId;
  @Field("stageid") String stageId;
  @Field("sequenceid") String sequenceId;
  @Field("stepid") String stepId;
  @Field("sbomname") String sbomName;
  @Field("createdon") Instant createdOn;
  @Field("isattested") boolean isAttested;
  @Field("attestedfileurl") String attestedFileUrl;
  Sbom sbom;
  @Value
  @Builder
  public static class Sbom {
    String tool;
    @Field("toolversion") String toolVersion;
    @Field("sbomformat") String sbomFormat;
    @Field("sbomversion") String sbomVersion;
  }
}
