package io.harness.ng.core.api.impl;

import java.util.List;

import io.harness.ng.core.api.JwtVerificationApiKeyService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.JWTVerificationApiKeyDTO;

public class JwtVerificationApiKeyServiceImpl implements JwtVerificationApiKeyService {
  @Override
  public JWTVerificationApiKeyDTO createJwtVerificationApiKey(JWTVerificationApiKeyDTO apiKeyDTO) {
    return null;
  }

  @Override
  public JWTVerificationApiKeyDTO updateJwtVerificationApiKey(JWTVerificationApiKeyDTO apiKeyDTO) {
    return null;
  }

  @Override
  public boolean deleteJwtVerificationApiKey(String accountIdentifier, String orgIdentifier, String projectIdentifier, String parentIdentifier, String identifier) {
    return false;
  }

  @Override
  public List<JWTVerificationApiKeyDTO> listJwtVerificationApiKeys(String accountIdentifier, String orgIdentifier, String projectIdentifier, ApiKeyType apiKeyType, String parentIdentifier) {
    return null;
  }

  @Override
  public JWTVerificationApiKeyDTO getJwtVerificationApiKey(String accountIdentifier, String orgIdentifier, String projectIdentifier, String parentIdentifier, String identifier) {
    return null;
  }
}
