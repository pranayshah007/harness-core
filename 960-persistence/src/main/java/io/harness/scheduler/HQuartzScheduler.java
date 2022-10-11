/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.scheduler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;
import io.harness.maintenance.MaintenanceListener;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoSSLConfig;

import com.google.common.base.Preconditions;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;

@OwnedBy(HarnessTeam.DEL)
@Slf4j
public class HQuartzScheduler implements PersistentScheduler, MaintenanceListener {
  protected Injector injector;
  protected Scheduler scheduler;

  private SchedulerConfig schedulerConfig;
  private String defaultMongoUri;

  public HQuartzScheduler(Injector injector, SchedulerConfig schedulerConfig, String defaultMongoUri) {
    this.injector = injector;
    this.schedulerConfig = schedulerConfig;
    this.defaultMongoUri = defaultMongoUri;
  }

  private String getMongoUri() {
    if (isEmpty(schedulerConfig.getMongoUri())) {
      return defaultMongoUri;
    }
    return schedulerConfig.getMongoUri();
  }

  protected Scheduler createScheduler(Properties properties) throws SchedulerException {
    MongoConfig mongoConfig = injector.getInstance(MongoConfig.class);
    populateMongoSSLProperties(properties, mongoConfig);
    StdSchedulerFactory factory = new StdSchedulerFactory(properties);
    Scheduler newScheduler = factory.getScheduler();

    // by default newScheduler does not create all needed mongo indexes.
    // it is a bit hack but we are going to add them from here
    if (schedulerConfig.getJobStoreClass().equals(
            com.novemberain.quartz.mongodb.DynamicMongoDBJobStore.class.getCanonicalName())) {
      MongoClient mongoClient = injector.getInstance(Key.get(MongoClient.class, Names.named("primaryMongoClient")));
      MongoClientURI uri = new MongoClientURI(getMongoUri());
      final String databaseName = uri.getDatabase();
      if (databaseName == null) {
        throw new WingsException("The mongo db uri does not specify database name");
      }
      final MongoDatabase database = mongoClient.getDatabase(databaseName);

      final String prefix = properties.getProperty("org.quartz.jobStore.collectionPrefix");

      final MongoCollection<Document> collection = database.getCollection(prefix + "_triggers");

      BasicDBObject jobIdKey = new BasicDBObject("jobId", 1);
      collection.createIndex(jobIdKey, new IndexOptions().background(true));

      BasicDBObject fireKeys = new BasicDBObject();
      fireKeys.append("state", 1);
      fireKeys.append("nextFireTime", 1);
      collection.createIndex(fireKeys, new IndexOptions().background(true).name("fire"));

      BasicDBObject oldKeys = new BasicDBObject();
      oldKeys.append("lockState", 1);
      oldKeys.append("lastUpdated", 1);
      collection.createIndex(oldKeys, new IndexOptions().background(true).name("old"));
    }

    newScheduler.setJobFactory(injector.getInstance(InjectorJobFactory.class));
    return newScheduler;
  }

  protected Properties getDefaultProperties() {
    Properties props = new Properties();
    props.setProperty("org.quartz.scheduler.instanceId", schedulerConfig.getInstanceId());

    if (schedulerConfig.getJobStoreClass().equals(
            com.novemberain.quartz.mongodb.DynamicMongoDBJobStore.class.getCanonicalName())) {
      MongoClientURI uri = new MongoClientURI(getMongoUri());

      final String databaseName = uri.getDatabase();
      if (databaseName == null) {
        throw new WingsException("The mongo db uri does not specify database name");
      }

      MongoConfig mongoConfig = injector.getInstance(MongoConfig.class);
      populateMongoSSLProperties(props, mongoConfig);
      props.setProperty("org.quartz.jobStore.class", schedulerConfig.getJobStoreClass());
      props.setProperty("org.quartz.jobStore.mongoUri", uri.getURI());
      props.setProperty("org.quartz.jobStore.dbName", databaseName);
      props.setProperty("org.quartz.jobStore.collectionPrefix", schedulerConfig.getTablePrefix());
      props.setProperty("org.quartz.jobStore.mongoOptionWriteConcernTimeoutMillis",
          schedulerConfig.getMongoOptionWriteConcernTimeoutMillis());

      if (schedulerConfig.isClustered()) {
        props.setProperty("org.quartz.jobStore.isClustered", Boolean.TRUE.toString());
        props.setProperty("org.quartz.jobStore.checkInErrorHandler",
            com.novemberain.quartz.mongodb.cluster.NoOpErrorHandler.class.getCanonicalName());
        props.setProperty("org.quartz.scheduler.instanceId", "AUTO");

        props.setProperty("org.quartz.jobStore.clusterCheckinInterval", "30000");
      } else {
        props.setProperty("org.quartz.jobStore.isClustered", Boolean.FALSE.toString());
      }
    }

    props.setProperty("org.quartz.scheduler.idleWaitTime", schedulerConfig.getIdleWaitTime());
    props.setProperty("org.quartz.threadPool.threadCount", schedulerConfig.getThreadCount());
    props.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
    props.setProperty("org.quartz.plugin.triggHistory.class",
        org.quartz.plugins.history.LoggingTriggerHistoryPlugin.class.getCanonicalName());
    props.setProperty("org.quartz.plugin.jobHistory.class",
        org.quartz.plugins.history.LoggingJobHistoryPlugin.class.getCanonicalName());
    props.setProperty("org.quartz.scheduler.instanceName", schedulerConfig.getSchedulerName());

    return props;
  }

