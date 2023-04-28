package io.harness.authenticationservice.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.OauthProviderType;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PL)
@Data
@Builder
public class AuthenticationInfoV2 {
  private AuthenticationMechanism authenticationMechanism;
  private boolean oauthEnabled;
  private List<OauthProviderType> oauthProviders;
  private List<SSORequest> ssoRequests;
  private String accountId;
}

