/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional;

import static io.harness.generator.AccountGenerator.Accounts.GENERIC_TEST;

import static software.wings.beans.Account.Builder.anAccount;

import static java.time.Duration.ofMinutes;

import io.harness.beans.FeatureName;
import io.harness.exception.GeneralException;
import io.harness.ff.FeatureFlagService;
import io.harness.filesystem.FileIo;
import io.harness.generator.AccountGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.resource.Project;

import software.wings.beans.Account;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.util.Collection;

@Singleton
public class AccountSetupService {
  private static final Collection<FeatureName> DEFAULT_ENABLED_FEATURES =
      ImmutableList.of(FeatureName.HELM_CHART_AS_ARTIFACT, FeatureName.EXPORT_TF_PLAN,
          FeatureName.HELM_STEADY_STATE_CHECK_1_16, FeatureName.CUSTOM_MANIFEST);

  @Inject OwnerManager ownerManager;
  @Inject private AccountGenerator accountGenerator;
  @Inject FeatureFlagService featureFlagService;

  Account ensureAccount() {
    String directoryPath = Project.rootDirectory(AbstractFunctionalTest.class);
    final File lockfile = new File(directoryPath, GENERIC_TEST.name());
    try {
      if (FileIo.acquireLock(lockfile, ofMinutes(2))) {
        Account accountObj = anAccount().withAccountName("Harness").withCompanyName("Harness").build();

        final Account account = accountGenerator.exists(accountObj);
        if (account != null) {
          return account;
        }
        final Seed seed = new Seed(0);
        Owners owners = ownerManager.create();
        return enableFeatureFlags(
            accountGenerator.ensurePredefined(seed, owners, GENERIC_TEST), DEFAULT_ENABLED_FEATURES);
      }
    } finally {
      FileIo.releaseLock(lockfile);
    }
    throw new GeneralException("Unknown error occurred during account setup");
  }

  Account enableFeatureFlags(Account account, Collection<FeatureName> featureNameList) {
    for (FeatureName ff : featureNameList) {
      featureFlagService.enableAccount(ff, account.getUuid());
    }

    return account;
  }
}
