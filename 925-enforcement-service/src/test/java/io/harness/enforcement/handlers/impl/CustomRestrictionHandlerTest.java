/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.handlers.impl;

import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.bases.CustomRestriction;
import io.harness.enforcement.beans.details.FeatureRestrictionDetailsDTO;
import io.harness.enforcement.beans.metadata.CustomRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.exceptions.FeatureNotSupportedException;
import io.harness.enforcement.services.impl.EnforcementSdkClient;
import io.harness.licensing.Edition;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Call;
import retrofit2.Response;

public class CustomRestrictionHandlerTest extends CategoryTest {
  private CustomRestrictionHandler handler;
  private FeatureRestrictionName featureRestrictionName = FeatureRestrictionName.TEST1;
  private CustomRestriction restriction;
  private Call<ResponseDTO<Boolean>> evaluateCustomCall;
  private String accountIdentifier = "accountId";
  private ModuleType moduleType = ModuleType.CD;
  private Edition edition = Edition.ENTERPRISE;

  @Before
  public void setup() {
    handler = new CustomRestrictionHandler();
    EnforcementSdkClient client = mock(EnforcementSdkClient.class);
    evaluateCustomCall = mock(Call.class);
    when(client.evaluateCustomFeatureRestriction(any(), any(), any())).thenReturn(evaluateCustomCall);
    restriction = new CustomRestriction(RestrictionType.CUSTOM, "test", client);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCheck() throws IOException {
    when(evaluateCustomCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Boolean.TRUE)));
    handler.check(featureRestrictionName, restriction, accountIdentifier, moduleType, edition);
  }

  @Test(expected = FeatureNotSupportedException.class)
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCheckFailed() throws IOException {
    when(evaluateCustomCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Boolean.FALSE)));
    handler.check(featureRestrictionName, restriction, accountIdentifier, moduleType, edition);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testFillRestrictionDTO() throws IOException {
    when(evaluateCustomCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Boolean.TRUE)));
    FeatureRestrictionDetailsDTO dto = FeatureRestrictionDetailsDTO.builder().build();
    handler.fillRestrictionDTO(featureRestrictionName, restriction, accountIdentifier, edition, dto);

    assertThat(dto.getRestrictionType()).isEqualTo(RestrictionType.CUSTOM);
    assertThat(dto.isAllowed()).isTrue();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testGetMetadataDTO() {
    RestrictionMetadataDTO metadataDTO = handler.getMetadataDTO(restriction, null, null);

    CustomRestrictionMetadataDTO dto = (CustomRestrictionMetadataDTO) metadataDTO;
    assertThat(dto.getRestrictionType()).isEqualTo(RestrictionType.CUSTOM);
  }
}
