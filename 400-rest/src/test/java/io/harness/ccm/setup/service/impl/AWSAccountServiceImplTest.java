/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.setup.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.billing.CECloudAccount.AccountStatus;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.ccm.setup.config.CESetUpConfig;
import io.harness.ccm.setup.service.support.intfc.AWSOrganizationHelperService;
import io.harness.rule.Owner;

import software.wings.app.MainConfiguration;
import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.intfc.SettingsService;

import com.amazonaws.services.organizations.model.Account;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CE)
public class AWSAccountServiceImplTest extends CategoryTest {
  private AWSAccountServiceImpl awsAccountService;
  @Mock private SettingsService settingsService;
  @Mock private CECloudAccountDao ceCloudAccountDao;
  @Mock private AWSOrganizationHelperService awsOrganizationHelperService;
  @Mock private AwsCEInfraSetupHandler awsCEInfraSetupHandler;
  @Mock private MainConfiguration configuration;

  @Captor private ArgumentCaptor<CECloudAccount> ceCloudCreateAccountArgumentCaptor;

  private String ACCOUNT_NAME = "accountName";
  private final String ACCOUNT_ID = "accountId";
  private final String SETTING_ID = "settingId";
  private final String AWS_ACCOUNT_ID = "424324243";
  private final String AWS_MASTER_ACCOUNT_ID = "424324243";
  private String ACCOUNT_ARN = "arn:aws:organizations::424324243:account/o-tbm3caqef8/424324243";

  @Before
  public void setUp() throws Exception {
    awsAccountService = new AWSAccountServiceImpl(
        awsOrganizationHelperService, settingsService, ceCloudAccountDao, awsCEInfraSetupHandler, configuration);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListAWSAccounts() {
    CEAwsConfig ceAwsConfig = getCEAwsConfig();
    doReturn(ImmutableList.of(new Account()
                                  .withName("account_name_1")
                                  .withArn("arn:aws:organizations::424324243:account/o-tbm3caqef8/424324243"),
                 new Account()
                     .withName("account_name_2")
                     .withArn("arn:aws:organizations::424324243:account/o-tbm3caqef8/454324242")))
        .when(awsOrganizationHelperService)
        .listAwsAccounts(ceAwsConfig.getAwsCrossAccountAttributes());

    doReturn(CESetUpConfig.builder().awsRoleName("harnessCERole").build()).when(configuration).getCeSetUpConfig();

    List<CECloudAccount> awsAccounts = awsAccountService.getAWSAccounts(ACCOUNT_ID, SETTING_ID, ceAwsConfig);
    assertThat(awsAccounts).hasSize(1);
    assertThat(awsAccounts.get(0))
        .isEqualTo(CECloudAccount.builder()
                       .accountId(ACCOUNT_ID)
                       .accountArn("arn:aws:organizations::424324243:account/o-tbm3caqef8/454324242")
                       .accountName("account_name_2")
                       .infraAccountId("454324242")
                       .infraMasterAccountId(AWS_MASTER_ACCOUNT_ID)
                       .accountStatus(AccountStatus.NOT_VERIFIED)
                       .masterAccountSettingId(SETTING_ID)
                       .awsCrossAccountAttributes(AwsCrossAccountAttributes.builder()
                                                      .crossAccountRoleArn("arn:aws:iam::454324242:role/harnessCERole")
                                                      .externalId("externalId")
                                                      .build())
                       .build());
  }

  private CEAwsConfig getCEAwsConfig() {
    return CEAwsConfig.builder()
        .awsAccountId(AWS_ACCOUNT_ID)
        .awsMasterAccountId(AWS_MASTER_ACCOUNT_ID)
        .awsCrossAccountAttributes(AwsCrossAccountAttributes.builder()
                                       .crossAccountRoleArn("arn:aws:iam::424324243:role/harness_master_account")
                                       .externalId("externalId")
                                       .build())
        .build();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testUpdateAccountPermission() {
    CEAwsConfig ceAwsConfig = getCEAwsConfig();
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).withValue(ceAwsConfig).build();
    when(settingsService.getByAccountAndId(ACCOUNT_ID, SETTING_ID)).thenReturn(settingAttribute);

    CECloudAccount ceCloudAccount = getCECloudAccount(ACCOUNT_NAME, ACCOUNT_ARN, AWS_ACCOUNT_ID);
    List<CECloudAccount> infraAccounts = ImmutableList.of(ceCloudAccount);

    when(ceCloudAccountDao.getByMasterAccountId(
             settingAttribute.getAccountId(), settingAttribute.getUuid(), ceAwsConfig.getAwsAccountId()))
        .thenReturn(infraAccounts);

    awsAccountService.updateAccountPermission(ACCOUNT_ID, SETTING_ID);
    verify(awsCEInfraSetupHandler).updateAccountPermission(ceCloudCreateAccountArgumentCaptor.capture());
    CECloudAccount createCECloudAccount = ceCloudCreateAccountArgumentCaptor.getValue();
    assertThat(createCECloudAccount).isEqualTo(ceCloudAccount);
  }

  private CECloudAccount getCECloudAccount(String accountName, String accountArn, String infraAccountId) {
    return CECloudAccount.builder()
        .accountId(ACCOUNT_ID)
        .accountName(accountName)
        .accountArn(accountArn)
        .infraAccountId(infraAccountId)
        .infraMasterAccountId(AWS_ACCOUNT_ID)
        .masterAccountSettingId(AWS_MASTER_ACCOUNT_ID)
        .build();
  }
}