  private void populateMongoSSLProperties(Properties properties, MongoConfig mongoConfig) {
    if (mongoConfig != null) {
      MongoSSLConfig mongoSSLConfig = mongoConfig.getMongoSSLConfig();
      if (mongoSSLConfig != null && mongoSSLConfig.isMongoSSLEnabled()) {
        properties.setProperty(
            "org.quartz.jobStore.mongoOptionEnableSSL", String.valueOf(mongoSSLConfig.isMongoSSLEnabled()));
        Preconditions.checkArgument(StringUtils.isNotBlank(mongoSSLConfig.getMongoTrustStorePath()),
            "mongoTrustStorePath must be set if mongoSSLEnabled is set to true");
        properties.setProperty(
            "org.quartz.jobStore.mongoOptionTrustStorePath", mongoSSLConfig.getMongoTrustStorePath());
        properties.setProperty(
            "org.quartz.jobStore.mongoOptionTrustStorePassword", mongoSSLConfig.getMongoTrustStorePassword());
        properties.setProperty(
            "org.quartz.jobStore.mongoOptionTrustStorePassword", mongoSSLConfig.getMongoTrustStorePassword());
        properties.setProperty("org.quartz.jobStore.mongoOptionSslInvalidHostNameAllowed", String.valueOf(true));
      }
    }
  }

  /**
   * Gets scheduler.
   *
   * @return the scheduler
   */
  public Scheduler getScheduler() {
    return scheduler;
  }

  static boolean compare(JobDetail jobDetail1, JobDetail jobDetail2) {
    if (jobDetail1 == null && jobDetail2 == null) {
      return true;
    }

    if (jobDetail1 == null || jobDetail2 == null) {
      return false;
    }

    if (jobDetail1.isConcurrentExectionDisallowed() != jobDetail2.isConcurrentExectionDisallowed()) {
      return false;
    }

    if (jobDetail1.isPersistJobDataAfterExecution() != jobDetail2.isPersistJobDataAfterExecution()) {
      return false;
    }

    if (jobDetail1.isDurable() != jobDetail2.isDurable()) {
      return false;
    }

    return StringUtils.equals(jobDetail1.getDescription(), jobDetail1.getDescription());
  }

  static boolean compare(Trigger trigger1, Trigger trigger2) {
    if (trigger1 == null && trigger2 == null) {
      return true;
    }

    if (trigger1 == null || trigger2 == null) {
      return false;
    }

    if (!trigger1.getScheduleBuilder().equals(trigger2.getScheduleBuilder())) {
      return false;
    }

    return StringUtils.equals(trigger1.getDescription(), trigger2.getDescription());
  }

  // This method is under construction. If you using it and you make changes to your job or trigger
  // make sure that the difference will be detected from the compare methods
  @Override
  public void ensureJob__UnderConstruction(JobDetail jobDetail, Trigger trigger) {
    if (scheduler == null) {
      return;
    }

    try {
      final JobDetail currentJobDetail = scheduler.getJobDetail(jobDetail.getKey());

      if (compare(jobDetail, currentJobDetail)) {
        final Trigger currentTrigger = scheduler.getTrigger(trigger.getKey());
        if (compare(trigger, currentTrigger)) {
          return;
        }
      }

      scheduler.deleteJob(jobDetail.getKey());
      scheduler.scheduleJob(jobDetail, trigger);
    } catch (SchedulerException ex) {
      log.error("Couldn't ensure cron job [{}]", jobDetail.getKey(), ex);
    }
  }

  /**
   * Schedule job date.
   *
   * @param jobDetail the job detail
   * @param trigger   the trigger
   * @return the date
   */
  @Override
  public Date scheduleJob(JobDetail jobDetail, Trigger trigger) {
    if (scheduler == null) {
      return new Date();
    }

    try {
      return scheduler.scheduleJob(jobDetail, trigger);
    } catch (org.quartz.ObjectAlreadyExistsException ex) {
      // We do not need to pollute the logs with error logs, just the job already exists.
      // TODO: add additional check if the about to add job properties are the same with the already existing one.
      //       we should update the job if they differ.
    } catch (SchedulerException ex) {
      log.error("Couldn't schedule cron for job {} with trigger {}", jobDetail.toString(), trigger.toString(), ex);
    }
    return null;
  }

