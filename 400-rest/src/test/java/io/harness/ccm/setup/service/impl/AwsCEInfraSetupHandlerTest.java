/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.setup.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.ccm.setup.service.intfc.AWSAccountService;
import io.harness.ccm.setup.service.intfc.AwsEKSClusterService;
import io.harness.ccm.setup.service.support.intfc.AwsEKSHelperService;
import io.harness.rule.Owner;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsCEInfraSetupHandlerTest extends CategoryTest {
  @InjectMocks private AwsCEInfraSetupHandler awsCEInfraSetupHandler;
  @Mock private CECloudAccountDao ceCloudAccountDao;
  @Mock private AWSAccountService awsAccountService;
  @Mock private AwsEKSClusterService awsEKSClusterService;
  @Mock private AwsEKSHelperService awsEKSHelperService;

  @Captor private ArgumentCaptor<CECloudAccount> ceCloudCreateAccountArgumentCaptor;
  @Captor private ArgumentCaptor<String> ceCloudDeleteAccountArgumentCaptor;

  private String accountId = "ACCOUNT_ID";
  private String deleteRecordUUID = "DELETE_RECORD_UUID";
  private String accountName = "ACCOUNT_NAME";
  private String accountNameDelete = "ACCOUNT_NAME_DELETE";
  private String infraAccountId = "123123112";
  private String infraAccountIdDelete = "4423232112";
  private String infraMasterAccountId = "3243223122";
  private String masterAccountSettingId = "MASTER_SETTING_ID";
  private String accountArn = "arn:aws:organizations::123123112:account/o-tbm3caqef8/3243223122";
  private String accountArnDelete = "arn:aws:organizations::123123112:account/o-tbm3caqef8/4423232112";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testSyncInfra() {
    CEAwsConfig ceAwsConfig =
        CEAwsConfig.builder()
            .awsAccountId(infraMasterAccountId)
            .awsMasterAccountId(infraMasterAccountId)
            .awsAccountType(CEAwsConfig.AWSAccountType.MASTER_ACCOUNT.name())
            .awsCrossAccountAttributes(AwsCrossAccountAttributes.builder()
                                           .crossAccountRoleArn("arn:aws:iam::454324243:role/harness_master_account")
                                           .externalId("externalId")
                                           .build())
            .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withUuid(masterAccountSettingId)
                                            .withAccountId(accountId)
                                            .withCategory(SettingAttribute.SettingCategory.CE_CONNECTOR)
                                            .withValue(ceAwsConfig)
                                            .build();

    CECloudAccount ceCloudAccountSaved = getCECloudAccount(accountNameDelete, accountArnDelete, infraAccountIdDelete);
    ceCloudAccountSaved.setUuid(deleteRecordUUID);
    CECloudAccount ceCloudAccount = getCECloudAccount(accountName, accountArn, infraAccountId);
    List<CECloudAccount> savedCEAccounts = ImmutableList.of(ceCloudAccountSaved);
    List<CECloudAccount> infraAccounts = ImmutableList.of(ceCloudAccount);

    when(ceCloudAccountDao.getByMasterAccountId(accountId, masterAccountSettingId, infraMasterAccountId))
        .thenReturn(savedCEAccounts);
    when(awsAccountService.getAWSAccounts(accountId, masterAccountSettingId, ceAwsConfig)).thenReturn(infraAccounts);

    awsCEInfraSetupHandler.syncCEInfra(settingAttribute);
    verify(ceCloudAccountDao).create(ceCloudCreateAccountArgumentCaptor.capture());
    verify(ceCloudAccountDao).deleteAccount(ceCloudDeleteAccountArgumentCaptor.capture());
    CECloudAccount createCECloudAccount = ceCloudCreateAccountArgumentCaptor.getValue();
    String deleteCECloudAccountUUID = ceCloudDeleteAccountArgumentCaptor.getValue();
    assertThat(createCECloudAccount.getAccountArn()).isEqualTo(accountArn);
    assertThat(deleteCECloudAccountUUID).isEqualTo(deleteRecordUUID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testUpdateAccountPermission() {
    CECloudAccount ceCloudAccount = getCECloudAccount(accountNameDelete, accountArnDelete, infraAccountIdDelete);
    when(awsEKSHelperService.verifyAccess("us-east-1", ceCloudAccount.getAwsCrossAccountAttributes())).thenReturn(true);
    boolean verifyAccess = awsCEInfraSetupHandler.updateAccountPermission(ceCloudAccount);
    assertThat(verifyAccess).isTrue();
  }

  private CECloudAccount getCECloudAccount(String accountName, String accountArn, String infraAccountId) {
    return CECloudAccount.builder()
        .accountId(accountId)
        .accountName(accountName)
        .accountArn(accountArn)
        .infraAccountId(infraAccountId)
        .infraMasterAccountId(infraMasterAccountId)
        .masterAccountSettingId(masterAccountSettingId)
        .build();
  }
}
