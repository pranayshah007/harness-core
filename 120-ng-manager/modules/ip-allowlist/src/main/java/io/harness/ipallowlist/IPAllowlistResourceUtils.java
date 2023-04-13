/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.ng.core.common.beans.NGTag;
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
    return IPAllowlistEntity.builder()
        .identifier(config.getIdentifier())
        .name(config.getName())
        .description(config.getDescription())
        .accountIdentifier(accountIdentifier)
        .allowedSourceType(config.getAllowedSourceType())
        .enabled(config.isEnabled())
        .ipAddress(config.getIpAddress())
        .tag((NGTag) config.getTags())
        .build();
  }

  public IPAllowlistConfig toIPAllowlistConfig(IPAllowlistEntity entity) {
    IPAllowlistConfig ipAllowlistConfig = new IPAllowlistConfig();
    ipAllowlistConfig.setIdentifier(entity.getIdentifier());
    ipAllowlistConfig.setName(entity.getName());
    ipAllowlistConfig.setDescription(entity.getDescription());
    ipAllowlistConfig.setAllowedSourceType(entity.getAllowedSourceType());
    ipAllowlistConfig.setTags(entity.getTags());
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
        .tag((NGTag) config.getTags())
        .build();
  }

  public IPAllowlistConfigResponse toIPAllowlistConfigResponse(IPAllowlistEntity entity) {
    IPAllowlistConfig ipAllowlistConfig = new IPAllowlistConfig();
    ipAllowlistConfig.setIdentifier(entity.getIdentifier());
    ipAllowlistConfig.setName(entity.getName());
    ipAllowlistConfig.setDescription(entity.getDescription());
    ipAllowlistConfig.setAllowedSourceType(entity.getAllowedSourceType());
    ipAllowlistConfig.setTags(entity.getTags());
    ipAllowlistConfig.setIpAddress(entity.getIpAddress());
    IPAllowlistConfigResponse ipAllowlistConfigResponse = new IPAllowlistConfigResponse();
    ipAllowlistConfigResponse.setIpAllowlistConfig(ipAllowlistConfig);
    ipAllowlistConfigResponse.setCreated(entity.getCreated());
    ipAllowlistConfigResponse.setUpdated(entity.getUpdated());
    Set<ConstraintViolation<IPAllowlistConfigResponse>> violations = validator.validate(ipAllowlistConfigResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return ipAllowlistConfigResponse;
  }
}
