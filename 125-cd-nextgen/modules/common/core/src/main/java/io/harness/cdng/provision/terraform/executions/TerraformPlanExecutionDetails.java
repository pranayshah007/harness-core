/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.executions;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@ToString
@FieldNameConstants(innerTypeName = "TFPlanExecutionDetailsKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "terraformPlanExecutionDetails", noClassnameStored = true)
@Document("terraformPlanExecutionDetails")
@TypeAlias("terraformPlanExecutionDetails")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanExecutionDetails implements PersistentEntity, UuidAware {
  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastModifiedAt;

  @NotNull private String accountIdentifier;
  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;
  @NotNull private String pipelineExecutionId;
  @NotNull private String stageExecutionId;
  @NotNull private String provisionerId;
  private String tfPlanJsonFieldId;
  @NotNull private String tfPlanFileBucket;
  private String tfHumanReadablePlanId;
  @NotNull private String tfHumanReadablePlanFileBucket;
  @NotNull private EncryptionConfig encryptionConfig;
  private String tfcPolicyChecksFileId;
  private String tfcPolicyChecksFileBucket;
  /**
   * Currently, encryptedTfPlan is storing only one element. But in future encryptedTfPlan will be broken in multiple
   * parts and stored in vault, because one element can exceed the allowed size limit.
   */
  @NotNull private List<EncryptedRecordData> encryptedTfPlan;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_pipelineExecutionId_idx")
                 .field(TFPlanExecutionDetailsKeys.accountIdentifier)
                 .field(TFPlanExecutionDetailsKeys.orgIdentifier)
                 .field(TFPlanExecutionDetailsKeys.projectIdentifier)
                 .field(TFPlanExecutionDetailsKeys.pipelineExecutionId)
                 .build())
        .build();
  }
}
