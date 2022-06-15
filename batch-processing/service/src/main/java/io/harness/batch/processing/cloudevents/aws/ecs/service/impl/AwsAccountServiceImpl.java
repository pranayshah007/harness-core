/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.impl;

import io.harness.batch.processing.cloudevents.aws.ecs.service.intfc.AwsAccountService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AWSOrganizationHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsECSHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsHelperResourceService;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.ccm.setup.util.InfraSetUpUtils;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.ce.CEAwsConfig;

import com.amazonaws.services.organizations.model.Account;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AwsAccountServiceImpl implements AwsAccountService {
  private final BatchMainConfig mainConfig;
  private final AwsHelperResourceService awsHelperResourceService;
  private final AwsECSHelperService awsECSHelperService;
  private final CECloudAccountDao ceCloudAccountDao;
  private final AWSOrganizationHelperService awsOrganizationHelperService;

  @Autowired
  public AwsAccountServiceImpl(BatchMainConfig mainConfig, AwsHelperResourceService awsHelperResourceService,
      AwsECSHelperService awsECSHelperService, CECloudAccountDao ceCloudAccountDao,
      AWSOrganizationHelperService awsOrganizationHelperService) {
    this.mainConfig = mainConfig;
    this.awsHelperResourceService = awsHelperResourceService;
    this.awsECSHelperService = awsECSHelperService;
    this.ceCloudAccountDao = ceCloudAccountDao;
    this.awsOrganizationHelperService = awsOrganizationHelperService;
  }

  @Value
  private static class AccountIdentifierKey {
    String accountId;
    String infraAccountId;
    String infraMasterAccountId;
  }

  @Override
  public void syncLinkedAccounts(String accountId, String settingId, CEAwsConfig ceAwsConfig) {
    List<CECloudAccount> linkedAccounts = getLinkedAccounts(accountId, settingId, ceAwsConfig);
    updateLinkedAccounts(accountId, settingId, ceAwsConfig.getAwsAccountId(), linkedAccounts);
  }

  private List<CECloudAccount> getLinkedAccounts(String accountId, String settingId, CEAwsConfig ceAwsConfig) {
    List<CECloudAccount> ceCloudAccounts = new ArrayList<>();
    List<Account> accountList =
        awsOrganizationHelperService.listAwsAccounts(ceAwsConfig.getAwsCrossAccountAttributes());
    String masterAwsAccountId =
        getMasterAccountIdFromArn(ceAwsConfig.getAwsCrossAccountAttributes().getCrossAccountRoleArn());
    String externalId = ceAwsConfig.getAwsCrossAccountAttributes().getExternalId();
    accountList.forEach(account -> {
      String awsAccountId = getAccountIdFromArn(account.getArn());
      if (!awsAccountId.equals(masterAwsAccountId)) {
        AwsCrossAccountAttributes linkedAwsCrossAccountAttributes =
            AwsCrossAccountAttributes.builder()
                .externalId(externalId)
                .crossAccountRoleArn(InfraSetUpUtils.getLinkedAccountArn(
                    awsAccountId, mainConfig.getBillingDataPipelineConfig().getAwsRoleName()))
                .build();
        CECloudAccount cloudAccount = CECloudAccount.builder()
                                          .accountId(accountId)
                                          .accountArn(account.getArn())
                                          .accountStatus(CECloudAccount.AccountStatus.NOT_VERIFIED)
                                          .accountName(account.getName())
                                          .infraAccountId(awsAccountId)
                                          .infraMasterAccountId(ceAwsConfig.getAwsMasterAccountId())
                                          .masterAccountSettingId(settingId)
                                          .awsCrossAccountAttributes(linkedAwsCrossAccountAttributes)
                                          .build();
        ceCloudAccounts.add(cloudAccount);
      }
    });
    log.info("CE cloud account {}", ceCloudAccounts.size());
    return ceCloudAccounts;
  }

  protected void updateLinkedAccounts(
      String accountId, String settingId, String infraMasterAccountId, List<CECloudAccount> infraAccounts) {
    Map<AccountIdentifierKey, CECloudAccount> infraAccountMap = createAccountMap(infraAccounts);

    List<CECloudAccount> ceExistingAccounts =
        ceCloudAccountDao.getByMasterAccountId(accountId, settingId, infraMasterAccountId);
    Map<AccountIdentifierKey, CECloudAccount> ceExistingAccountMap = createAccountMap(ceExistingAccounts);

    infraAccountMap.forEach((accountIdentifierKey, ceCloudAccount) -> {
      if (!ceExistingAccountMap.containsKey(accountIdentifierKey)) {
        ceCloudAccountDao.create(ceCloudAccount);
      }
    });

    ceExistingAccountMap.forEach((accountIdentifierKey, ceCloudAccount) -> {
      if (!infraAccountMap.containsKey(accountIdentifierKey)) {
        ceCloudAccountDao.deleteAccount(ceCloudAccount.getUuid());
      }
    });
  }

  private Map<AccountIdentifierKey, CECloudAccount> createAccountMap(List<CECloudAccount> cloudAccounts) {
    return cloudAccounts.stream().collect(Collectors.toMap(cloudAccount
        -> new AccountIdentifierKey(
            cloudAccount.getAccountId(), cloudAccount.getInfraAccountId(), cloudAccount.getInfraMasterAccountId()),
        Function.identity()));
  }

  public static String getAccountIdFromArn(String arn) {
    return StringUtils.substringAfterLast(arn, "/");
  }

  public static String getMasterAccountIdFromArn(String arn) {
    return StringUtils.substringBefore(StringUtils.substringAfterLast(arn, "iam::"), ":");
  }
}
