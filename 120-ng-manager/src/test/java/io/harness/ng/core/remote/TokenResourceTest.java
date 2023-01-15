/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.api.impl.ApiKeyServiceImpl;
import io.harness.ng.core.api.impl.TokenServiceImpl;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TokenResourceTest extends CategoryTest {
  private TokenService tokenService;
  private ApiKeyService apiKeyService;
  private TokenResource tokenResource;

  @Before
  public void Setup() {
    tokenService = mock(TokenServiceImpl.class);
    apiKeyService = mock(ApiKeyServiceImpl.class);
    tokenResource = new TokenResource(tokenService, apiKeyService);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testValidateToken_ReturnsTokenDTOSuccessfully() {
    String accoundIdentifier = "test_AccountId";
    String apiKey = "test_ApiKey";
    TokenDTO tokenDTO = TokenDTO.builder().build();
    doReturn(tokenDTO).when(tokenService).validateToken(anyString(), anyString());
    ResponseDTO<TokenDTO> tokenDTOResponseDTO = tokenResource.validateToken(accoundIdentifier, apiKey);
    assertThat(tokenDTOResponseDTO.getData()).isEqualTo(tokenDTO);
  }
}