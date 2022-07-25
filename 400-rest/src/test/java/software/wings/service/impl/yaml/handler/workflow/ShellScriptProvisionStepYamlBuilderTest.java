/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.rule.OwnerRule.SHUBHAM_MAHESHWARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureProvisionerService;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ShellScriptProvisionStepYamlBuilderTest extends WingsBaseTest {
  @Mock private AppService appService;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;
  @InjectMocks private ShellScriptProvisionStepYamlBuilder yamlBuilder = new ShellScriptProvisionStepYamlBuilder();

  private static final String PROVISIONER_ID = "provisionerId";
  private static final String PROVISIONER_NAME = "provisionerName";
  private static String APP_ID = "app-id";
  private static String ACCOUNT_ID = "accountId";

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testConvertIdToNameForKnownTypes() {
    Object objectValue = "id_of_provisioner";
    Map<String, Object> outputProperties = new HashMap<>();

    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
    when(infrastructureProvisionerService.get(APP_ID, (String) objectValue))
        .thenReturn(ShellScriptInfrastructureProvisioner.builder().name("ShellScriptProvisioner").build());

    yamlBuilder.convertIdToNameForKnownTypes(PROVISIONER_ID, objectValue, outputProperties, APP_ID, new HashMap<>());

    assertThat(outputProperties.size()).isEqualTo(1);
    assertThat(outputProperties.get(PROVISIONER_ID)).isNull();
    assertThat(outputProperties.get(PROVISIONER_NAME)).isEqualTo("ShellScriptProvisioner");
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testConvertIdToNameForKnownTypesWithExpression() {
    Object objectValue = "${provisioner.name.exp}";
    Map<String, Object> outputProperties = new HashMap<>();
    yamlBuilder.convertIdToNameForKnownTypes(PROVISIONER_ID, objectValue, outputProperties, APP_ID, new HashMap<>());

    assertThat(outputProperties.size()).isEqualTo(1);
    assertThat(outputProperties.get(PROVISIONER_ID)).isNull();
    assertThat(outputProperties.get(PROVISIONER_NAME)).isEqualTo("${provisioner.name.exp}");
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testConvertNameToIdForKnownTypesWithExpression() {
    Object objectValue = "${provisioner.name.exp}";
    Map<String, Object> outputProperties = new HashMap<>();
    yamlBuilder.convertNameToIdForKnownTypes(
        PROVISIONER_NAME, objectValue, outputProperties, APP_ID, ACCOUNT_ID, new HashMap<>());

    assertThat(outputProperties.size()).isEqualTo(1);
    assertThat(outputProperties.get(PROVISIONER_NAME)).isNull();
    assertThat(outputProperties.get(PROVISIONER_ID)).isEqualTo("${provisioner.name.exp}");
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testConvertNameToIdForKnownTypes() {
    Object objectValue = "ShellScriptProvisioner";
    Map<String, Object> outputProperties = new HashMap<>();

    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
    when(infrastructureProvisionerService.getByName(APP_ID, (String) objectValue))
        .thenReturn(ShellScriptInfrastructureProvisioner.builder()
                        .name(String.valueOf(objectValue))
                        .uuid("id_of_provisioner")
                        .build());

    yamlBuilder.convertNameToIdForKnownTypes(
        PROVISIONER_NAME, objectValue, outputProperties, APP_ID, ACCOUNT_ID, new HashMap<>());

    assertThat(outputProperties.size()).isEqualTo(1);
    assertThat(outputProperties.get(PROVISIONER_NAME)).isNull();
    assertThat(outputProperties.get(PROVISIONER_ID)).isEqualTo("id_of_provisioner");
  }
}
