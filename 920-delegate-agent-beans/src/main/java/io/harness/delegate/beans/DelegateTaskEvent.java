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
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", include = As.PROPERTY)
@JsonTypeName("DelegateTaskEvent")
@JsonSubTypes({
  @JsonSubTypes.Type(name = "DelegateTaskEvent", value = DelegateTaskEvent.class)
  , @JsonSubTypes.Type(name = "DelegateTaskAbortEvent", value = DelegateTaskAbortEvent.class)
})
@Data
public class DelegateTaskEvent {
  private String accountId;
  private String delegateTaskId;
  private boolean sync;
  private String taskType;
  private boolean useDms;

  public static final class DelegateTaskEventBuilder {
    private String accountId;
    private String delegateTaskId;
    private boolean sync;
    private String taskType;
    private boolean useDms;

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

    public DelegateTaskEventBuilder withDms(Boolean useDms) {
      this.useDms = useDms;
      return this;
    }

    public DelegateTaskEventBuilder but() {
      return aDelegateTaskEvent()
          .withAccountId(accountId)
          .withDelegateTaskId(delegateTaskId)
          .withSync(sync)
          .withTaskType(taskType)
          .withDms(useDms);
    }

    public DelegateTaskEvent build() {
      DelegateTaskEvent delegateTaskEvent = new DelegateTaskEvent();
      delegateTaskEvent.setAccountId(accountId);
      delegateTaskEvent.setDelegateTaskId(delegateTaskId);
      delegateTaskEvent.setSync(sync);
      delegateTaskEvent.setTaskType(taskType);
      delegateTaskEvent.setUseDms(useDms);
      return delegateTaskEvent;
    }
  }
}
