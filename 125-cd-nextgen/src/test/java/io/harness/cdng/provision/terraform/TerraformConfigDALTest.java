/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import dev.morphia.query.Query;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class TerraformConfigDALTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private HPersistence persistence;
  @Mock private CDExpressionResolver cdExpressionResolver;
  @InjectMocks private TerraformConfigDAL terraformConfigDAL;

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testObtainTaskSkippedRollback() {
    TerraformConfig terraformConfig =
        TerraformConfig.builder().backendConfig("${ngSecretManager.obtain(\"accessKey\", 123)}").build();

    doReturn(null).when(persistence).save(terraformConfig);
    ArgumentCaptor<TerraformConfig> terraformConfigArgumentCaptor = ArgumentCaptor.forClass(TerraformConfig.class);

    terraformConfigDAL.saveTerraformConfig(terraformConfig);
    verify(persistence, times(1)).save(terraformConfigArgumentCaptor.capture());
    TerraformConfig terraformConfigCaptured = terraformConfigArgumentCaptor.getValue();
    assertThat(terraformConfigCaptured.getBackendConfig()).isEqualTo("<+secrets.getValue(\"accessKey\")>");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetTerraformConfig() {
    Query mockQuery = Mockito.mock(Query.class);
    doReturn(null).when(mockQuery).get();
    TerraformConfig terraformConfig = terraformConfigDAL.getTerraformConfig(mockQuery, getAmbiance());
    assertThat(terraformConfig).isEqualTo(null);

    TerraformConfig config = TerraformConfig.builder().build();
    doReturn(config).when(mockQuery).get();
    terraformConfig = terraformConfigDAL.getTerraformConfig(mockQuery, getAmbiance());
    assertThat(terraformConfig).isEqualTo(config);
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .build();
  }
}
