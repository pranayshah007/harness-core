/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.YamlGitConfigKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(DX)
public class GitSyncPollingIterator
    extends IteratorPumpModeHandler implements MongoPersistenceIterator.Handler<YamlGitConfig> {
  @Inject PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject AccountService accountService;
  @Inject private MorphiaPersistenceProvider<YamlGitConfig> persistenceProvider;
  @Inject private FeatureFlagService featureFlagService;
  @Inject YamlChangeSetService yamlChangeSetService;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<YamlGitConfig, MorphiaFilterExpander<YamlGitConfig>>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, YamlGitConfig.class,
                MongoPersistenceIterator.<YamlGitConfig, MorphiaFilterExpander<YamlGitConfig>>builder()
                    .clazz(YamlGitConfig.class)
                    .fieldName(YamlGitConfigKeys.gitPollingIterator)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ofMinutes(1))
                    .acceptableExecutionTime(ofMinutes(1))
                    .handler(this)
                    .schedulingType(REGULAR)
                    .persistenceProvider(persistenceProvider)
                    .redistribute(true));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "GitSyncPollingIterator";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(YamlGitConfig entity) {
    yamlChangeSetService.pushYamlChangeSetForGitToHarness(entity.getAccountId(), entity.getBranchName(),
        entity.getGitConnectorId(), entity.getRepositoryName(), entity.getAppId());
  }
}
