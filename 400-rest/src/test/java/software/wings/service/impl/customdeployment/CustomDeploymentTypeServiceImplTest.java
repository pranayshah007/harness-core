/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.customdeployment;

import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.category.element.UnitTests;
import io.harness.exception.CustomDeploymentTypeNotFoundException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.Variable;
import software.wings.beans.customdeployment.CustomDeploymentTypeDTO;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeAware;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeService;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CustomDeploymentTypeServiceImplTest extends WingsBaseTest {
  @Mock private TemplateService templateService;
  @Inject @InjectMocks private CustomDeploymentTypeService customDeploymentTypeService;

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testPutCustomDeploymentTypeNameIfApplicable() {
    List<TestClass> entities = asList(new TestClass("id-1", null), new TestClass("id-2", null),
        new TestClass("id-1", "old-name"), new TestClass("deleted-template-id", null));
    doReturn(asList(buildTemplate("ssh", "id-1", null), buildTemplate("k8s", "id-2", null)))
        .when(templateService)
        .batchGet(anyList(), eq(ACCOUNT_ID));

    customDeploymentTypeService.putCustomDeploymentTypeNameIfApplicable(entities, ACCOUNT_ID);

    assertThat(entities).containsExactlyInAnyOrder(new TestClass("id-1", "ssh"), new TestClass("id-2", "k8s"),
        new TestClass("id-1", "ssh"), new TestClass("deleted-template-id", null));

    customDeploymentTypeService.putCustomDeploymentTypeNameIfApplicable(Collections.emptyList(), ACCOUNT_ID);
    customDeploymentTypeService.putCustomDeploymentTypeNameIfApplicable(null, ACCOUNT_ID);
  }

  private Template buildTemplate(String name, String id, List<Variable> variables) {
    return Template.builder().name(name).uuid(id).variables(variables).build();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void list() {
    doReturn(asList(buildTemplate("ssh", "id-1", asList(aVariable().name("url").value("abc.com").build())),
                 buildTemplate("k8s", "id-2", asList(aVariable().name("namespace").value("abc").build()))))
        .when(templateService)
        .getTemplatesByType(ACCOUNT_ID, null, TemplateType.CUSTOM_DEPLOYMENT_TYPE);

    List<CustomDeploymentTypeDTO> deploymentTypes;
    deploymentTypes = customDeploymentTypeService.list(ACCOUNT_ID, null, false);

    assertThat(deploymentTypes.stream().map(CustomDeploymentTypeDTO::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("ssh", "k8s");
    assertThat(deploymentTypes.stream()
                   .map(CustomDeploymentTypeDTO::getInfraVariables)
                   .filter(Objects::nonNull)
                   .collect(Collectors.toList()))
        .isEmpty();

    deploymentTypes = customDeploymentTypeService.list(ACCOUNT_ID, null, true);
    assertThat(deploymentTypes.stream()
                   .map(CustomDeploymentTypeDTO::getInfraVariables)
                   .filter(Objects::nonNull)
                   .collect(Collectors.toList()))
        .hasSize(2);

    doReturn(null).when(templateService).getTemplatesByType(ACCOUNT_ID, null, TemplateType.CUSTOM_DEPLOYMENT_TYPE);
    deploymentTypes = customDeploymentTypeService.list(ACCOUNT_ID, null, true);
    assertThat(deploymentTypes).isEmpty();

    doReturn(new ArrayList<>())
        .when(templateService)
        .getTemplatesByType(ACCOUNT_ID, null, TemplateType.CUSTOM_DEPLOYMENT_TYPE);
    deploymentTypes = customDeploymentTypeService.list(ACCOUNT_ID, null, true);
    assertThat(deploymentTypes).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void get() {
    doReturn(buildTemplate("k8s", TEMPLATE_ID, asList(aVariable().name("namespace").value("abc").build())))
        .when(templateService)
        .get(TEMPLATE_ID, "1");

    CustomDeploymentTypeDTO customDeploymentTypeDTO;
    customDeploymentTypeDTO = customDeploymentTypeService.get(ACCOUNT_ID, TEMPLATE_ID, "1");

    assertThat(customDeploymentTypeDTO.getName()).isEqualTo("k8s");
    assertThat(customDeploymentTypeDTO.getInfraVariables()).hasSize(1);

    doReturn(null).when(templateService).get(any(), any());
    assertThat(customDeploymentTypeService.get(ACCOUNT_ID, TEMPLATE_ID, "1")).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getIfTemplateDeleted() {
    doThrow(new RuntimeException("Template Deleted")).when(templateService).get(anyString(), anyString());

    CustomDeploymentTypeDTO customDeploymentTypeDTO;
    assertThatExceptionOfType(CustomDeploymentTypeNotFoundException.class)
        .isThrownBy(() -> customDeploymentTypeService.get(ACCOUNT_ID, TEMPLATE_ID, "1"))
        .withMessageContaining("Deployment Type With Given Id Does Not Exist");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void putCustomDeploymentTypeNameIfApplicable() {
    doReturn(buildTemplate("k8s", TEMPLATE_ID, asList(aVariable().name("namespace").value("abc").build())))
        .when(templateService)
        .get(TEMPLATE_ID);

    final TestClass entity = new TestClass(TEMPLATE_ID, null);
    customDeploymentTypeService.putCustomDeploymentTypeNameIfApplicable(entity);

    assertThat(entity.getName()).isEqualTo("k8s");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void putCustomDeploymentTypeNameIfTemplateDeleted() {
    doThrow(new RuntimeException()).when(templateService).get(anyString());

    final TestClass entity = new TestClass(TEMPLATE_ID, null);
    customDeploymentTypeService.putCustomDeploymentTypeNameIfApplicable(entity);

    assertThat(entity.getName()).isNull();
  }

  @Data
  @AllArgsConstructor
  public static final class TestClass implements CustomDeploymentTypeAware {
    private String id;
    private String name;

    @Override
    public String getDeploymentTypeTemplateId() {
      return id;
    }

    @Override
    public void setDeploymentTypeName(String theCustomDeploymentName) {
      name = theCustomDeploymentName;
    }
  }
}
