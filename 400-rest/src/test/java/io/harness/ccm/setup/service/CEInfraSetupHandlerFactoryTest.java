/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.setup.service;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.setup.service.impl.AwsCEInfraSetupHandler;
import io.harness.rule.Owner;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.settings.SettingValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CE)
public class CEInfraSetupHandlerFactoryTest extends CategoryTest {
  private CEInfraSetupHandlerFactory ceInfraSetupHandlerFactory;

  @Mock private AwsCEInfraSetupHandler awsCEInfraSetupHandler;

  private final String AWS_ACCOUNT_ID = "424324243";
  private final String AWS_MASTER_ACCOUNT_ID = "awsMasterAccountId";

  @Before
  public void setUp() throws Exception {
    ceInfraSetupHandlerFactory = new CEInfraSetupHandlerFactory(awsCEInfraSetupHandler);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetAwsCEInfraSetupHandler() {
    SettingValue settingValue =
        CEAwsConfig.builder()
            .awsAccountId(AWS_ACCOUNT_ID)
            .awsMasterAccountId(AWS_MASTER_ACCOUNT_ID)
            .awsCrossAccountAttributes(AwsCrossAccountAttributes.builder()
                                           .crossAccountRoleArn("arn:aws:iam::454324243:role/harness_master_account")
                                           .externalId("externalId")
                                           .build())
            .build();

    CEInfraSetupHandler ceInfraSetupHandler = ceInfraSetupHandlerFactory.getCEInfraSetupHandler(settingValue);
    assertThat(ceInfraSetupHandler).isInstanceOf(AwsCEInfraSetupHandler.class);
  }
}
