/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.setup.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.billing.CECloudAccount.AccountStatus;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.ccm.setup.service.intfc.AWSAccountService;
import io.harness.ccm.setup.service.support.intfc.AWSOrganizationHelperService;
import io.harness.ccm.setup.util.InfraSetUpUtils;

import software.wings.app.MainConfiguration;
import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.intfc.SettingsService;

import com.amazonaws.services.organizations.model.Account;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(CE)
public class AWSAccountServiceImpl implements AWSAccountService {
  private final MainConfiguration configuration;
  private final SettingsService settingsService;
  private final CECloudAccountDao ceCloudAccountDao;
  private final AwsCEInfraSetupHandler awsCEInfraSetupHandler;
  private final AWSOrganizationHelperService awsOrganizationHelperService;

  @Inject
  public AWSAccountServiceImpl(AWSOrganizationHelperService awsOrganizationHelperService,
      SettingsService settingsService, CECloudAccountDao ceCloudAccountDao,
      AwsCEInfraSetupHandler awsCEInfraSetupHandler, MainConfiguration configuration) {
    this.awsOrganizationHelperService = awsOrganizationHelperService;
    this.settingsService = settingsService;
    this.ceCloudAccountDao = ceCloudAccountDao;
    this.awsCEInfraSetupHandler = awsCEInfraSetupHandler;
    this.configuration = configuration;
  }

  @Override
  public List<CECloudAccount> getAWSAccounts(String accountId, String settingId, CEAwsConfig ceAwsConfig) {
    List<CECloudAccount> ceCloudAccounts = new ArrayList<>();
    List<Account> accountList =
        awsOrganizationHelperService.listAwsAccounts(ceAwsConfig.getAwsCrossAccountAttributes());
    String externalId = ceAwsConfig.getAwsCrossAccountAttributes().getExternalId();
    accountList.forEach(account -> {
      String awsAccountId = getAccountIdFromArn(account.getArn());
      AwsCrossAccountAttributes linkedAwsCrossAccountAttributes =
          AwsCrossAccountAttributes.builder()
              .externalId(externalId)
              .crossAccountRoleArn(
                  InfraSetUpUtils.getLinkedAccountArn(awsAccountId, configuration.getCeSetUpConfig().getAwsRoleName()))
              .build();
      CECloudAccount cloudAccount = CECloudAccount.builder()
                                        .accountId(accountId)
                                        .accountArn(account.getArn())
                                        .accountStatus(AccountStatus.NOT_VERIFIED)
                                        .accountName(account.getName())
                                        .infraAccountId(awsAccountId)
                                        .infraMasterAccountId(ceAwsConfig.getAwsMasterAccountId())
                                        .masterAccountSettingId(settingId)
                                        .awsCrossAccountAttributes(linkedAwsCrossAccountAttributes)
                                        .build();
      ceCloudAccounts.add(cloudAccount);
    });
    log.info("CE cloud account {}", ceCloudAccounts);
    return ceCloudAccounts;
  }

  @Override
  public void updateAccountPermission(String accountId, String settingId) {
    updateAccountPermission(settingsService.getByAccountAndId(accountId, settingId));
  }

  @Override
  public void updateAccountPermission(SettingAttribute settingAttribute) {
    if (null != settingAttribute) {
      if (settingAttribute.getValue() instanceof CEAwsConfig) {
        CEAwsConfig ceAwsConfig = (CEAwsConfig) settingAttribute.getValue();
        List<CECloudAccount> ceCloudAccounts = ceCloudAccountDao.getByMasterAccountId(
            settingAttribute.getAccountId(), settingAttribute.getUuid(), ceAwsConfig.getAwsAccountId());
        ceCloudAccounts.forEach(awsCEInfraSetupHandler::updateAccountPermission);
      }
    }
  }

  public static String getAccountIdFromArn(String arn) {
    return StringUtils.substringAfterLast(arn, "/");
  }
}
