package software.wings.graphql.schema.type.artifactSource;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

@Value
@Builder
@FieldNameConstants(innerTypeName = "QLBambooArtifactSourceKeys")
@Scope(PermissionAttribute.ResourceType.SERVICE)
public class QLBambooArtifactSource implements QLArtifactSource {
  String name;
  String id;
  Long createdAt;
}
