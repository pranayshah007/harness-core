/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.model.EventType;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.google.common.base.MoreObjects;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.mongodb.morphia.annotations.Entity;

/**
 * Created by anubhaw on 7/22/16.
 */
@Data
@AllArgsConstructor
@StoreIn(DbAliases.HARNESS)
@Entity(value = "notifications")
@HarnessEntity(exportable = false)
@JsonTypeInfo(use = Id.NAME, property = "notificationType", include = As.EXISTING_PROPERTY)
@TargetModule(HarnessModule._957_CG_BEANS)
public abstract class Notification extends Base implements AccountAccess {
  private String environmentId;
  private String entityId;
  private EntityType entityType;
  @FdIndex private String accountId;
  private EventType eventType;

  @NotNull private NotificationType notificationType;
  @NotNull private boolean complete = true;
  @NotNull private boolean actionable;

  private String notificationTemplateId;
  private Map<String, String> notificationTemplateVariables = new HashMap<>();

  /**
   * Instantiates a new Notification.
   */
  public Notification() {}

  /**
   * Instantiates a new Notification.
   *
   * @param notificationType the notification type
   */
  public Notification(NotificationType notificationType) {
    this(notificationType, false);
  }

  /**
   * Instantiates a new Notification.
   *
   * @param notificationType the notification type
   * @param actionable       the actionable
   */
  public Notification(NotificationType notificationType, boolean actionable) {
    this.notificationType = notificationType;
    this.actionable = actionable;
    this.complete = !actionable; // actionable notification are not complete on creation
  }

  /**
   * The enum Notification type.
   */
  public enum NotificationType {
    /**
     * Approval notification type.
     */
    APPROVAL,
    /**
     * Change notification type.
     */
    CHANGE,
    /**
     * Failure notification type.
     */
    FAILURE,
    /**
     * Information notification type.
     */
    INFORMATION
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("environmentId", environmentId)
        .add("entityId", entityId)
        .add("entityType", entityType)
        .add("accountId", accountId)
        .add("notificationType", notificationType)
        .add("complete", complete)
        .add("actionable", actionable)
        .toString();
  }
}
