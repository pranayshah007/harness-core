/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler.artifact;

import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.rule.Owner;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;
import io.harness.workers.background.critical.iterator.ArtifactCollectionHandler;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.PermitService;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class ArtifactCollectionHandlerTest extends WingsBaseTest {
  private static final String ARTIFACT_STREAM_ID = "ARTIFACT_STREAM_ID";

  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private PermitService permitService;
  @Mock private HarnessMetricRegistry harnessMetricRegistry;
  @InjectMocks @Inject private ArtifactCollectionHandler artifactCollectionHandler;
  @Inject private AccountService accountService;

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleWithValidAccountId() {
    try (MockedStatic<ExceptionLogger> mockedStatic = Mockito.mockStatic(ExceptionLogger.class)) {
      ArtifactStream ARTIFACT_STREAM = DockerArtifactStream.builder()
                                           .uuid(ARTIFACT_STREAM_ID)
                                           .sourceName(ARTIFACT_STREAM_NAME)
                                           .appId(APP_ID)
                                           .settingId(SETTING_ID)
                                           .serviceId(SERVICE_ID)
                                           .imageName("image_name")
                                           .accountId(ACCOUNT_ID)
                                           .build();

      when(permitService.acquirePermit(any())).thenThrow(new WingsException("Exception"));
      artifactCollectionHandler.handle(ARTIFACT_STREAM);

      ArgumentCaptor<WingsException> argumentCaptor = ArgumentCaptor.forClass(WingsException.class);
      mockedStatic.verify(() -> ExceptionLogger.logProcessedMessages(argumentCaptor.capture(), any(), any()));
      WingsException wingsException = argumentCaptor.getValue();

      assertThat(wingsException).isNotNull();
      assertThat(wingsException.calcRecursiveContextObjects().values().size()).isEqualTo(4);
      assertThat(wingsException.calcRecursiveContextObjects().values()).contains(ACCOUNT_ID, ARTIFACT_STREAM_ID);
    }
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    AccountStatusBasedEntityProcessController<ArtifactStream> accountStatusBasedEntityProcessController =
        new AccountStatusBasedEntityProcessController<>(accountService);
    // setup mock
    when(persistenceIteratorFactory.createIterator(any(), any()))
        .thenReturn(MongoPersistenceIterator.<ArtifactStream, MorphiaFilterExpander<ArtifactStream>>builder()
                        .entityProcessController(accountStatusBasedEntityProcessController)
                        .build());

    MetricRegistry metricRegistry = mock(MetricRegistry.class);
    when(harnessMetricRegistry.getThreadPoolMetricRegistry()).thenReturn(metricRegistry);

    artifactCollectionHandler.createAndStartIterator(PersistenceIteratorFactory.PumpExecutorOptions.builder()
                                                         .name("ArtifactCollection")
                                                         .poolSize(20)
                                                         .interval(Duration.ofSeconds(10))
                                                         .build(),
        Duration.ofMinutes(1));

    verify(persistenceIteratorFactory)
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(ArtifactCollectionHandler.class), any());
  }
}
