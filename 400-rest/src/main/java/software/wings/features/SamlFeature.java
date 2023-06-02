/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.features;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.OauthProviderType;

import software.wings.beans.AccountType;
import software.wings.beans.sso.SamlSettings;
import software.wings.features.api.AbstractPremiumFeature;
import software.wings.features.api.ComplianceByRemovingUsage;
import software.wings.features.api.FeatureRestrictions;
import software.wings.features.api.Usage;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SSOService;
import software.wings.service.intfc.SSOSettingService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

@Singleton
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
@OwnedBy(HarnessTeam.PL)
public class SamlFeature extends AbstractPremiumFeature implements ComplianceByRemovingUsage {
  public static final String FEATURE_NAME = "SAML";

  private final SSOService ssoService;
  private final SSOSettingService ssoSettingService;

  @Inject
  public SamlFeature(AccountService accountService, FeatureRestrictions featureRestrictions, SSOService ssoService,
      SSOSettingService ssoSettingService) {
    super(accountService, featureRestrictions);
    this.ssoService = ssoService;
    this.ssoSettingService = ssoSettingService;
  }

  @Override
  public boolean removeUsageForCompliance(String accountId, String targetAccountType) {
    if (isUsageCompliantWithRestrictions(accountId, targetAccountType)) {
      return true;
    }
    ssoService.deleteSamlConfiguration(accountId);

    if (!accountService.get(accountId).isOauthEnabled() && targetAccountType.equals(AccountType.COMMUNITY)) {
      ssoService.uploadOauthConfiguration(accountId, "", EnumSet.allOf(OauthProviderType.class), false);
      ssoService.setAuthenticationMechanism(accountId, AuthenticationMechanism.OAUTH, false);
    }

    return isUsageCompliantWithRestrictions(accountId, targetAccountType);
  }

  @Override
  public boolean isBeingUsed(String accountId) {
    return getSamlSettings(accountId) != null;
  }

  private SamlSettings getSamlSettings(String accountId) {
    return ssoSettingService.getSamlSettingsByAccountId(accountId);
  }

  @Override
  public Collection<Usage> getDisallowedUsages(String accountId, String targetAccountType) {
    if (isAvailable(targetAccountType)) {
      return Collections.emptyList();
    }
    if (!isBeingUsed(accountId)) {
      return Collections.emptyList();
    }

    SamlSettings samlSettings = getSamlSettings(accountId);
    return Collections.singletonList(Usage.builder()
                                         .entityId(samlSettings.getAccountId())
                                         .entityType(samlSettings.getDisplayName())
                                         .entityName(samlSettings.getType().name())
                                         .build());
  }

  @Override
  public String getFeatureName() {
    return FEATURE_NAME;
  }
}
