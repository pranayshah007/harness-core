/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.ipallowlist.entity.IPAllowlistEntity.IPAllowlistEntityBuilder;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfig;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfigResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

@Singleton
@OwnedBy(HarnessTeam.PL)
public class IPAllowlistResourceUtils {
  private final Validator validator;

  @Inject
  public IPAllowlistResourceUtils(Validator validator) {
    this.validator = validator;
  }

  public IPAllowlistEntity toIPAllowlistEntity(IPAllowlistConfig config, String accountIdentifier) {
    IPAllowlistEntityBuilder ipAllowlistEntityBuilder = IPAllowlistEntity.builder()
                                                            .identifier(config.getIdentifier())
                                                            .name(config.getName())
                                                            .description(config.getDescription())
                                                            .accountIdentifier(accountIdentifier)
                                                            .enabled(config.isEnabled())
                                                            .ipAddress(config.getIpAddress());

    if (config.getAllowedSourceType() != null && isNotEmpty(config.getAllowedSourceType())) {
      ipAllowlistEntityBuilder.allowedSourceType(config.getAllowedSourceType());
    }

    return ipAllowlistEntityBuilder.build();
  }

  public IPAllowlistConfig toIPAllowlistConfig(IPAllowlistEntity entity) {
    IPAllowlistConfig ipAllowlistConfig = new IPAllowlistConfig();
    ipAllowlistConfig.setIdentifier(entity.getIdentifier());
    ipAllowlistConfig.setName(entity.getName());
    ipAllowlistConfig.setDescription(entity.getDescription());
    ipAllowlistConfig.setAllowedSourceType(entity.getAllowedSourceType());
    ipAllowlistConfig.setIpAddress(entity.getIpAddress());
    return ipAllowlistConfig;
  }
  public IPAllowlistEntity toIPAllowlistDTO(IPAllowlistConfig config, String accountIdentifier) {
    return IPAllowlistEntity.builder()
        .identifier(config.getIdentifier())
        .name(config.getName())
        .description(config.getDescription())
        .accountIdentifier(accountIdentifier)
        .allowedSourceType(config.getAllowedSourceType())
        .enabled(config.isEnabled())
        .ipAddress(config.getIpAddress())
        .build();
  }

  public IPAllowlistConfigResponse toIPAllowlistConfigResponse(IPAllowlistEntity entity) {
    IPAllowlistConfig ipAllowlistConfig = new IPAllowlistConfig();
    ipAllowlistConfig.identifier(entity.getIdentifier());
    ipAllowlistConfig.name(entity.getName());
    ipAllowlistConfig.description(entity.getDescription());
    ipAllowlistConfig.allowedSourceType(entity.getAllowedSourceType());
    ipAllowlistConfig.ipAddress(entity.getIpAddress());
    ipAllowlistConfig.enabled(entity.getEnabled());
    IPAllowlistConfigResponse ipAllowlistConfigResponse = new IPAllowlistConfigResponse();
    ipAllowlistConfigResponse.ipAllowlistConfig(ipAllowlistConfig);
    ipAllowlistConfigResponse.created(entity.getCreated());
    ipAllowlistConfigResponse.updated(entity.getUpdated());
    Set<ConstraintViolation<IPAllowlistConfigResponse>> violations = validator.validate(ipAllowlistConfigResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return ipAllowlistConfigResponse;
  }
}
