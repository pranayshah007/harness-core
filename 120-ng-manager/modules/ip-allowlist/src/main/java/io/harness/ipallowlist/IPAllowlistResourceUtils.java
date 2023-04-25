/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist;

import static io.harness.beans.SortOrder.Builder.aSortOrder;
import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.ipallowlist.dto.IPAllowlistFilterDTO;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.ipallowlist.entity.IPAllowlistEntity.IPAllowlistEntityBuilder;
import io.harness.ng.beans.PageRequest;
import io.harness.spec.server.ng.v1.model.AllowedSourceType;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfig;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfigResponse;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.data.domain.Pageable;

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
    ipAllowlistConfig.identifier(entity.getIdentifier());
    ipAllowlistConfig.name(entity.getName());
    ipAllowlistConfig.description(entity.getDescription());
    ipAllowlistConfig.allowedSourceType(entity.getAllowedSourceType());
    ipAllowlistConfig.ipAddress(entity.getIpAddress());
    ipAllowlistConfig.enabled(entity.getEnabled());
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
    IPAllowlistConfig ipAllowlistConfig = toIPAllowlistConfig(entity);
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

  public Pageable getPageRequest(Integer page, Integer limit, String sort, String order) {
    List<SortOrder> sortOrders;
    String fieldName = getFieldName(sort);
    if (fieldName != null) {
      SortOrder.OrderType orderType = EnumUtils.getEnum(SortOrder.OrderType.class, order, DESC);
      sortOrders = List.of(aSortOrder().withField(fieldName, orderType).build());
    } else {
      sortOrders = List.of(aSortOrder().withField(IPAllowlistEntity.IPAllowlistConfigKeys.updated, DESC).build());
    }
    return PageUtils.getPageRequest(new PageRequest(page, limit, sortOrders));
  }

  private String getFieldName(String sort) {
    String fieldName;
    PageUtils.SortFields sortField = PageUtils.SortFields.fromValue(sort);
    if (sortField == null) {
      sortField = PageUtils.SortFields.UNSUPPORTED;
    }
    switch (sortField) {
      case IDENTIFIER:
        fieldName = IPAllowlistEntity.IPAllowlistConfigKeys.identifier;
        break;
      case NAME:
        fieldName = IPAllowlistEntity.IPAllowlistConfigKeys.name;
        break;
      case CREATED:
        fieldName = IPAllowlistEntity.IPAllowlistConfigKeys.created;
        break;
      case UPDATED:
        fieldName = IPAllowlistEntity.IPAllowlistConfigKeys.updated;
        break;
      case UNSUPPORTED:
      default:
        fieldName = null;
    }
    return fieldName;
  }

  public IPAllowlistFilterDTO getFilterProperties(String searchTerm, String allowedSourceType) {
    return IPAllowlistFilterDTO.builder()
        .searchTerm(searchTerm)
        .allowedSourceType(getAllowedSourceTypeEnum(allowedSourceType))
        .build();
  }

  public AllowedSourceType getAllowedSourceTypeEnum(String allowedSourceType) {
    if (isEmpty(allowedSourceType)) {
      return null;
    }
    try {
      return AllowedSourceType.valueOf(allowedSourceType);
    } catch (IllegalArgumentException exception) {
      throw new UnknownEnumTypeException("IP Allowlist allowed source type", allowedSourceType);
    }
  }
}
