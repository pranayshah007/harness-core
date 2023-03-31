package software.wings.security.authentication;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.DefaultExperience;

import software.wings.security.saml.SSORequest;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
@Data
@Builder
public class LoginTypeResponseV2 {
  private AuthenticationMechanism authenticationMechanism;
  private List<SSORequest> ssoRequests;
  private boolean isOauthEnabled;
  private boolean showCaptcha;
  private DefaultExperience defaultExperience;
}
