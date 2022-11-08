/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.ng.v1.model.CreateOrganizationRequest;
import io.harness.spec.server.ng.v1.model.OrganizationResponse;
import io.harness.spec.server.ng.v1.model.UpdateOrganizationRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jersey.validation.JerseyViolationException;
import io.serializer.HObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import net.sf.json.test.JSONAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.DX)
public class OrganizationApiUtilsTest extends CategoryTest {
  public static final String SORT_SLUG_FIELD = "slug";
  public static final String SORT_NAME_FIELD = "name";
  public static final String SORT_IDENTIFIER_FIELD = "identifier";
  public static final String ASCENDING_ORDER = "ASC";
  public static final String DESCENDING_ORDER = "DESC";
  public static final String SORT_CREATED_FIELD = "created";
  public static final String SORT_UPDATED_FIELD = "updated";
  public static final String SORT_CREATED_AT_FIELD = "createdAt";
  public static final String SORT_LAST_MODIFIED_AT_FIELD = "lastModifiedAt";
  private ObjectMapper objectMapper;
  private Validator validator;

  private String testFilesBasePath = "120-ng-manager/src/test/resources/server/stub/org/";

  private OrganizationApiUtils organizationApiUtils;
  private String slug = "org_slug";
  private String name = "name";
  private String description = "description";

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();

    organizationApiUtils = new OrganizationApiUtils(validator);
  }

  private void testOrgDtoMappingFromCreateRequest(String from, String to) throws JsonProcessingException {
    String toJson = readFileAsString(testFilesBasePath + to);

    CreateOrganizationRequest createOrganizationRequest =
        objectMapper.readValue(readFileAsString(testFilesBasePath + from), CreateOrganizationRequest.class);

    OrganizationDTO organizationDto = organizationApiUtils.getOrganizationDto(createOrganizationRequest);
    Set<ConstraintViolation<OrganizationDTO>> violations = validator.validate(organizationDto);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    String orgDtoJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(organizationDto);
    JSONAssert.assertEquals(toJson, orgDtoJson);
  }

  private void testOrgDtoMappingFromUpdateRequest(String from, String to) throws JsonProcessingException {
    String fromJson = readFileAsString(testFilesBasePath + from);
    String toJson = readFileAsString(testFilesBasePath + to);

    UpdateOrganizationRequest updateOrganizationRequest =
        objectMapper.readValue(fromJson, UpdateOrganizationRequest.class);

    OrganizationDTO organizationDto = organizationApiUtils.getOrganizationDto(updateOrganizationRequest);
    Set<ConstraintViolation<Object>> violations = validator.validate(organizationDto);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    String orgDtoJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(organizationDto);
    JSONAssert.assertEquals(toJson, orgDtoJson);
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testOrgDtoMapping() throws JsonProcessingException {
    testOrgDtoMappingFromCreateRequest("create-org-request.json", "org-dto.json");
    testOrgDtoMappingFromUpdateRequest("update-org-request.json", "org-dto.json");
  }

  @Test(expected = JerseyViolationException.class)
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testOrgDtoMappingValidationExceptionForMissingSlug() throws JsonProcessingException {
    CreateOrganizationRequest organizationRequest = objectMapper.readValue(
        readFileAsString(testFilesBasePath + "create-org-request.json"), CreateOrganizationRequest.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(organizationRequest);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    organizationRequest.getOrg().setSlug(null);

    organizationApiUtils.getOrganizationDto(organizationRequest);
  }

  @Test(expected = JerseyViolationException.class)
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testOrgDtoMappingValidationExceptionForMissingName() throws JsonProcessingException {
    CreateOrganizationRequest organizationRequest = objectMapper.readValue(
        readFileAsString(testFilesBasePath + "create-org-request.json"), CreateOrganizationRequest.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(organizationRequest);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    organizationRequest.getOrg().setName(null);
    organizationApiUtils.getOrganizationDto(organizationRequest);
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testOrgDtoMappingValidationForMissingTags() throws JsonProcessingException {
    CreateOrganizationRequest organizationRequest = objectMapper.readValue(
        readFileAsString(testFilesBasePath + "create-org-request.json"), CreateOrganizationRequest.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(organizationRequest);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    organizationRequest.getOrg().setTags(null);
    assertThat(organizationApiUtils.getOrganizationDto(organizationRequest).getTags()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testResponseMapping() {
    Organization organization = Organization.builder()
                                    .identifier(slug)
                                    .name(name)
                                    .description(description)
                                    .createdAt(1234567890L)
                                    .lastModifiedAt(1234567890L)
                                    .tags(Collections.emptyList())
                                    .harnessManaged(true)
                                    .build();

    OrganizationResponse organizationResponse = organizationApiUtils.getOrganizationResponse(organization);

    assertThat(organizationResponse.getOrg()).isNotNull();
    assertThat(organizationResponse.getOrg().getSlug()).isEqualTo(slug);
    assertThat(organizationResponse.getOrg().getName()).isEqualTo(name);
    assertThat(organizationResponse.getOrg().getDescription()).isEqualTo(description);
    assertThat(organizationResponse.getOrg().getTags()).isNotNull();
    assertThat(organizationResponse.getOrg().getTags()).isEmpty();
    assertThat(organizationResponse.isHarnessManaged()).isEqualTo(true);
    assertThat(organizationResponse.getCreated()).isEqualTo(1234567890L);
    assertThat(organizationResponse.getUpdated()).isEqualTo(1234567890L);
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPageRequestSortAndOrder() {
    Pageable pageRequest = organizationApiUtils.getPageRequest(0, 10, "slug", "desc");
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getPageNumber()).isEqualTo(0);
    assertThat(pageRequest.getPageSize()).isEqualTo(10);
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_IDENTIFIER_FIELD).descending());

    pageRequest = organizationApiUtils.getPageRequest(0, 10, SORT_SLUG_FIELD, ASCENDING_ORDER);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_IDENTIFIER_FIELD).ascending());

    pageRequest = organizationApiUtils.getPageRequest(0, 10, SORT_NAME_FIELD, DESCENDING_ORDER);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_NAME_FIELD).descending());

    pageRequest = organizationApiUtils.getPageRequest(0, 10, SORT_CREATED_FIELD, DESCENDING_ORDER);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_CREATED_AT_FIELD).descending());

    pageRequest = organizationApiUtils.getPageRequest(0, 10, SORT_UPDATED_FIELD, DESCENDING_ORDER);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_LAST_MODIFIED_AT_FIELD).descending());

    pageRequest = organizationApiUtils.getPageRequest(0, 10, null, DESCENDING_ORDER);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_LAST_MODIFIED_AT_FIELD).descending());

    pageRequest = organizationApiUtils.getPageRequest(0, 10, null, null);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_LAST_MODIFIED_AT_FIELD).descending());

    pageRequest = organizationApiUtils.getPageRequest(0, 10, SORT_SLUG_FIELD, null);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_IDENTIFIER_FIELD).descending());

    pageRequest = organizationApiUtils.getPageRequest(0, 10, SORT_SLUG_FIELD, "asc");
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_IDENTIFIER_FIELD).descending());
  }

  public static String readFileAsString(String file) {
    try {
      return new String(Files.readAllBytes(Paths.get(file)));
    } catch (Exception ex) {
      Assert.fail("Failed reading the json from " + file + " with error " + ex.getMessage());
      return "";
    }
  }
}