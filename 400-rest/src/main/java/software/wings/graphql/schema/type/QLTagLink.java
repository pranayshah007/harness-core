package software.wings.graphql.schema.type;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.aggregation.QLEntityType;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@OwnedBy(CDC)
@Value
@Builder
@Scope(PermissionAttribute.ResourceType.SETTING)
@FieldNameConstants(innerTypeName = "QLTagLinkKeys")
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLTagLink implements QLObject {
  private String name;
  private String value;
  private QLEntityType entityType;
  private String entityId;
}
