/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionInterruptType;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.service.impl.AppLogContext;
import software.wings.service.impl.StateExecutionInstanceLogContext;
import software.wings.service.impl.WorkflowExecutionLogContext;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/**
 * The type Workflow execution event.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
@Data
@Entity(value = "executionInterrupts", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "ExecutionInterruptKeys")
public class ExecutionInterrupt implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware,
                                           UpdatedByAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("appId_seized_executionUuid_createdAt")
                 .field(ExecutionInterruptKeys.appId)
                 .field(ExecutionInterruptKeys.seized)
                 .field(ExecutionInterruptKeys.executionUuid)
                 .descSortField(ExecutionInterruptKeys.createdAt)
                 .build())
        .build();
  }

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;

  @NotNull private ExecutionInterruptType executionInterruptType;

  // If true, means this interruption is no longer in effect
  private boolean seized;
  private String envId;
  @NotNull @FdIndex private String executionUuid;
  @FdIndex private String stateExecutionInstanceId;
  @FdIndex private String accountId;

  private Map<String, Object> properties;

  public boolean isSeized() {
    return seized;
  }

  public void setSeized(boolean seized) {
    this.seized = seized;
  }

  public String getEnvId() {
    return envId;
  }

  public void setEnvId(String envId) {
    this.envId = envId;
  }

  public String getExecutionUuid() {
    return executionUuid;
  }

  public void setExecutionUuid(String executionUuid) {
    this.executionUuid = executionUuid;
  }

  public ExecutionInterruptType getExecutionInterruptType() {
    return executionInterruptType;
  }

  public void setExecutionInterruptType(ExecutionInterruptType executionInterruptType) {
    this.executionInterruptType = executionInterruptType;
  }

  public String getStateExecutionInstanceId() {
    return stateExecutionInstanceId;
  }

  public void setStateExecutionInstanceId(String stateExecutionInstanceId) {
    this.stateExecutionInstanceId = stateExecutionInstanceId;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }

  public void setProperties(Map<String, Object> properties) {
    this.properties = properties;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ExecutionInterrupt)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    ExecutionInterrupt that = (ExecutionInterrupt) o;
    return seized == that.seized && executionInterruptType == that.executionInterruptType
        && Objects.equals(envId, that.envId) && Objects.equals(executionUuid, that.executionUuid)
        && Objects.equals(stateExecutionInstanceId, that.stateExecutionInstanceId)
        && Objects.equals(properties, that.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), executionInterruptType, seized, envId, executionUuid, stateExecutionInstanceId, properties);
  }

  public AutoLogContext autoLogContext() {
    ImmutableMap.Builder<String, String> context = ImmutableMap.builder();
    if (getUuid() != null) {
      context.put("executionInterruptId", getUuid());
    }
    if (getAccountId() != null) {
      context.put(AccountLogContext.ID, getAccountId());
    }
    if (getAppId() != null) {
      context.put(AppLogContext.ID, getAppId());
    }

    if (getExecutionUuid() != null) {
      context.put(WorkflowExecutionLogContext.ID, getExecutionUuid());
    }

    if (getExecutionInterruptType() != null) {
      context.put(ExecutionInterruptKeys.executionInterruptType, getExecutionInterruptType().name());
    }

    if (getStateExecutionInstanceId() != null) {
      context.put(StateExecutionInstanceLogContext.ID, getStateExecutionInstanceId());
    }

    return new AutoLogContext(context.build(), OVERRIDE_NESTS);
  }

  public static final class ExecutionInterruptBuilder {
    protected String appId;
    private ExecutionInterruptType executionInterruptType;
    private boolean seized;
    private String envId;
    private String executionUuid;
    private String stateExecutionInstanceId;
    private Map<String, Object> properties;
    private String uuid;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private String accoundId;

    private ExecutionInterruptBuilder() {}

    public static ExecutionInterruptBuilder anExecutionInterrupt() {
      return new ExecutionInterruptBuilder();
    }

    public ExecutionInterruptBuilder executionInterruptType(ExecutionInterruptType executionInterruptType) {
      this.executionInterruptType = executionInterruptType;
      return this;
    }

    public ExecutionInterruptBuilder seized(boolean seized) {
      this.seized = seized;
      return this;
    }

    public ExecutionInterruptBuilder envId(String envId) {
      this.envId = envId;
      return this;
    }

    public ExecutionInterruptBuilder executionUuid(String executionUuid) {
      this.executionUuid = executionUuid;
      return this;
    }

    public ExecutionInterruptBuilder stateExecutionInstanceId(String stateExecutionInstanceId) {
      this.stateExecutionInstanceId = stateExecutionInstanceId;
      return this;
    }

    public ExecutionInterruptBuilder properties(Map<String, Object> properties) {
      this.properties = properties;
      return this;
    }

    public ExecutionInterruptBuilder uuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public ExecutionInterruptBuilder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public ExecutionInterruptBuilder createdBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public ExecutionInterruptBuilder createdAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public ExecutionInterruptBuilder lastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public ExecutionInterruptBuilder lastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public ExecutionInterruptBuilder accountId(String accoundId) {
      this.accoundId = accoundId;
      return this;
    }

    public ExecutionInterrupt build() {
      ExecutionInterrupt executionInterrupt = new ExecutionInterrupt();
      executionInterrupt.setExecutionInterruptType(executionInterruptType);
      executionInterrupt.setSeized(seized);
      executionInterrupt.setEnvId(envId);
      executionInterrupt.setExecutionUuid(executionUuid);
      executionInterrupt.setStateExecutionInstanceId(stateExecutionInstanceId);
      executionInterrupt.setProperties(properties);
      executionInterrupt.setUuid(uuid);
      executionInterrupt.setAppId(appId);
      executionInterrupt.setCreatedBy(createdBy);
      executionInterrupt.setCreatedAt(createdAt);
      executionInterrupt.setLastUpdatedBy(lastUpdatedBy);
      executionInterrupt.setLastUpdatedAt(lastUpdatedAt);
      executionInterrupt.setAccountId(accoundId);
      return executionInterrupt;
    }
  }
}
