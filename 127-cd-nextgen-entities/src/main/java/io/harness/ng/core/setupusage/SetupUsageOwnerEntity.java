package io.harness.ng.core.setupusage;

import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

/**
 *SetupUsageOwnerEntity represents the 'ReferredBy' entity in a setup usage relation
 */
@Value
@Builder
public class SetupUsageOwnerEntity {
  @NotEmpty String accountId;
  String orgIdentifier;
  String projectIdentifier;
  @NotEmpty String identifier;
  @NotEmpty String name;
  @NotNull EntityTypeProtoEnum type;
}
