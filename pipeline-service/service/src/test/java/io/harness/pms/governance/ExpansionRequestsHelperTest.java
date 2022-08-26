/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.ExpansionRequestType.KEY;
import static io.harness.pms.contracts.plan.ExpansionRequestType.LOCAL_FQN;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class ExpansionRequestsHelperTest extends CategoryTest {
  @InjectMocks ExpansionRequestsHelper expansionRequestsHelper;
  @Mock PmsSdkInstanceService pmsSdkInstanceService;

  List<PmsSdkInstance> activeInstances;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Map<String, Set<String>> cdSupportedTypes = new HashMap<>();
    cdSupportedTypes.put("stage", Collections.singleton("Deployment"));
    cdSupportedTypes.put("step", Collections.singleton("K8sApply"));
    PmsSdkInstance cdInstance =
        PmsSdkInstance.builder()
            .name("cd")
            .jsonExpansionInfo(
                Arrays.asList(JsonExpansionInfo.newBuilder().setKey("connectorRef").setExpansionType(KEY).build(),
                    JsonExpansionInfo.newBuilder().setKey("serviceRef").setExpansionType(KEY).build(),
                    JsonExpansionInfo.newBuilder().setKey("environmentRef").setExpansionType(KEY).build()))
            .supportedTypes(cdSupportedTypes)
            .build();

    Map<String, Set<String>> pmsSupportedTypes = new HashMap<>();
    pmsSupportedTypes.put("stage", Collections.singleton("Approval"));
    pmsSupportedTypes.put("step", Collections.singleton("ShellScript"));
    PmsSdkInstance pmsInstance =
        PmsSdkInstance.builder()
            .name("pms")
            .jsonExpansionInfo(Collections.singletonList(
                JsonExpansionInfo.newBuilder().setKey("connectorRef").setExpansionType(KEY).build()))
            .supportedTypes(pmsSupportedTypes)
            .build();

    PmsSdkInstance arbitrary =
        PmsSdkInstance.builder()
            .name("cf")
            .jsonExpansionInfo(Collections.singletonList(
                JsonExpansionInfo.newBuilder()
                    .setKey("stage/spec")
                    .setExpansionType(LOCAL_FQN)
                    .setStageType(
                        StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("Deployment").build())
                    .build()))
            .supportedTypes(Collections.emptyMap())
            .build();
    activeInstances = Arrays.asList(cdInstance, pmsInstance, arbitrary);
    doReturn(activeInstances).when(pmsSdkInstanceService).getActiveInstances();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetExpandableFieldsPerService() {
    Map<ModuleType, Set<String>> expandableFieldsPerService =
        expansionRequestsHelper.getExpandableFieldsPerService(activeInstances);
    assertThat(expandableFieldsPerService).hasSize(2);
    Set<String> cdKeys = expandableFieldsPerService.get(ModuleType.CD);
    assertThat(cdKeys).hasSize(3);
    assertThat(cdKeys).contains("connectorRef", "serviceRef", "environmentRef");
    Set<String> pmsKeys = expandableFieldsPerService.get(ModuleType.PMS);
    assertThat(pmsKeys).hasSize(1);
    assertThat(pmsKeys).contains("connectorRef");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetTypeToService() {
    Map<String, ModuleType> typeToService = expansionRequestsHelper.getTypeToService(activeInstances);
    assertThat(typeToService).hasSize(4);
    assertThat(typeToService.get("Approval")).isEqualTo(ModuleType.PMS);
    assertThat(typeToService.get("ShellScript")).isEqualTo(ModuleType.PMS);
    assertThat(typeToService.get("Deployment")).isEqualTo(ModuleType.CD);
    assertThat(typeToService.get("K8sApply")).isEqualTo(ModuleType.CD);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetLocalFQNRequestMetadata() {
    List<LocalFQNExpansionInfo> localFQNRequestMetadata =
        expansionRequestsHelper.getLocalFQNRequestMetadata(activeInstances);
    assertThat(localFQNRequestMetadata).hasSize(1);
    LocalFQNExpansionInfo localFQNExpansionInfo = localFQNRequestMetadata.get(0);
    assertThat(localFQNExpansionInfo.getLocalFQN()).isEqualTo("stage/spec");
    assertThat(localFQNExpansionInfo.getStageType()).isEqualTo("Deployment");
    assertThat(localFQNExpansionInfo.getModule()).isEqualTo(ModuleType.CF);
  }
}
