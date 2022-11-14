package io.harness.ng.core.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.JwtVerificationApiKeyDTO;
import io.harness.ng.core.entities.ApiKey;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class JwtVerificationApiKeyMapper {
  public ApiKey toApiKey(JwtVerificationApiKeyDTO dto) {
    ApiKey builtApiKey = ApiKeyDTOMapper.getApiKeyFromDTO(dto);
    builtApiKey.setKeyField(dto.getKeyField());
    builtApiKey.setValueField(dto.getValueField());
    builtApiKey.setCertificateUrl(dto.getCertificateUrl());
    // builtApiKey.setDefaultTimeToExpireToken(0L);
    return builtApiKey;
  }

  public JwtVerificationApiKeyDTO toJwtVerificationApiKeyDTO(ApiKey apiKey) {
    return JwtVerificationApiKeyDTO.builder()
        .identifier(apiKey.getIdentifier())
        .apiKeyType(apiKey.getApiKeyType())
        .parentIdentifier(apiKey.getParentIdentifier())
        .accountIdentifier(apiKey.getAccountIdentifier())
        .orgIdentifier(apiKey.getOrgIdentifier())
        .projectIdentifier(apiKey.getProjectIdentifier())
        .defaultTimeToExpireToken(apiKey.getDefaultTimeToExpireToken())
        .name(apiKey.getName())
        .description(apiKey.getDescription())
        .tags(TagMapper.convertToMap(apiKey.getTags()))
        .keyField(apiKey.getKeyField())
        .valueField(apiKey.getValueField())
        .certificateUrl(apiKey.getCertificateUrl())
        .build();
  }
}
