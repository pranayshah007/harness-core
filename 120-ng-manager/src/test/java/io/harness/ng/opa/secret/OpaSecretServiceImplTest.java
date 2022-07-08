/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.secret;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.opa.OpaService;
import io.harness.ng.opa.entities.secret.OpaSecretServiceImpl;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class OpaSecretServiceImplTest extends NgManagerTestBase {
  @Mock private OpaService opaService;
  @Mock private AccountClient accountClient;

  private OpaSecretServiceImpl opaSecretService;

  @Before
  public void setup() throws Exception {
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);

    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));

    opaSecretService = new OpaSecretServiceImpl(opaService, accountClient);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluatePolicies() throws IOException {
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder().name("name").identifier("id").build();

    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().build();
    when(opaService.evaluate(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(governanceMetadata);

    GovernanceMetadata finalGovernanceMetadata = opaSecretService.evaluatePoliciesWithEntity(
        "accountId", secretDTOV2, "orgIdentifier", "projectIdentifier", "action", "identifier");

    assertThat(finalGovernanceMetadata).isNotNull();
    assertThat(finalGovernanceMetadata.getId()).isBlank();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testEvaluatePolicies2() {
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder().name("name").identifier("id").build();

    when(opaService.evaluate(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(null);
    GovernanceMetadata finalGovernanceMetadata = opaSecretService.evaluatePoliciesWithEntity(
        "accountId", secretDTOV2, "orgIdentifier", "projectIdentifier", "action", "identifier");

    assertThat(finalGovernanceMetadata).isNull();
  }
}
