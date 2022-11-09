package io.harness.ng.core.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import java.util.List;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.JWTVerificationApiKeyDTO;

@OwnedBy(PL)
public interface JwtVerificationApiKeyService {
  JWTVerificationApiKeyDTO createJwtVerificationApiKey(JWTVerificationApiKeyDTO apiKeyDTO);

  JWTVerificationApiKeyDTO updateJwtVerificationApiKey(JWTVerificationApiKeyDTO apiKeyDTO);

  boolean deleteJwtVerificationApiKey(String accountIdentifier, String orgIdentifier, String projectIdentifier,
                                      String parentIdentifier, String identifier);

  List<JWTVerificationApiKeyDTO> listJwtVerificationApiKeys(String accountIdentifier, String orgIdentifier, String projectIdentifier,
                                                            ApiKeyType apiKeyType, String parentIdentifier);

  JWTVerificationApiKeyDTO getJwtVerificationApiKey(String accountIdentifier, String orgIdentifier, String projectIdentifier,
                                                    String parentIdentifier, String identifier);




}
