/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.visitor.helpers.store.HarnessStoreVisitorHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.joor.Reflect;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class K8sManifestVisitorHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private FileStoreService fileStoreService;
  @InjectMocks private HarnessStoreVisitorHelper harnessStoreVisitorHelper;
  @Spy @InjectMocks private K8sManifestVisitorHelper k8sManifestVisitorHelper;

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testAddReference() {
    Reflect.on(k8sManifestVisitorHelper).set("harnessStoreVisitorHelper", harnessStoreVisitorHelper);
    Reflect.on(harnessStoreVisitorHelper).set("fileStoreService", fileStoreService);
    HarnessStore harnessStore = HarnessStore.builder().build();
    K8sManifest k8sManifest =
        K8sManifest.builder()
            .store(ParameterField.createValueField(
                StoreConfigWrapper.builder().type(StoreConfigType.HARNESS).spec(harnessStore).build()))
            .valuesPaths(ParameterField.createValueField(Collections.singletonList("/k8s/values.yaml")))
            .build();
    String accountId = "acc";
    String orgId = "org";
    String projectId = "proj";
    doReturn(Optional.of(getFileDTO("k8s/values.yaml", "values.yaml")))
        .when(fileStoreService)
        .getByPath(eq(accountId), eq(orgId), eq(projectId), eq("/k8s/values.yaml"));
    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS =
        k8sManifestVisitorHelper.addReference(k8sManifest, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).hasSize(1);
    assertThat(new ArrayList<>(entityDetailProtoDTOS).get(0).getIdentifierRef().getIdentifier().getValue())
        .isEqualTo("identifier");
    assertThat(new ArrayList<>(entityDetailProtoDTOS).get(0).getIdentifierRef().getAccountIdentifier().getValue())
        .isEqualTo(accountId);
    assertThat(new ArrayList<>(entityDetailProtoDTOS).get(0).getIdentifierRef().getOrgIdentifier().getValue())
        .isEqualTo(orgId);
    assertThat(new ArrayList<>(entityDetailProtoDTOS).get(0).getIdentifierRef().getProjectIdentifier().getValue())
        .isEqualTo(projectId);
    assertThat(new ArrayList<>(entityDetailProtoDTOS).get(0).getType().name()).isEqualTo("FILES");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testAddReferenceWithNonHarnessStore() {
    GitStore gitStore = GitStore.builder().build();
    K8sManifest k8sManifest =
        K8sManifest.builder()
            .store(ParameterField.createValueField(
                StoreConfigWrapper.builder().type(StoreConfigType.HARNESS).spec(gitStore).build()))
            .valuesPaths(ParameterField.createValueField(Collections.singletonList("/k8s/values.yaml")))
            .build();
    String accountId = "acc";
    String orgId = "org";
    String projectId = "proj";
    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS =
        k8sManifestVisitorHelper.addReference(k8sManifest, accountId, orgId, projectId, contextMap);
    assertThat(entityDetailProtoDTOS).hasSize(0);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testAddReferenceWithFilePathAsExpression() {
    Reflect.on(k8sManifestVisitorHelper).set("harnessStoreVisitorHelper", harnessStoreVisitorHelper);
    Reflect.on(harnessStoreVisitorHelper).set("fileStoreService", fileStoreService);
    HarnessStore harnessStore = HarnessStore.builder().build();
    K8sManifest k8sManifest =
        K8sManifest.builder()
            .store(ParameterField.createValueField(
                StoreConfigWrapper.builder().type(StoreConfigType.HARNESS).spec(harnessStore).build()))
            .valuesPaths(ParameterField.createValueField(
                Collections.singletonList("<+<+pipeline.variables.boolvalue>==\"True\"?\"/testfile\":\"/qafile\">")))
            .build();
    String accountId = "acc";
    String orgId = "org";
    String projectId = "proj";

    Map<String, Object> contextMap = Collections.emptyMap();
    Set<EntityDetailProtoDTO> entityDetailProtoDTOS =
        k8sManifestVisitorHelper.addReference(k8sManifest, accountId, orgId, projectId, contextMap);
    // no reference added since file path is an expression
    assertThat(entityDetailProtoDTOS).hasSize(0);
  }

  private FileDTO getFileDTO(String path, String name) {
    return FileDTO.builder()
        .name(name)
        .identifier("identifier")
        .fileUsage(FileUsage.MANIFEST_FILE)
        .parentIdentifier("folder")
        .path(path)
        .build();
  }
}
