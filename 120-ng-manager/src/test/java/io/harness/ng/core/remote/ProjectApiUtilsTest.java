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
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.entities.Project;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.ng.v1.model.CreateProjectRequest;
import io.harness.spec.server.ng.v1.model.ProjectResponse;
import io.harness.spec.server.ng.v1.model.UpdateProjectRequest;

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
public class ProjectApiUtilsTest extends CategoryTest {
  public static final String SORT_IDENTIFIER_FIELD = "identifier";
  public static final String SORT_NAME_FIELD = "name";
  public static final String ASCENDING_ORDER = "ASC";
  public static final String DESCENDING_ORDER = "DESC";
  public static final String SORT_CREATED_FIELD = "created";
  public static final String SORT_UPDATED_FIELD = "updated";
  public static final String SORT_CREATED_AT_FIELD = "createdAt";
  public static final String SORT_LAST_MODIFIED_AT_FIELD = "lastModifiedAt";
  private ObjectMapper objectMapper;
  private Validator validator;

  private String testFilesBasePath = "120-ng-manager/src/test/resources/server/stub/project/";

  private ProjectApiUtils projectApiUtils;
  private String identifier = "project_identifier";
  private String name = "name";
  private String org = "org";
  String HARNESS_BLUE = "#0063F7";
  private String description = "description";

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();

