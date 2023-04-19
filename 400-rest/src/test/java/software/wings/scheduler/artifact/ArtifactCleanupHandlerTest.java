/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler.artifact;

import static io.harness.rule.OwnerRule.YOGESH;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.rule.Owner;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;
import io.harness.workers.background.iterator.ArtifactCleanupHandler;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDC)
public class ArtifactCleanupHandlerTest extends WingsBaseTest {
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @InjectMocks @Inject ArtifactCleanupHandler artifactCleanupHandler;
  @Inject private AccountService accountService;
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Ignore("Disable top running CG tests")
  public void testRegisterIterators() {
    AccountStatusBasedEntityProcessController<ArtifactStream> accountStatusBasedEntityProcessController =
        new AccountStatusBasedEntityProcessController<>(accountService);
    // setup mock
    when(persistenceIteratorFactory.createIterator(any(), any()))
        .thenReturn(MongoPersistenceIterator.<ArtifactStream, MorphiaFilterExpander<ArtifactStream>>builder()
                        .entityProcessController(accountStatusBasedEntityProcessController)
                        .filterExpander(query
                            -> query.field(ArtifactStreamKeys.artifactStreamType)
                                   .in(asList(ArtifactStreamType.DOCKER.name(), ArtifactStreamType.AMI.name(),
                                       ArtifactStreamType.ARTIFACTORY.name(), ArtifactStreamType.ECR.name(),
                                       ArtifactStreamType.GCR.name())))
                        .build());

    artifactCleanupHandler.createAndStartIterator(PersistenceIteratorFactory.PumpExecutorOptions.builder()
                                                      .name("ArtifactCleanup")
                                                      .poolSize(5)
                                                      .interval(Duration.ofMinutes(5))
                                                      .build(),
        Duration.ofHours(2));

    verify(persistenceIteratorFactory)
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(ArtifactCleanupHandler.class), any());
  }
}
