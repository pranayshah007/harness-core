package io.harness.ng.oauth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OauthAccessTokenDTO {
  String accessToken;
  String refreshToken;
}
