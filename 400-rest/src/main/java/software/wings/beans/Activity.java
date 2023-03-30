/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.TriggeredBy;
import io.harness.beans.WorkflowType;
import io.harness.dataretention.AccountDataRetentionEntity;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.entityinterface.ApplicationAccess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ActivityKeys")
@NoArgsConstructor
@AllArgsConstructor
@StoreIn(DbAliases.HARNESS)
@Entity(value = "activities", noClassnameStored = true)
@HarnessEntity(exportable = true)
@TargetModule(HarnessModule._957_CG_BEANS)
public class Activity implements PersistentEntity, AccountDataRetentionEntity, UuidAware, CreatedAtAware,
                                 CreatedByAware, UpdatedAtAware, UpdatedByAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("app_status_createdAt")
                 .unique(false)
                 .field(ActivityKeys.appId)
                 .field(ActivityKeys.serviceInstanceId)
                 .field(ActivityKeys.status)
                 .descSortField(ActivityKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("appId_serviceId_environmentId_status_createdAt")
                 .field(ActivityKeys.appId)
                 .field(ActivityKeys.serviceId)
                 .field(ActivityKeys.environmentId)
                 .field(ActivityKeys.status)
                 .descSortField(ActivityKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_validUntil")
                 .field(ActivityKeys.accountId)
                 .field(ActivityKeys.validUntil)
                 .build())
        .build();
  }
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;

  private Type type;
  @NotEmpty private String applicationName;
  @NotEmpty private String environmentId;
  @NotEmpty private String environmentName;
  @NotNull private EnvironmentType environmentType;
  @NotEmpty private String commandName;
  @Default @NotNull private List<CommandUnit> commandUnits = new ArrayList<>();
  private Map<String, Integer> commandNameVersionMap;
  private String commandType;
  private String serviceId;
  private String serviceName;
  private String serviceTemplateId;
  private String serviceTemplateName;
  private String hostName;
  private String publicDns;
  private String serviceInstanceId;
  private String infrastructureDefinitionId;
  @NotEmpty private String workflowExecutionId;
  @NotEmpty private String workflowId;
  @NotEmpty private String workflowExecutionName;
  @NotNull private WorkflowType workflowType;
  @NotEmpty private String stateExecutionInstanceId;
  @NotEmpty private String stateExecutionInstanceName;
  @Version private Long version; // Morphia managed for optimistic locking. don't remove

  @Default private CommandUnitType commandUnitType = CommandUnitType.COMMAND;
  private boolean logPurged;

  private String artifactStreamId;
  private String artifactStreamName;
  private boolean isPipeline;
  private String artifactId;
  private String artifactName;
  @Default private ExecutionStatus status = ExecutionStatus.RUNNING;
  private TriggeredBy triggeredBy;
  @FdIndex private String accountId;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  @Default
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(6).toInstant());

  /**
   * The enum Type.
   */
  public enum Type {
    /**
     * Command type.
     */
    Command,
    /**
     * Verification type.
     */
    Verification,
    /**
     * None of the above.
     */
    Other
  }
}
