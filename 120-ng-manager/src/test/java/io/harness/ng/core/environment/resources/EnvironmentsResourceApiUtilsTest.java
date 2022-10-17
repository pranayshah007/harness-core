/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.NGConstants.HARNESS_BLUE;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static java.util.Collections.singletonMap;
import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.model.Environment;
import io.harness.spec.server.ng.model.EnvironmentRequest;
import io.harness.spec.server.ng.model.EnvironmentResponse;
import io.harness.spec.server.ng.model.ServiceOverrideRequest;
import io.harness.spec.server.ng.model.ServiceOverrideResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EnvironmentsResourceApiUtilsTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private Validator validator;
  io.harness.ng.core.environment.beans.Environment environmentEntity;
  io.harness.ng.core.environment.beans.Environment environmentRequestEntity;
  List<NGTag> tags;
  String slug = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String service = randomAlphabetic(10);
  String yamlServiceOverride;
  String description = "sample description";
  NGEnvironmentConfig ngEnvironmentConfig;
  EnvironmentResponse environmentResponse;
  EnvironmentRequest environmentRequest;
  ServiceOverrideResponse serviceOverrideResponse;
  NGServiceOverrideConfig ngServiceOverrideConfig;
  NGServiceOverridesEntity ngServiceOverridesEntity;
  ServiceOverrideRequest serviceOverrideRequest;
  private EnvironmentsResourceApiUtils environmentsResourceApiUtils;
  Environment environment;

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    environmentsResourceApiUtils = new EnvironmentsResourceApiUtils(validator);

    tags = Arrays.asList(NGTag.builder().key("k1").value("v1").build());
    ngEnvironmentConfig = NGEnvironmentConfig.builder()
                              .ngEnvironmentInfoConfig(NGEnvironmentInfoConfig.builder()
                                                           .name(name)
                                                           .identifier(slug)
                                                           .orgIdentifier(org)
                                                           .projectIdentifier(project)
                                                           .tags(singletonMap("k1", "v1"))
                                                           .type(EnvironmentType.PreProduction)
                                                           .build())
                              .build();

    ngServiceOverrideConfig =
        NGServiceOverrideConfig.builder()
            .serviceOverrideInfoConfig(
                NGServiceOverrideInfoConfig.builder().serviceRef(service).environmentRef(slug).build())
            .build();

    yamlServiceOverride = ServiceOverridesMapper.toYaml(ngServiceOverrideConfig);
    environmentRequest = new EnvironmentRequest();
    environmentRequest.setColor(HARNESS_BLUE);
    environmentRequest.setDescription(description);
    environmentRequest.setName(name);
    environmentRequest.setTags(singletonMap("k1", "v1"));
    environmentRequest.setSlug(slug);
    environmentRequest.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
    environmentRequest.setType(EnvironmentRequest.TypeEnum.PREPRODUCTION);

    serviceOverrideRequest = new ServiceOverrideRequest();
    serviceOverrideRequest.setYaml(yamlServiceOverride);

    serviceOverrideResponse = new ServiceOverrideResponse();
    serviceOverrideResponse.setYaml(yamlServiceOverride);
    serviceOverrideResponse.setProject(project);
    serviceOverrideResponse.setService(service);
    serviceOverrideResponse.setEnvironemnt(slug);
    serviceOverrideResponse.setAccount(account);
    serviceOverrideResponse.setOrg(org);

    environmentEntity = io.harness.ng.core.environment.beans.Environment.builder()
                            .accountId(account)
                            .identifier(slug)
                            .orgIdentifier(org)
                            .projectIdentifier(project)
                            .color(HARNESS_BLUE)
                            .name(name)
                            .description(description)
                            .type(EnvironmentType.PreProduction)
                            .tags(tags)
                            .version(1L)
                            .createdAt(987654321L)
                            .lastModifiedAt(123456789L)
                            .yaml(EnvironmentMapper.toYaml(ngEnvironmentConfig))
                            .build();

    environmentRequestEntity = io.harness.ng.core.environment.beans.Environment.builder()
                                   .accountId(account)
                                   .identifier(slug)
                                   .orgIdentifier(org)
                                   .projectIdentifier(project)
                                   .color(HARNESS_BLUE)
                                   .name(name)
                                   .description(description)
                                   .type(EnvironmentType.PreProduction)
                                   .tags(tags)
                                   .yaml(EnvironmentMapper.toYaml(ngEnvironmentConfig))
                                   .build();

    ngServiceOverridesEntity = NGServiceOverridesEntity.builder()
                                   .accountId(account)
                                   .projectIdentifier(project)
                                   .orgIdentifier(org)
                                   .environmentRef(slug)
                                   .serviceRef(service)
                                   .yaml(yamlServiceOverride)
                                   .build();

    environment = new Environment();
    environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
    environment.setProject(project);
    environment.setType(io.harness.spec.server.ng.model.Environment.TypeEnum.PREPRODUCTION);
    environment.setName(name);
    environment.setTags(singletonMap("k1", "v1"));
    environment.setDescription(description);
    environment.setSlug(slug);
    environment.setAccount(account);
    environment.setColor(HARNESS_BLUE);
    environment.setOrg(org);

    environmentResponse = new EnvironmentResponse();
    environmentResponse.setEnvironment(environment);
    environmentResponse.setUpdated(123456789L);
    environmentResponse.setCreated(987654321L);
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testPermissionCheckDTO() {
    PermissionCheckDTO permissionCheckDTO =
        environmentsResourceApiUtils.environmentResponseToPermissionCheckDTO(environmentResponse);
    Set<ConstraintViolation<Object>> violations = validator.validate(permissionCheckDTO);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    assertEquals(CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION, permissionCheckDTO.getPermission());
    assertEquals(slug, permissionCheckDTO.getResourceIdentifier());
    assertEquals(org, permissionCheckDTO.getResourceScope().getOrgIdentifier());
    assertEquals(account, permissionCheckDTO.getResourceScope().getAccountIdentifier());
    assertEquals(project, permissionCheckDTO.getResourceScope().getProjectIdentifier());
    assertEquals(NGResourceType.ENVIRONMENT, permissionCheckDTO.getResourceType());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testEnvironmentResponseWrapper() {
    EnvironmentResponse environmentResponseFinal =
        environmentsResourceApiUtils.toEnvironmentResponseWrapper(environmentEntity);
    Set<ConstraintViolation<Object>> violations = validator.validate(environmentResponse);

    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();
    assertEquals(environmentResponseFinal, environmentResponse);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testEnvironmentEntity() {
    io.harness.ng.core.environment.beans.Environment environmentFinal =
        environmentsResourceApiUtils.toEnvironmentEntity(account, environmentRequest, org, project);
    Set<ConstraintViolation<Object>> violations = validator.validate(environmentResponse);

    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();
    assertEquals(environmentFinal, environmentRequestEntity);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testServiceOverrideEntity() {
    NGServiceOverridesEntity ngServiceOverridesEntityFinal = environmentsResourceApiUtils.toServiceOverridesEntity(
        account, serviceOverrideRequest, org, project, slug, service);
    Set<ConstraintViolation<Object>> violations = validator.validate(environmentResponse);

    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();
    assertEquals(ngServiceOverridesEntityFinal, ngServiceOverridesEntity);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testServiceOverrideResponse() {
    ServiceOverrideResponse serviceOverrideResponseFinal =
        environmentsResourceApiUtils.toServiceOverrideResponse(ngServiceOverridesEntity);
    Set<ConstraintViolation<Object>> violations = validator.validate(environmentResponse);

    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();
    assertEquals(serviceOverrideResponseFinal, serviceOverrideResponse);
  }
}
