/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.scheduler;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SchedulerConfig {
  @JsonProperty(defaultValue = "com.novemberain.quartz.mongodb.DynamicMongoDBJobStore")
  private String jobStoreClass = com.novemberain.quartz.mongodb.DynamicMongoDBJobStore.class.getCanonicalName();

  @JsonProperty(defaultValue = "true") private boolean isEnabled = true;
  @JsonProperty(defaultValue = "1") private String threadCount = "1";
  @JsonProperty(defaultValue = "10000") private String idleWaitTime = "10000";
  @JsonProperty(defaultValue = "true") private String autoStart = "true";
  @JsonProperty(defaultValue = "scheduler") private String schedulerName = "scheduler";
  @JsonProperty(defaultValue = "scheduler") private String instanceId = "scheduler";
  @JsonProperty(defaultValue = "quartz") private String tablePrefix = "quartz";
  @JsonProperty(defaultValue = "false") private boolean isClustered;

  private String mongoUri;
  @JsonProperty(defaultValue = "30000") private String mongoOptionWriteConcernTimeoutMillis = "30000";

  public boolean isEnabled() {
    return isEnabled;
  }

  public void setEnabled(boolean enabled) {
    isEnabled = enabled;
  }

  public String getJobStoreClass() {
    return jobStoreClass;
  }

  public void setJobStoreClass(String jobStoreClass) {
    this.jobStoreClass = jobStoreClass;
  }

  public String getThreadCount() {
    return threadCount;
  }

  public void setThreadCount(String threadCount) {
    this.threadCount = threadCount;
  }

  public String getIdleWaitTime() {
    return idleWaitTime;
  }

  public void setIdleWaitTime(String idleWaitTime) {
    this.idleWaitTime = idleWaitTime;
  }

  public String getAutoStart() {
    return autoStart;
  }

  public void setAutoStart(String autoStart) {
    this.autoStart = autoStart;
  }

  public String getSchedulerName() {
    return schedulerName;
  }

  public void setSchedulerName(String schedulerName) {
    this.schedulerName = schedulerName;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  public String getTablePrefix() {
    return tablePrefix;
  }

  public void setTablePrefix(String tablePrefix) {
    this.tablePrefix = tablePrefix;
  }

  public boolean isClustered() {
    return isClustered;
  }

  public void setClustered(boolean clustered) {
    isClustered = clustered;
  }

  public String getMongoUri() {
    return mongoUri;
  }

  public void setMongoUri(String mongoUri) {
    this.mongoUri = mongoUri;
  }

  public String getMongoOptionWriteConcernTimeoutMillis() {
    return mongoOptionWriteConcernTimeoutMillis;
  }

  public void setMongoOptionWriteConcernTimeoutMillis(String mongoOptionWriteConcernTimeoutMillis) {
    this.mongoOptionWriteConcernTimeoutMillis = mongoOptionWriteConcernTimeoutMillis;
  }
}
