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

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
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
  @NotNull private String tfPlanJsonFieldId;
  @NotNull private String tfPlanFileBucket;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_tf_plan_execution_details_idx")
                 .unique(true)
                 .field(TFPlanExecutionDetailsKeys.accountIdentifier)
                 .field(TFPlanExecutionDetailsKeys.orgIdentifier)
                 .field(TFPlanExecutionDetailsKeys.projectIdentifier)
                 .field(TFPlanExecutionDetailsKeys.pipelineExecutionId)
                 .field(TFPlanExecutionDetailsKeys.stageExecutionId)
                 .field(TFPlanExecutionDetailsKeys.provisionerId)
                 .build())
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
