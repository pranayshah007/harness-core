package software.wings.scheduler.account;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import software.wings.app.JobsFrequencyConfig;
import software.wings.beans.Account;
import software.wings.service.intfc.AccountService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class NameChangeAccountHandler implements MongoPersistenceIterator.Handler<Account> {
  // Cache of account id to account name
  private final Cache<String, String> caffeineAccountCache = Caffeine.newBuilder().maximumSize(50000).build();
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private AccountService accountService;
  @Inject private JobsFrequencyConfig jobsFrequencyConfig;
  @Inject private MorphiaPersistenceProvider<Account> persistenceProvider;
  @Inject private Provider<CfClient> cfClient;

  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("AccountNameChange")
            .poolSize(2)
            .interval(ofMinutes(1))
            .build(),
        NameChangeAccountHandler.class,
        MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
            .clazz(Account.class)
            .fieldName(Account.AccountKeys.accountNameChangeIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(300))
            .acceptableExecutionTime(ofSeconds(120))
            .persistenceProvider(persistenceProvider)
            .handler(this)
            .schedulingType(REGULAR)
            .redistribute(true));
  }

  @Override
  public void handle(Account account) {
    String accountId = account.getAccountKey();
    String name = account.getAccountName();
    String oldName = caffeineAccountCache.getIfPresent(accountId);
    if (isEmpty(oldName) || !oldName.equals(name)) {
      caffeineAccountCache.put(accountId, name);
      Target target = Target.builder().identifier(accountId).name(name).build();
      for (FeatureName featureName : FeatureName.values()) {
        cfClient.get().boolVariation(featureName.name(), target, false);
      }
    }
  }
}
