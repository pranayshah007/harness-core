package io.harness.ng.core.environment.resources;

import io.harness.cdng.environment.filters.Entity;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnvSwaggerObjectWrapper {
  NGServiceOverrideConfig serviceOverrideConfig;
  Entity envFilterEntityType;
}
