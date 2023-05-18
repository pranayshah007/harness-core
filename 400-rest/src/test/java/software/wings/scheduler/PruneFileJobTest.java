/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.rule.OwnerRule.GEORGE;

import static software.wings.persistence.AppContainer.Builder.anAppContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.FileBucket;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.scheduler.PersistentScheduler;

import software.wings.WingsBaseTest;
import software.wings.beans.Base;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.AppContainer;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.FileService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.Inject;
import java.util.Date;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.slf4j.LoggerFactory;

public class PruneFileJobTest extends WingsBaseTest {
  @Mock private WingsPersistence wingsPersistence;

  @Mock private FileService fileService;

  @Mock private PersistentScheduler jobScheduler;

  @Inject @InjectMocks PruneFileJob job;

  private static final String ENTITY_ID = "entityId";

  public JobDetail details(String className, String entityId, FileBucket bucket) {
    return JobBuilder.newJob(PruneFileJob.class)
        .withIdentity(ENTITY_ID, PruneFileJob.GROUP)
        .usingJobData(PruneFileJob.ENTITY_CLASS_KEY, className)
        .usingJobData(PruneFileJob.ENTITY_ID_KEY, entityId)
        .usingJobData(PruneFileJob.BUCKET_KEY, bucket.name())
        .build();
  }

  public JobDetail details(Class cls, String objectId, FileBucket bucket) {
    return details(cls.getCanonicalName(), objectId, bucket);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void selfPruneTheJobWhenSucceed() throws Exception {
    when(wingsPersistence.get(Artifact.class, ENTITY_ID)).thenReturn(null);
    doNothing().when(fileService).deleteFile(ENTITY_ID, FileBucket.PLATFORMS);

    JobExecutionContext context = mock(JobExecutionContext.class);
    when(context.getJobDetail()).thenReturn(details(Artifact.class, ENTITY_ID, FileBucket.PLATFORMS));

    job.execute(context);

    verify(wingsPersistence, times(1)).get(Artifact.class, ENTITY_ID);
    verify(fileService, times(1)).deleteAllFilesForEntity(ENTITY_ID, FileBucket.PLATFORMS);
    verify(jobScheduler, times(1)).deleteJob(ENTITY_ID, PruneFileJob.GROUP);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void selfPruneTheJobIfServiceStillThereFirstTime() throws Exception {
    when(wingsPersistence.get(AppContainer.class, ENTITY_ID)).thenReturn(anAppContainer().build());

    JobExecutionContext context = mock(JobExecutionContext.class);
    when(context.getJobDetail()).thenReturn(details(AppContainer.class, ENTITY_ID, FileBucket.PLATFORMS));
    when(context.getPreviousFireTime()).thenReturn(null);

    job.execute(context);

    verify(fileService, times(0)).deleteAllFilesForEntity(ENTITY_ID, FileBucket.PLATFORMS);
    verify(jobScheduler, times(0)).deleteJob(ENTITY_ID, PruneFileJob.GROUP);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void selfPruneTheJobIfServiceStillThere() throws Exception {
    when(wingsPersistence.get(AppContainer.class, ENTITY_ID)).thenReturn(anAppContainer().build());

    JobExecutionContext context = mock(JobExecutionContext.class);
    when(context.getJobDetail()).thenReturn(details(AppContainer.class, ENTITY_ID, FileBucket.PLATFORMS));
    when(context.getPreviousFireTime()).thenReturn(new Date());

    job.execute(context);

    verify(fileService, times(0)).deleteAllFilesForEntity(ENTITY_ID, FileBucket.PLATFORMS);
    verify(jobScheduler, times(1)).deleteJob(ENTITY_ID, PruneFileJob.GROUP);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void UnhandledClass() throws Exception {
    when(wingsPersistence.get(Base.class, ENTITY_ID)).thenReturn(null);

    JobExecutionContext context = mock(JobExecutionContext.class);
    when(context.getJobDetail()).thenReturn(details(Base.class, ENTITY_ID, FileBucket.PLATFORMS));

    ListAppender<ILoggingEvent> listAppender = initLogger(PruneFileJob.class);

    job.execute(context);

    assertThat(listAppender.list).hasSize(1);
    assertThat(listAppender.list.get(0).getFormattedMessage())
        .isEqualTo(String.format("Unsupported class [%s] was scheduled for pruning.", Base.class.getCanonicalName()));
    verify(fileService, times(0)).deleteAllFilesForEntity(ENTITY_ID, FileBucket.PLATFORMS);
    verify(jobScheduler, times(1)).deleteJob(ENTITY_ID, PruneFileJob.GROUP);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void WrongClass() throws Exception {
    JobExecutionContext context = mock(JobExecutionContext.class);
    when(context.getJobDetail()).thenReturn(details(Base.class, ENTITY_ID, FileBucket.PLATFORMS));

    job.execute(context);

    verify(fileService, times(0)).deleteAllFilesForEntity(ENTITY_ID, FileBucket.PLATFORMS);
    verify(jobScheduler, times(1)).deleteJob(ENTITY_ID, PruneFileJob.GROUP);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void retryIfServiceThrew() throws Exception {
    when(wingsPersistence.get(AppContainer.class, ENTITY_ID)).thenReturn(null);

    doThrow(new WingsException("Forced exception"))
        .when(fileService)
        .deleteAllFilesForEntity(ENTITY_ID, FileBucket.PLATFORMS);

    JobExecutionContext context = mock(JobExecutionContext.class);
    when(context.getJobDetail()).thenReturn(details(AppContainer.class, ENTITY_ID, FileBucket.PLATFORMS));

    job.execute(context);

    verify(jobScheduler, times(0)).deleteJob(ENTITY_ID, PruneFileJob.GROUP);
  }

  private <T> ListAppender<ILoggingEvent> initLogger(Class<T> aClass) {
    Logger logger = (Logger) LoggerFactory.getLogger(aClass);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    return listAppender;
  }
}
