package io.harness.ng.oauth;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OauthAccessTokenResponseDTO {
  String accessTokenRef;
  String refreshTokenRef;
}
