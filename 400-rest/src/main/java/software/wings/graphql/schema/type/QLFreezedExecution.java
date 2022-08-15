package software.wings.graphql.schema.type;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import lombok.Builder;
import lombok.Value;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import static io.harness.annotations.dev.HarnessTeam.CDC;

@OwnedBy(CDC)
@Value
@Builder
@Scope(PermissionAttribute.ResourceType.APPLICATION)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLFreezedExecution implements QLCause {
	private String id;
	private String deploymentType;
	private String executionUrl;
	private Long createdAt;
}
