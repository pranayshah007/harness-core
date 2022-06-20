package io.harness.ng.oauth;

import static java.lang.String.format;

import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;

import com.google.inject.Inject;
import java.util.Date;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class OauthSecretService {
  @Inject SecretCrudService ngSecretService;

  String oauthAccessTokenSecretName = "harnessoauthaccesstoken_%s_%s";
  String oauthRefreshTokenSecretName = "harnessoauthsecrettoken_%s_%s";

  public OauthAccessTokenResponseDTO createSecrets(
      String accountIdentifier, String provider, OauthAccessTokenDTO accessToken) {
    SecretTextSpecDTO accessTokenSecretDTO = SecretTextSpecDTO.builder()
                                                 .secretManagerIdentifier("harnessSecretManager")
                                                 .value(accessToken.getAccessToken())
                                                 .valueType(ValueType.Inline)
                                                 .build();
    SecretTextSpecDTO refreshTokenSecretDTO = SecretTextSpecDTO.builder()
                                                  .secretManagerIdentifier("harnessSecretManager")
                                                  .value(accessToken.getRefreshToken())
                                                  .valueType(ValueType.Inline)
                                                  .build();
    SecretResponseWrapper accessTokenResponse = ngSecretService.create(accountIdentifier,
        SecretDTOV2.builder()
            .identifier(format(oauthAccessTokenSecretName, provider, (new Date()).getTime()))
            .name("Harness Oauth access token")
            .spec(accessTokenSecretDTO)
            .type(SecretType.SecretText)
            .build());

    // github doesn't provides refresh token
    if (provider.equals("github")) {
      return OauthAccessTokenResponseDTO.builder()
          .accessTokenRef(accessTokenResponse.getSecret().getIdentifier())
          .build();
    }

    SecretResponseWrapper refreshTokenResponse = ngSecretService.create(accountIdentifier,
        SecretDTOV2.builder()
            .identifier(format(oauthRefreshTokenSecretName, provider, (new Date()).getTime()))
            .name("Harness Oauth refresh token")
            .spec(accessTokenSecretDTO)
            .type(SecretType.SecretText)
            .build());
    return OauthAccessTokenResponseDTO.builder()
        .accessTokenRef(accessTokenResponse.getSecret().getIdentifier())
        .refreshTokenRef(refreshTokenResponse.getSecret().getIdentifier())
        .build();
  }
}
