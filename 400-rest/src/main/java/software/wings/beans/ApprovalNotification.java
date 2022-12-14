/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static software.wings.beans.ApprovalNotification.ApprovalStage.APPROVED;
import static software.wings.beans.ApprovalNotification.ApprovalStage.PENDING;
import static software.wings.beans.ApprovalNotification.ApprovalStage.REJECTED;
import static software.wings.beans.Notification.NotificationType.APPROVAL;
import static software.wings.beans.NotificationAction.Builder.aNotificationAction;
import static software.wings.beans.NotificationAction.NotificationActionType.APPROVE;
import static software.wings.beans.NotificationAction.NotificationActionType.REJECT;

import static java.util.Arrays.asList;

import io.harness.beans.EmbeddedUser;

import software.wings.beans.NotificationAction.NotificationActionType;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact.Status;
import software.wings.service.intfc.ArtifactService;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by anubhaw on 7/25/16.
 */
@JsonTypeName("APPROVAL")
@EqualsAndHashCode(callSuper = true)
public class ApprovalNotification extends ActionableNotification {
  @NotEmpty private String entityName;
  @NotNull private ApprovalStage stage = PENDING;
  private String artifactStreamId;
  @Inject @Transient private transient WingsPersistence wingsPersistence;
  @Inject @Transient private transient ArtifactService artifactService;

  /**
   * Instantiates a new Approval notification.
   */
  public ApprovalNotification() {
    super(APPROVAL,
        asList(aNotificationAction().withName("Approve").withType(APPROVE).withPrimary(true).build(),
            aNotificationAction().withName("Reject").withType(REJECT).withPrimary(false).build()));
  }

  @Override
  public boolean performAction(NotificationActionType actionType) {
    if (EntityType.ARTIFACT == getEntityType()) {
      artifactService.updateStatus(
          getEntityId(), getAccountId(), actionType == APPROVE ? Status.APPROVED : Status.REJECTED);
    }
    wingsPersistence.updateFields(
        ApprovalNotification.class, getUuid(), ImmutableMap.of("stage", actionType == APPROVE ? APPROVED : REJECTED));
    return true;
  }

  /**
   * Gets entity name.
   *
   * @return the entity name
   */
  public String getEntityName() {
    return entityName;
  }

  /**
   * Sets entity name.
   *
   * @param entityName the entity name
   */
  public void setEntityName(String entityName) {
    this.entityName = entityName;
  }

  /**
   * Gets stage.
   *
   * @return the stage
   */
  public ApprovalStage getStage() {
    return stage;
  }

  /**
   * Sets stage.
   *
   * @param stage the stage
   */
  public void setStage(ApprovalStage stage) {
    this.stage = stage;
  }

  /**
   * Gets artifact stream id.
   *
   * @return the artifact stream id
   */
  public String getArtifactStreamId() {
    return artifactStreamId;
  }

  /**
   * Sets artifact stream id.
   *
   * @param artifactStreamId the artifact stream id
   */
  public void setArtifactStreamId(String artifactStreamId) {
    this.artifactStreamId = artifactStreamId;
  }

  /**
   * The enum Approval stage.
   */
  public enum ApprovalStage {
    /**
     * Pending approval stage.
     */
    PENDING,
    /**
     * Accepted approval stage.
     */
    APPROVED,
    /**
     * Rejected approval stage.
     */
    REJECTED
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String environmentId;
    private String entityId;
    private EntityType entityType;
    private String accountId;
    private boolean complete = true;
    private String notificationTemplateId;
    private Map<String, String> notificationTemplateVariables = new HashMap<>();
    private String uuid;
    private String entityName;
    private String appId;
    private EmbeddedUser createdBy;
    private ApprovalStage stage = PENDING;
    private long createdAt;
    private String artifactStreamId;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private transient WingsPersistence wingsPersistence;
    private transient ArtifactService artifactService;

    private Builder() {}

    /**
     * An approval notification builder.
     *
     * @return the builder
     */
    public static Builder anApprovalNotification() {
      return new Builder();
    }

    /**
     * With environment id builder.
     *
     * @param environmentId the environment id
     * @return the builder
     */
    public Builder withEnvironmentId(String environmentId) {
      this.environmentId = environmentId;
      return this;
    }

    /**
     * With entity id builder.
     *
     * @param entityId the entity id
     * @return the builder
     */
    public Builder withEntityId(String entityId) {
      this.entityId = entityId;
      return this;
    }

