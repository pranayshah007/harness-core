package software.wings.graphql.schema.type.secrets;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "QLKerberosAuthenticationKeys")
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLKerberosAuthentication implements QLSSHAuthenticationType {
  String principal;
  String realm;
  Integer port;
}
