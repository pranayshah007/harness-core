/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.MoreObjects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", include = As.PROPERTY)
@JsonTypeName("DelegateTaskEvent")
@JsonSubTypes({
  @JsonSubTypes.Type(name = "DelegateTaskEvent", value = DelegateTaskEvent.class)
  , @JsonSubTypes.Type(name = "DelegateTaskAbortEvent", value = DelegateTaskAbortEvent.class)
})
public class DelegateTaskEvent {
  private String accountId;
  private String delegateTaskId;
  private boolean sync;
  private String taskType;

  /**
   * Getter for property 'accountId'.
   *
   * @return Value for property 'accountId'.
   */
  public String getAccountId() {
    return accountId;
  }

  /**
   * Setter for property 'accountId'.
   *
   * @param accountId Value to set for property 'accountId'.
   */
  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  /**
   * Getter for property 'delegateTaskId'.
   *
   * @return Value for property 'delegateTaskId'.
   */
  public String getDelegateTaskId() {
    return delegateTaskId;
  }

  /**
   * Setter for property 'delegateTaskId'.
   *
   * @param delegateTaskId Value to set for property 'delegateTaskId'.
   */
  public void setDelegateTaskId(String delegateTaskId) {
    this.delegateTaskId = delegateTaskId;
  }

  /**
   * Getter for property 'sync'.
   *
   * @return Value for property 'sync'.
   */
  public boolean isSync() {
    return sync;
  }

  /**
   * Setter for property 'sync'.
   *
   * @param sync Value to set for property 'sync'.
   */
  public void setSync(boolean sync) {
    this.sync = sync;
  }

  /**
   * Getter for property 'taskType'.
   *
   * @return Value for property 'taskType'.
   */
  public String getTaskType() {
    return taskType;
  }

  /**
   * Setter for property 'accountId'.
   *
   * @param accountId Value to set for property 'accountId'.
   */
  public void setTaskType(String taskType) {
    this.taskType = taskType;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("delegateTaskId", delegateTaskId)
        .add("sync", sync)
        .add("accountId", accountId)
        .add("taskType", taskType)
        .toString();
  }

  public static final class DelegateTaskEventBuilder {
    private String accountId;
    private String delegateTaskId;
    private boolean sync;
    private String taskType;

    private DelegateTaskEventBuilder() {}

    public static DelegateTaskEventBuilder aDelegateTaskEvent() {
      return new DelegateTaskEventBuilder();
    }

    public DelegateTaskEventBuilder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public DelegateTaskEventBuilder withDelegateTaskId(String delegateTaskId) {
      this.delegateTaskId = delegateTaskId;
      return this;
    }

    public DelegateTaskEventBuilder withSync(boolean sync) {
      this.sync = sync;
      return this;
    }

    public DelegateTaskEventBuilder withTaskType(String taskType) {
      this.taskType = taskType;
      return this;
    }

    public DelegateTaskEventBuilder but() {
      return aDelegateTaskEvent()
          .withAccountId(accountId)
          .withDelegateTaskId(delegateTaskId)
          .withSync(sync)
          .withTaskType(taskType);
    }

    public DelegateTaskEvent build() {
      DelegateTaskEvent delegateTaskEvent = new DelegateTaskEvent();
      delegateTaskEvent.setAccountId(accountId);
      delegateTaskEvent.setDelegateTaskId(delegateTaskId);
      delegateTaskEvent.setSync(sync);
      delegateTaskEvent.setTaskType(taskType);
      return delegateTaskEvent;
    }
  }
}
