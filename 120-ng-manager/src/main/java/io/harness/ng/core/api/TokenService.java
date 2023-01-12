/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenAggregateDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenFilterDTO;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public interface TokenService {
  String createToken(TokenDTO tokenDTO);
  boolean revokeToken(String accountIdentifier, String orgIdentifier, String projectIdentifier, ApiKeyType apiKeyType,
      String parentIdentifier, String apiKeyIdentifier, String identifier);
  String rotateToken(String accountIdentifier, String orgIdentifier, String projectIdentifier, ApiKeyType apiKeyType,
      String parentIdentifier, String apiKeyIdentifier, String identifier, Instant scheduledExpireTime);
  TokenDTO updateToken(TokenDTO tokenDTO);

  Map<String, Integer> getTokensPerApiKeyIdentifier(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ApiKeyType apiKeyType, String parentIdentifier, List<String> apiKeyIdentifiers);

  PageResponse<TokenAggregateDTO> listAggregateTokens(
      String accountIdentifier, Pageable pageable, TokenFilterDTO filterDTO);

  long deleteAllByParentIdentifier(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier);

  long deleteAllByApiKeyIdentifier(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier);
  TokenDTO getToken(String tokenId, boolean withEncodedPassword);

  TokenDTO validateToken(String accountIdentifier, String apiKey);

  Long countApiTokens(String accountIdentifier);
}