    /**
     * With entity type builder.
     *
     * @param entityType the entity type
     * @return the builder
     */
    public Builder withEntityType(EntityType entityType) {
      this.entityType = entityType;
      return this;
    }

    /**
     * With account id builder.
     *
     * @param accountId the account id
     * @return the builder
     */
    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    /**
     * With complete builder.
     *
     * @param complete the complete
     * @return the builder
     */
    public Builder withComplete(boolean complete) {
      this.complete = complete;
      return this;
    }

    /**
     * With notification template id builder.
     *
     * @param notificationTemplateId the notification template id
     * @return the builder
     */
    public Builder withNotificationTemplateId(String notificationTemplateId) {
      this.notificationTemplateId = notificationTemplateId;
      return this;
    }

    /**
     * With notificatio template variables builder.
     *
     * @param notificatioTemplateVariables the notificatio template variables
     * @return the builder
     */
    public Builder withNotificatioTemplateVariables(Map<String, String> notificatioTemplateVariables) {
      this.notificationTemplateVariables = notificatioTemplateVariables;
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
     * With entity name builder.
     *
     * @param entityName the entity name
     * @return the builder
     */
    public Builder withEntityName(String entityName) {
      this.entityName = entityName;
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
     * With stage builder.
     *
     * @param stage the stage
     * @return the builder
     */
    public Builder withStage(ApprovalStage stage) {
      this.stage = stage;
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
     * With artifact stream id builder.
     *
     * @param artifactStreamId the artifact stream id
     * @return the builder
     */
    public Builder withArtifactStreamId(String artifactStreamId) {
      this.artifactStreamId = artifactStreamId;
      return this;
    }

    /**
     * With last updated by builder.
     *
     * @param lastUpdatedBy the last updated by
     * @return the builder
     */
    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    /**
     * With last updated at builder.
     *
     * @param lastUpdatedAt the last updated at
     * @return the builder
     */
    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    /**
     * With wings persistence builder.
     *
     * @param wingsPersistence the wings persistence
     * @return the builder
     */
    public Builder withWingsPersistence(WingsPersistence wingsPersistence) {
      this.wingsPersistence = wingsPersistence;
      return this;
    }

    /**
     * With artifact service builder.
     *
     * @param artifactService the artifact service
     * @return the builder
     */
    public Builder withArtifactService(ArtifactService artifactService) {
      this.artifactService = artifactService;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return anApprovalNotification()
          .withEnvironmentId(environmentId)
          .withEntityId(entityId)
          .withEntityType(entityType)
          .withAccountId(accountId)
          .withComplete(complete)
          .withNotificationTemplateId(notificationTemplateId)
          .withNotificatioTemplateVariables(notificationTemplateVariables)
          .withUuid(uuid)
          .withEntityName(entityName)
          .withAppId(appId)
          .withCreatedBy(createdBy)
          .withStage(stage)
          .withCreatedAt(createdAt)
          .withArtifactStreamId(artifactStreamId)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt)
          .withWingsPersistence(wingsPersistence)
          .withArtifactService(artifactService);
    }

    /**
     * Build approval notification.
     *
     * @return the approval notification
     */
    public ApprovalNotification build() {
      ApprovalNotification approvalNotification = new ApprovalNotification();
      approvalNotification.setEnvironmentId(environmentId);
      approvalNotification.setEntityId(entityId);
      approvalNotification.setEntityType(entityType);
      approvalNotification.setAccountId(accountId);
      approvalNotification.setComplete(complete);
      approvalNotification.setNotificationTemplateId(notificationTemplateId);
      approvalNotification.setNotificationTemplateVariables(notificationTemplateVariables);
      approvalNotification.setUuid(uuid);
      approvalNotification.setEntityName(entityName);
      approvalNotification.setAppId(appId);
      approvalNotification.setCreatedBy(createdBy);
      approvalNotification.setStage(stage);
      approvalNotification.setCreatedAt(createdAt);
      approvalNotification.setArtifactStreamId(artifactStreamId);
      approvalNotification.setLastUpdatedBy(lastUpdatedBy);
      approvalNotification.setLastUpdatedAt(lastUpdatedAt);
      approvalNotification.artifactService = this.artifactService;
      approvalNotification.wingsPersistence = this.wingsPersistence;
      return approvalNotification;
    }
  }
}