  /**
   * Delete job boolean.
   *
   * @param jobName   the job name
   * @param groupName the group name
   * @return the boolean
   */
  @Override
  public boolean deleteJob(String jobName, String groupName) {
    if (scheduler == null) {
      return true;
    }

    if (groupName != null && jobName != null) {
      try {
        return scheduler.deleteJob(new JobKey(jobName, groupName));
      } catch (SchedulerException ex) {
        log.error("Couldn't delete cron job [{} {}] ", groupName, jobName, ex);
      }
    }
    return false;
  }

  @Override
  public boolean pauseJob(String jobName, String groupName) {
    if (null == scheduler) {
      return true;
    }

    if (null != groupName && null != jobName) {
      try {
        scheduler.pauseJob(new JobKey(jobName, groupName));
        return true;
      } catch (SchedulerException ex) {
        log.error("Couldn't pause quartz job [{} {}] ", groupName, jobName, ex);
      }
    }
    return false;
  }

  @Override
  public boolean resumeJob(String jobName, String groupName) {
    if (null == scheduler) {
      return true;
    }

    if (null != groupName && jobName != null) {
      try {
        scheduler.resumeJob(new JobKey(jobName, groupName));
        return true;
      } catch (SchedulerException ex) {
        log.error("Couldn't resume quartz job [{} {}] ", groupName, jobName, ex);
      }
    }
    return false;
  }

  @Override
  public Date rescheduleJob(TriggerKey triggerKey, Trigger newTrigger) {
    try {
      return scheduler.rescheduleJob(triggerKey, newTrigger);
    } catch (SchedulerException e) {
      log.error("Couldn't reschedule cron for trigger {} with trigger {}", triggerKey, newTrigger);
    }
    return null;
  }

  @Override
  public Boolean checkExists(String jobName, String groupName) {
    if (scheduler == null) {
      return true;
    }
    if (groupName != null && jobName != null) {
      JobKey jobKey = new JobKey(jobName, groupName);
      try {
        return scheduler.checkExists(jobKey);
      } catch (SchedulerException e) {
        log.error("Couldn't check for cron for trigger {}", jobKey);
      }
    }
    return false;
  }

  @Override
  public void pauseAllQuartzJobsForAccount(String accountId) throws SchedulerException {
    for (JobKey jobKey : getAllJobKeysForAccount(accountId)) {
      pauseJob(jobKey.getName(), jobKey.getGroup());
    }
  }

  @Override
  public void resumeAllQuartzJobsForAccount(String accountId) throws SchedulerException {
    for (JobKey jobKey : getAllJobKeysForAccount(accountId)) {
      resumeJob(jobKey.getName(), jobKey.getGroup());
    }
  }

  @Override
  public void deleteAllQuartzJobsForAccount(String accountId) throws SchedulerException {
    for (JobKey jobKey : getAllJobKeysForAccount(accountId)) {
      deleteJob(jobKey.getName(), jobKey.getGroup());
    }
  }

  @Override
  public void onShutdown() {
    // do nothing
  }

  @Override
  public void onEnterMaintenance() {
    if (scheduler != null) {
      try {
        scheduler.standby();
      } catch (SchedulerException e) {
        log.error("Error putting scheduler into standby.", e);
      }
    }
  }

  @Override
  public void onLeaveMaintenance() {
    if (scheduler != null) {
      try {
        scheduler.start();
      } catch (SchedulerException e) {
        log.error("Error starting scheduler.", e);
      }
    }
  }

  /**
   * Gets all JobKeys matching an accountId
   * @param accountId given accountId
   * @return List of JobKeys
   * @throws SchedulerException when scheduler fails to get group names
   */
  public List<JobKey> getAllJobKeysForAccount(String accountId) throws SchedulerException {
    if (null != scheduler) {
      List<String> groupNames = scheduler.getJobGroupNames();
      return groupNames.stream()
          .flatMap(this::jobKeysFromGroupName)
          .filter(jobKey -> accountId.equals(accountIdFromJobKey(jobKey)))
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  /**
   * Returns a Stream of JobKeys extracted from groupName by the scheduler
   * @param groupName groupName of Job
   * @return JobKey contains details of Job in a dataMap
   */
  private Stream<JobKey> jobKeysFromGroupName(String groupName) {
    GroupMatcher<JobKey> matcher = GroupMatcher.jobGroupEquals(groupName);
    try {
      return scheduler.getJobKeys(matcher).stream();
    } catch (SchedulerException e) {
      log.error("Couldn't get JobKeys for group name {}", groupName, e);
      return Stream.empty();
    }
  }

  /**
   * Returns accountId value of Job, which is stored in jobDataMap of JobDetail
   * @param jobKey used to retrieve job instance
   * @return accountId associated with job else returns null
   */
  private String accountIdFromJobKey(JobKey jobKey) {
    try {
      return scheduler.getJobDetail(jobKey).getJobDataMap().getString("accountId");
    } catch (SchedulerException e) {
      log.error("Couldn't get accountId for JobDetail associated with JobKey {}", jobKey, e);
      return null;
    }
  }
}
