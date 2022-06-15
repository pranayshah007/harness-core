package io.harness.accesscontrol.acl.conditions;

import io.harness.accesscontrol.acl.PermissionCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import java.util.Map;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class ACLExpressionEvaluatorProvider {
  public ACLExpressionEvaluator get(PermissionCheck permissionCheck, Map<String, String> attributes) {
    return new ACLExpressionEvaluator(permissionCheck, attributes);
  }
}
