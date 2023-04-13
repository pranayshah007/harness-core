/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.prune;

import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.scheduler.PersistentScheduler;

import software.wings.WingsBaseTest;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.Base;
import software.wings.beans.Environment;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.LogService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

public class PruneEntityListenerTest extends WingsBaseTest {
  @Mock private WingsPersistence wingsPersistence;

  @Mock private LogService logService;
  @Inject @InjectMocks private ActivityService activityService;

  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;

  @Mock private PersistentScheduler jobScheduler;

  @Inject @InjectMocks PruneEntityListener listener;

  private static final String APP_ID = "app_id";
  private static final String ENTITY_ID = "entityId";

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void unhandledClass() throws Exception {
    when(wingsPersistence.get(Base.class, ENTITY_ID)).thenReturn(null);

    ListAppender<ILoggingEvent> listAppender = initLogger(PruneEntityListener.class);

    listener.onMessage(new PruneEvent(Base.class, APP_ID, ENTITY_ID));

    assertThat(listAppender.list).hasSize(2);
    assertThat(listAppender.list.get(1).getFormattedMessage())
        .isEqualTo(String.format("Unsupported class [%s] was scheduled for pruning.", Base.class.getCanonicalName()));
    verify(environmentService, times(0)).pruneDescendingEntities(APP_ID, ENTITY_ID);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void wrongClass() throws Exception {
    listener.onMessage(new PruneEvent("foo", APP_ID, ENTITY_ID));
    verify(environmentService, times(0)).pruneDescendingEntities(APP_ID, ENTITY_ID);
  }
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void retryIfServiceThrew() throws Exception {
    when(wingsPersistence.get(Environment.class, ENTITY_ID)).thenReturn(null);

    doThrow(new WingsException("Forced exception")).when(environmentService).pruneDescendingEntities(APP_ID, ENTITY_ID);

    assertThatThrownBy(() -> listener.onMessage(new PruneEvent(Environment.class, APP_ID, ENTITY_ID)))
        .isInstanceOf(WingsException.class)
        .hasMessage("The prune failed this time");
  }
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void verifyThrowFromDescendingEntity() throws Exception {
    when(wingsPersistence.get(Activity.class, ENTITY_ID)).thenReturn(null);

    WingsException exception = new WingsException(DEFAULT_ERROR_CODE);
    doThrow(exception).when(logService).pruneByActivity(APP_ID, ENTITY_ID);

    assertThatThrownBy(() -> listener.onMessage(new PruneEvent(Activity.class, APP_ID, ENTITY_ID)))
        .isInstanceOf(WingsException.class)
        .hasMessage("The prune failed this time");

    verify(logService, times(1)).pruneByActivity(APP_ID, ENTITY_ID);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void differentAppIdAndObjectIdForApplication() throws Exception {
    assertThatCode(() -> {
      when(wingsPersistence.get(Application.class, ENTITY_ID)).thenReturn(null);

      listener.onMessage(new PruneEvent(Application.class, APP_ID, ENTITY_ID));
    }).doesNotThrowAnyException();
  }

  private <T> ListAppender<ILoggingEvent> initLogger(Class<T> aClass) {
    Logger logger = (Logger) LoggerFactory.getLogger(aClass);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    return listAppender;
  }
}
