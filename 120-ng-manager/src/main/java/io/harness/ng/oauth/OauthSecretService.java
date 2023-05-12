/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static java.lang.String.format;

import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OauthSecretService {
  @Inject SecretCrudService ngSecretService;
  @Inject NgUserService ngUserService;

  String oauthAccessTokenSecretName = "harnessoauthaccesstoken_%s_%s";
  String oauthRefreshTokenSecretName = "harnessoauthsecrettoken_%s_%s";

  public OauthAccessTokenResponseDTO createSecrets(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String provider, OauthAccessTokenDTO accessToken, String secretManagerIdentifier,
      boolean isPrivateSecret, UserDetailsDTO userDetailsDTO) {
    SecretTextSpecDTO accessTokenSecretDTO = SecretTextSpecDTO.builder()
                                                 .secretManagerIdentifier(secretManagerIdentifier)
                                                 .value(accessToken.getAccessToken())
                                                 .valueType(ValueType.Inline)
                                                 .build();
    SecretTextSpecDTO refreshTokenSecretDTO = SecretTextSpecDTO.builder()
                                                  .secretManagerIdentifier(secretManagerIdentifier)
                                                  .value(accessToken.getRefreshToken())
                                                  .valueType(ValueType.Inline)
                                                  .build();
    String randomUUID = UUID.randomUUID().toString();
    SecretDTOV2 accessTokenSecretDTOV2 =
        SecretDTOV2.builder()
            .identifier(format(oauthAccessTokenSecretName, provider, (new Date()).getTime()))
            .name(format("Harness-Oauth-access-token-%s", randomUUID))
            .spec(accessTokenSecretDTO)
            .type(SecretType.SecretText)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .build();
    Optional<UserMetadataDTO> userMetadataDTO = Optional.empty();
    if (isPrivateSecret) {
      userMetadataDTO = ngUserService.getUserByEmail(userDetailsDTO.getUserEmail(), false);
      if (!userMetadataDTO.isPresent()) {
        log.error("Failed to get user details for user email: {}", userDetailsDTO.getUserEmail());
        throw new InvalidRequestException(
            String.format("Failed to get user details for user email: %s", userDetailsDTO.getUserEmail()));
      }
      accessTokenSecretDTOV2.setOwner(new UserPrincipal(userMetadataDTO.get().getUuid(), userDetailsDTO.getUserEmail(),
          userMetadataDTO.get().getName(), accountIdentifier));
    }
    SecretResponseWrapper accessTokenResponse = ngSecretService.create(accountIdentifier, accessTokenSecretDTOV2);

    // github doesn't provides refresh token
    if (provider.equals("github")) {
      return OauthAccessTokenResponseDTO.builder()
          .accessTokenRef(accessTokenResponse.getSecret().getIdentifier())
          .build();
    }

    SecretDTOV2 refreshTokenSecretDTOV2 =
        SecretDTOV2.builder()
            .identifier(format(oauthRefreshTokenSecretName, provider, (new Date()).getTime()))
            .name(format("Harness-Oauth-refresh-token-%s", randomUUID))
            .spec(refreshTokenSecretDTO)
            .type(SecretType.SecretText)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .build();
    if (isPrivateSecret) {
      refreshTokenSecretDTOV2.setOwner(new UserPrincipal(userMetadataDTO.get().getUuid(), userDetailsDTO.getUserEmail(),
          userMetadataDTO.get().getName(), accountIdentifier));
    }
    SecretResponseWrapper refreshTokenResponse = ngSecretService.create(accountIdentifier, refreshTokenSecretDTOV2);
    return OauthAccessTokenResponseDTO.builder()
        .accessTokenRef(accessTokenResponse.getSecret().getIdentifier())
        .refreshTokenRef(refreshTokenResponse.getSecret().getIdentifier())
        .build();
  }
}
