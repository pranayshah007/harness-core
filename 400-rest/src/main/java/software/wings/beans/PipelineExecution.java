/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.entityinterface.ApplicationAccess;

import com.github.reinert.jjschema.SchemaIgnore;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
@Data
@StoreIn(DbAliases.HARNESS)
@Entity(value = "pipelineExecutions", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "PipelineExecutionKeys")
public class PipelineExecution
    implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, ApplicationAccess {
  public static final String PIPELINE_ID_KEY = "pipelineId";

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @FdIndex private String pipelineId;
  private String workflowExecutionId;
  private String stateMachineId;
  private Pipeline pipeline;
  private List<PipelineStageExecution> pipelineStageExecutions;
  private String appName;
  private WorkflowType workflowType;
  private ExecutionStatus status = ExecutionStatus.NEW;

  private String name;

  private Long startTs;
  private Long endTs;
  private Long estimatedTime;

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String pipelineId;
    private String workflowExecutionId;
    private String stateMachineId;
    private String artifactId;
    private String artifactName;
    private Pipeline pipeline;
    private ExecutionArgs executionArgs;
    private List<PipelineStageExecution> pipelineStageExecutions = new ArrayList<>();
    private String appName;
    private WorkflowType workflowType;
    private ExecutionStatus status = ExecutionStatus.NEW;
    private String name;
    private Long startTs;
    private Long endTs;
    private Long estimatedTime = Long.valueOf(5 * 60 * 1000);
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;

    private Builder() {}

    /**
     * A pipeline execution builder.
     *
     * @return the builder
     */
    public static Builder aPipelineExecution() {
      return new Builder();
    }

    /**
     * With pipeline id builder.
     *
     * @param pipelineId the pipeline id
     * @return the builder
     */
    public Builder withPipelineId(String pipelineId) {
      this.pipelineId = pipelineId;
      return this;
    }

    /**
     * With workflow execution id builder.
     *
     * @param workflowExecutionId the workflow execution id
     * @return the builder
     */
    public Builder withWorkflowExecutionId(String workflowExecutionId) {
      this.workflowExecutionId = workflowExecutionId;
      return this;
    }

    /**
     * With state machine id builder.
     *
     * @param stateMachineId  the state machine id
     * @return the builder
     */
    public Builder withStateMachineId(String stateMachineId) {
      this.stateMachineId = stateMachineId;
      return this;
    }

    /**
     * With artifact id builder.
     *
     * @param artifactId the artifact id
     * @return the builder
     */
    public Builder withArtifactId(String artifactId) {
      this.artifactId = artifactId;
      return this;
    }

    /**
     * With artifact name builder.
     *
     * @param artifactName the artifact name
     * @return the builder
     */
    public Builder withArtifactName(String artifactName) {
      this.artifactName = artifactName;
      return this;
    }

    /**
     * With pipeline builder.
     *
     * @param pipeline the pipeline
     * @return the builder
     */
    public Builder withPipeline(Pipeline pipeline) {
      this.pipeline = pipeline;
      return this;
    }

    /**
     * With pipeline builder.
     *
     * @param executionArgs the executionArgs
     * @return the builder
     */
    public Builder withExecutionArgs(ExecutionArgs executionArgs) {
      this.executionArgs = executionArgs;
      return this;
    }

    /**
     * With pipeline stage executions builder.
     *
     * @param pipelineStageExecutions the pipeline stage executions
     * @return the builder
     */
    public Builder withPipelineStageExecutions(List<PipelineStageExecution> pipelineStageExecutions) {
      this.pipelineStageExecutions = pipelineStageExecutions;
      return this;
    }

    /**
     * With app name builder.
     *
     * @param appName the app name
     * @return the builder
     */
    public Builder withAppName(String appName) {
      this.appName = appName;
      return this;
    }

    /**
     * With workflow type builder.
     *
     * @param workflowType the workflow type
     * @return the builder
     */
    public Builder withWorkflowType(WorkflowType workflowType) {
      this.workflowType = workflowType;
      return this;
    }

    /**
     * With status builder.
     *
     * @param status the status
     * @return the builder
     */
    public Builder withStatus(ExecutionStatus status) {
      this.status = status;
      return this;
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * With start ts builder.
     *
     * @param startTs the start ts
     * @return the builder
     */
    public Builder withStartTs(Long startTs) {
      this.startTs = startTs;
      return this;
    }

    /**
     * With end ts builder.
     *
     * @param endTs the end ts
     * @return the builder
     */
    public Builder withEndTs(Long endTs) {
      this.endTs = endTs;
      return this;
    }

    /**
     * With estimated time builder.
     *
     * @param estimatedTime the estimated time
     * @return the builder
     */
    public Builder withEstimatedTime(Long estimatedTime) {
      this.estimatedTime = estimatedTime;
      return this;
    }

    /**
     * With uuid builder.
     *
     * @param uuid the uuid
     * @return the builder
     */
    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    /**
     * With app id builder.
     *
     * @param appId the app id
     * @return the builder
     */
    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    /**
     * With created by builder.
     *
     * @param createdBy the created by
     * @return the builder
     */
    public Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    /**
     * With created at builder.
     *
     * @param createdAt the created at
     * @return the builder
     */
    public Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aPipelineExecution()
          .withPipelineId(pipelineId)
          .withWorkflowExecutionId(workflowExecutionId)
          .withArtifactId(artifactId)
          .withArtifactName(artifactName)
          .withPipeline(pipeline)
          .withPipelineStageExecutions(pipelineStageExecutions)
          .withAppName(appName)
          .withWorkflowType(workflowType)
          .withStatus(status)
          .withName(name)
          .withStartTs(startTs)
          .withEndTs(endTs)
          .withEstimatedTime(estimatedTime)
          .withUuid(uuid)
          .withAppId(appId)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withStateMachineId(stateMachineId)
          .withExecutionArgs(executionArgs);
    }

    /**
     * Build pipeline execution.
     *
     * @return the pipeline execution
     */
    public PipelineExecution build() {
      PipelineExecution pipelineExecution = new PipelineExecution();
      pipelineExecution.setPipelineId(pipelineId);
      pipelineExecution.setWorkflowExecutionId(workflowExecutionId);
      pipelineExecution.setPipeline(pipeline);
      pipelineExecution.setPipelineStageExecutions(pipelineStageExecutions);
      pipelineExecution.setAppName(appName);
      pipelineExecution.setWorkflowType(workflowType);
      pipelineExecution.setStatus(status);
      pipelineExecution.setName(name);
      pipelineExecution.setStartTs(startTs);
      pipelineExecution.setEndTs(endTs);
      pipelineExecution.setEstimatedTime(estimatedTime);
      pipelineExecution.setUuid(uuid);
      pipelineExecution.setAppId(appId);
      pipelineExecution.setCreatedBy(createdBy);
      pipelineExecution.setCreatedAt(createdAt);
      pipelineExecution.setStateMachineId(stateMachineId);
      return pipelineExecution;
    }
  }
}
