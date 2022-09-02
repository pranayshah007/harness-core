/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.WorkflowType;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;

import io.harness.ng.DbAliases;
import software.wings.beans.Base;
import software.wings.beans.ExecutionArgs;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
@Data
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "triggerExecutions")
@HarnessEntity(exportable = false)
public class TriggerExecution extends Base {
  public static final String TRIGGER_ID_KEY = "triggerId";
  public static final String WEBHOOK_TOKEN_KEY = "webhookToken";
  public static final String STATUS_KEY = "status";
  public static final String WORKFLOW_ID_KEY = "workflowId";
  public static final String WEBHOOK_EVENT_DETAILS_GIT_CONNECTOR_ID_KEY = "webhookEventDetails.gitConnectorId";
  public static final String WEBHOOK_EVENT_DETAILS_BRANCH_NAME_KEY = "webhookEventDetails.branchName";
  public static final String WEBHOOK_EVENT_DETAILS_BRANCH_NAMES_KEY = "webhookEventDetails.branchNames";
  public static final String WEBHOOK_EVENT_DETAILS_WEBHOOK_SOURCE_KEY = "webhookEventDetails.webhookSource";

  @FdIndex private String accountId;
  @NotEmpty private String triggerId;
  @NotEmpty private String triggerName;
  String workflowExecutionId;
  private String workflowExecutionName;
  @NotNull private Status status;
  private String message;
  private String webhookToken;
  private WebhookEventDetails webhookEventDetails;
  private String envId;
  private ExecutionArgs executionArgs;
  private String workflowId;
  private WorkflowType workflowType;

  @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(6).toInstant());

  @Builder
  public TriggerExecution(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, String entityYamlPath, String triggerId, String triggerName, String workflowExecutionId,
      String workflowExecutionName, Status status, String message, String webhookToken,
      WebhookEventDetails webhookEventDetails) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.triggerId = triggerId;
    this.triggerName = triggerName;
    this.workflowExecutionId = workflowExecutionId;
    this.workflowExecutionName = workflowExecutionName;
    this.status = status;
    this.message = message;
    this.webhookToken = webhookToken;
    this.webhookEventDetails = webhookEventDetails;
  }

  public enum Status { FAILED, REJECTED, SUCCESS, RUNNING }

  @Data
  @Builder
  public static class WebhookEventDetails {
    String gitConnectorId;
    List<String> branchNames;
    String repoName;
    String commitId;
    String prevCommitId;
    String payload;
    List<String> filePaths;
    String webhookSource;
    String webhookEventType;
    String prAction;
    Map<String, String> parameters;
  }
}
