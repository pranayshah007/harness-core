package software.wings.graphql.schema.query;

import graphql.schema.DataFetchingFieldSelectionSet;
import lombok.AccessLevel;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import software.wings.beans.Environment.EnvironmentType;

@Value
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QLInstanceConnectionQueryParameters implements QLPageQueryParameters {
  int limit;
  int offset;
  String environmentId;
  String serviceId;
  EnvironmentType envType;
  String accountId;
  DataFetchingFieldSelectionSet selectionSet;
  String applicationId;
}