    projectApiUtils = new ProjectApiUtils(validator);
  }

  private void testProjectDtoMappingFromCreateRequest(String from, String to) throws JsonProcessingException {
    String toJson = readFileAsString(testFilesBasePath + to);

    CreateProjectRequest projectRequest =
        objectMapper.readValue(readFileAsString(testFilesBasePath + from), CreateProjectRequest.class);

    ProjectDTO projectDTO = projectApiUtils.getProjectDto(projectRequest);
    Set<ConstraintViolation<Object>> violations = validator.validate(projectDTO);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    String projectDtoJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(projectDTO);
    JSONAssert.assertEquals(toJson, projectDtoJson);
  }

  private void testProjectDtoMappingFromUpdateRequest(String from, String to) throws JsonProcessingException {
    String fromJson = readFileAsString(testFilesBasePath + from);
    String toJson = readFileAsString(testFilesBasePath + to);

    UpdateProjectRequest projectRequest = objectMapper.readValue(fromJson, UpdateProjectRequest.class);

    ProjectDTO projectDTO = projectApiUtils.getProjectDto(projectRequest);
    Set<ConstraintViolation<Object>> violations = validator.validate(projectDTO);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    String projectDtoJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(projectDTO);
    JSONAssert.assertEquals(toJson, projectDtoJson);
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testProjectDtoMapping() throws JsonProcessingException {
    testProjectDtoMappingFromCreateRequest("create-project-request-1.json", "project-2.json");
    testProjectDtoMappingFromUpdateRequest("update-project-request-1.json", "project-2.json");
  }

  @Test(expected = JerseyViolationException.class)
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testProjectDtoMappingValidationExceptionForMissingIdentifier() throws JsonProcessingException {
    CreateProjectRequest projectRequest = objectMapper.readValue(
        readFileAsString(testFilesBasePath + "create-project-request-1.json"), CreateProjectRequest.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(projectRequest);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    projectRequest.getProject().setIdentifier(null);
    projectApiUtils.getProjectDto(projectRequest);
  }

  @Test(expected = JerseyViolationException.class)
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testProjectDtoMappingValidationExceptionForMissingName() throws JsonProcessingException {
    CreateProjectRequest projectRequest = objectMapper.readValue(
        readFileAsString(testFilesBasePath + "create-project-request-1.json"), CreateProjectRequest.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(projectRequest);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    projectRequest.getProject().setName(null);
    projectApiUtils.getProjectDto(projectRequest);
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testProjectDtoMappingValidationForMissingTags() throws JsonProcessingException {
    CreateProjectRequest projectRequest = objectMapper.readValue(
        readFileAsString(testFilesBasePath + "create-project-request-1.json"), CreateProjectRequest.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(projectRequest);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    projectRequest.getProject().setTags(null);
    assertThat(projectApiUtils.getProjectDto(projectRequest).getTags()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testProjectDtoMappingValidationForMissingColor() throws JsonProcessingException {
    CreateProjectRequest projectRequest = objectMapper.readValue(
        readFileAsString(testFilesBasePath + "create-project-request-1.json"), CreateProjectRequest.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(projectRequest);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    projectRequest.getProject().setColor(null);
    assertThat(projectApiUtils.getProjectDto(projectRequest).getColor()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testResponseMapping() {
    Project project = Project.builder()
                          .identifier(identifier)
                          .name(name)
                          .orgIdentifier(org)
                          .color(HARNESS_BLUE)
                          .description(description)
                          .modules(Collections.emptyList())
                          .tags(Collections.emptyList())
                          .createdAt(1234567890L)
                          .lastModifiedAt(1234567890L)
                          .build();

    ProjectResponse projectResponse = projectApiUtils.getProjectResponse(project);

    assertThat(projectResponse.getProject()).isNotNull();
    assertThat(projectResponse.getProject().getIdentifier()).isEqualTo(identifier);
    assertThat(projectResponse.getProject().getName()).isEqualTo(name);
    assertThat(projectResponse.getProject().getOrg()).isEqualTo(org);
    assertThat(projectResponse.getProject().getColor()).isEqualTo(HARNESS_BLUE);
    assertThat(projectResponse.getProject().getDescription()).isEqualTo(description);
    assertThat(projectResponse.getProject().getModules()).isNotNull();
    assertThat(projectResponse.getProject().getModules()).isEmpty();
    assertThat(projectResponse.getProject().getTags()).isNotNull();
    assertThat(projectResponse.getProject().getTags()).isEmpty();
    assertThat(projectResponse.getCreated()).isEqualTo(1234567890L);
    assertThat(projectResponse.getUpdated()).isEqualTo(1234567890L);
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPageRequestSortAndOrder() {
    Pageable pageRequest = projectApiUtils.getPageRequest(0, 10, "identifier", "desc");
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getPageNumber()).isEqualTo(0);
    assertThat(pageRequest.getPageSize()).isEqualTo(10);
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_IDENTIFIER_FIELD).descending());

    pageRequest = projectApiUtils.getPageRequest(0, 10, SORT_IDENTIFIER_FIELD, ASCENDING_ORDER);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_IDENTIFIER_FIELD).ascending());

    pageRequest = projectApiUtils.getPageRequest(0, 10, SORT_NAME_FIELD, DESCENDING_ORDER);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_NAME_FIELD).descending());

    pageRequest = projectApiUtils.getPageRequest(0, 10, SORT_CREATED_FIELD, DESCENDING_ORDER);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_CREATED_AT_FIELD).descending());

    pageRequest = projectApiUtils.getPageRequest(0, 10, SORT_UPDATED_FIELD, DESCENDING_ORDER);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_LAST_MODIFIED_AT_FIELD).descending());

    pageRequest = projectApiUtils.getPageRequest(0, 10, null, DESCENDING_ORDER);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_LAST_MODIFIED_AT_FIELD).descending());

    pageRequest = projectApiUtils.getPageRequest(0, 10, null, null);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_LAST_MODIFIED_AT_FIELD).descending());

    pageRequest = projectApiUtils.getPageRequest(0, 10, SORT_IDENTIFIER_FIELD, null);
    assertThat(pageRequest.getSort()).isNotNull();
    assertThat(pageRequest.getSort()).isEqualTo(Sort.by(SORT_IDENTIFIER_FIELD).descending());

    pageRequest = projectApiUtils.getPageRequest(0, 10, SORT_IDENTIFIER_FIELD, "asc");
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