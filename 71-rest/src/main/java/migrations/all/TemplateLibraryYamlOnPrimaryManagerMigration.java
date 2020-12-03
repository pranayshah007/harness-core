package migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureFlag.FeatureFlagKeys;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.yaml.sync.YamlService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import migrations.OnPrimaryManagerMigration;

@Slf4j
public class TemplateLibraryYamlOnPrimaryManagerMigration implements OnPrimaryManagerMigration {
  private static final String DEBUG_LINE = "TEMPLATE_YAML_SUPPORT: ";
  private static final String FEATURE_FLAG_NAME = "TEMPLATE_YAML_SUPPORT";

  @Inject YamlService yamlService;
  @Inject WingsPersistence wingsPersistence;
  @Inject FeatureFlagService featureFlagService;

  @Override
  public void migrate() {
    log.info(String.join(DEBUG_LINE, " Starting Migration For Template Library Yaml"));
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();
        if (wingsPersistence.createQuery(FeatureFlag.class, excludeAuthority)
                .filter(FeatureFlagKeys.name, FEATURE_FLAG_NAME)
                .get()
            == null) {
          log.info(String.join(
              DEBUG_LINE, " Starting Migration For Template Library Yaml for account", account.getAccountName()));
          yamlService.syncYamlTemplate(account.getUuid());
        } else {
          log.info(String.join(DEBUG_LINE,
              " Migration For Template Library Yaml for account {} already completed in last migration.",
              account.getAccountName()));
        }
      }
    }
    log.info(String.join(DEBUG_LINE, " Completed triggering migration for Template Library Yaml"));
  }
}
