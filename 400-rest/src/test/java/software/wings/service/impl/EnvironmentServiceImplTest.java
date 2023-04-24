/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.appmanifest.AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE;
import static software.wings.beans.yaml.YamlConstants.CONN_STRINGS_FILE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Environment;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class EnvironmentServiceImplTest extends WingsBaseTest {
  @Mock FeatureFlagService featureFlagService;
  @Mock AppService appService;
  @Mock InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock WorkflowExecutionService workflowExecutionService;
  @Inject private HarnessTagService harnessTagService;
  @InjectMocks @Inject EnvironmentServiceImpl environmentService;

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldAddInfraDefWithCount() {
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
    PageResponse<Environment> environmentPageResponse = new PageResponse<>();
    Environment env1 = anEnvironment().uuid("ENV_1").build();
    Environment env2 = anEnvironment().uuid("ENV_2").build();
    InfrastructureDefinition infra1 = InfrastructureDefinition.builder().uuid("ID1").build();
    InfrastructureDefinition infra2 = InfrastructureDefinition.builder().uuid("ID2").build();
    environmentPageResponse.setResponse(Arrays.asList(env1, env2));
    doReturn(Arrays.asList(infra1, infra2))
        .when(infrastructureDefinitionService)
        .getNameAndIdForEnvironment(APP_ID, "ENV_1", 5);
    doReturn(Collections.emptyList())
        .when(infrastructureDefinitionService)
        .getNameAndIdForEnvironment(APP_ID, "ENV_2", 5);
    Map<String, Integer> envIdCountMap = new HashMap<>();
    envIdCountMap.put("ENV_1", 2);
    envIdCountMap.put("ENV_2", 0);
    doReturn(envIdCountMap)
        .when(infrastructureDefinitionService)
        .getCountForEnvironments(APP_ID, Arrays.asList("ENV_1", "ENV_2"));

    environmentService.addInfraDefDetailToEnv(APP_ID, environmentPageResponse);

    env1 = environmentPageResponse.getResponse().get(0);
    env2 = environmentPageResponse.getResponse().get(1);
    assertThat(env1.getInfrastructureDefinitions()).hasSize(2);
    assertThat(env2.getInfrastructureDefinitions()).hasSize(0);
    assertThat(env1.getInfraDefinitionsCount()).isEqualTo(2);
    assertThat(env2.getInfraDefinitionsCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testCreateValuesNegativeScenario() {
    ManifestFile manifestFile = ManifestFile.builder().fileName(CONN_STRINGS_FILE).fileContent("").build();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> environmentService.createValues(
                            APP_ID, ENV_ID, SERVICE_ID, manifestFile, AZURE_APP_SETTINGS_OVERRIDE));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldSetServiceDeploymentTypeAndArtifactTypeTag() {
    Environment env1 =
        anEnvironment().uuid(ENV_ID).name(ENV_NAME).appId(APP_ID).environmentType(EnvironmentType.PROD).build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(featureFlagService.isEnabled(FeatureName.HARNESS_TAGS, ACCOUNT_ID)).thenReturn(true);

    Environment savedEnv = environmentService.save(env1);
    List<HarnessTagLink> tagLinksWithEntityId =
        harnessTagService.getTagLinksWithEntityId(ACCOUNT_ID, savedEnv.getUuid());
    assertThat(tagLinksWithEntityId).hasSize(1);
    assertTrue(tagLinksWithEntityId.stream().anyMatch(
        tagLink -> tagLink.getKey().equals("environmentType") && tagLink.getValue().equals("PROD")));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldNoTDeleteIfRunningExecution() {
    Environment env =
        anEnvironment().uuid(ENV_ID).name(ENV_NAME).appId(APP_ID).environmentType(EnvironmentType.PROD).build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    environmentService.save(env);
    when(workflowExecutionService.runningExecutionsForEnvironment(APP_ID, ENV_ID))
        .thenReturn(ImmutableList.of("Execution Name 1"));
    assertThatThrownBy(() -> environmentService.delete(APP_ID, ENV_ID)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void updateEnvironment() {
    Environment savedEnv =
        anEnvironment().uuid(ENV_ID).name(ENV_NAME).appId(APP_ID).environmentType(EnvironmentType.PROD).build();
    Environment updatedEnv = anEnvironment()
                                 .uuid(ENV_ID)
                                 .name("DEV_ENV_NAME")
                                 .appId(APP_ID)
                                 .environmentType(EnvironmentType.NON_PROD)
                                 .build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    environmentService.save(savedEnv);
    environmentService.update(updatedEnv);

    verify(appService, times(3)).getAccountIdByAppId(APP_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetEnvIdByType() {
    when(appService.getAccountIdByAppId(anyString())).thenReturn(ACCOUNT_ID);
    createAndSaveEnvironment(ENV_ID, APP_ID, EnvironmentType.PROD);
    createAndSaveEnvironment(ENV_ID + 2, APP_ID, EnvironmentType.PROD);
    createAndSaveEnvironment(ENV_ID + 3, APP_ID + 2, EnvironmentType.PROD);
    createAndSaveEnvironment(ENV_ID + 4, APP_ID + 3, EnvironmentType.NON_PROD);

    Map<String, Set<String>> envMap = environmentService.getAppIdEnvIdMapByType(
        ImmutableSet.of(APP_ID, APP_ID + 2, APP_ID + 3), EnvironmentType.PROD);
    assertThat(envMap).hasSize(3);
    assertThat(envMap.get(APP_ID)).containsExactlyInAnyOrder(ENV_ID, ENV_ID + 2);
    assertThat(envMap.get(APP_ID + 2)).containsExactlyInAnyOrder(ENV_ID + 3);
    assertThat(envMap.get(APP_ID + 3)).isEmpty();

    envMap = environmentService.getAppIdEnvIdMapByType(ImmutableSet.of(APP_ID + 4, APP_ID + 5), EnvironmentType.PROD);
    assertThat(envMap).hasSize(2);
    assertThat(envMap.get(APP_ID + 4)).isEmpty();
    assertThat(envMap.get(APP_ID + 5)).isEmpty();
  }

  private void createAndSaveEnvironment(String envId, String appId, EnvironmentType prod) {
    Environment env1 = anEnvironment().uuid(envId).appId(appId).environmentType(prod).name(ENV_NAME).build();
    environmentService.save(env1);
  }
}
