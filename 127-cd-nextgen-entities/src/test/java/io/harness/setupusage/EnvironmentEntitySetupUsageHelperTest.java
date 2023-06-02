/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.setupusage;

import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.setupusage.EnvironmentSetupUsagePublisher;
import io.harness.rule.Owner;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EnvironmentEntitySetupUsageHelperTest extends CategoryTest {
  @Mock private SimpleVisitorFactory mockedFactory;
  @Mock private EnvironmentSetupUsagePublisher environmentSetupUsagePublisher;
  @Mock EntityReferenceExtractorVisitor mockedVisitor;
  @Mock EntitySetupUsageService setupUsageService;
  private static final String ACCOUNT = "ACCOUNT";
  private static final String ORG = "ORG";
  private static final String PROJECT = "PROJECT";

  private static final String ENV_NAME = "ENV_NAME";
  private final ClassLoader classLoader = this.getClass().getClassLoader();
  @InjectMocks @Inject private EnvironmentEntitySetupUsageHelper environmentEntitySetupUsageHelper;
  private AutoCloseable mocks;
  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }
  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpdateSetupUsages() {
    final Environment environment = getEnvironmentEntity();
    final EntityDetailProtoDTO referredEntityDetail =
        EntityDetailProtoDTO.newBuilder().setType(EntityTypeProtoEnum.CONNECTORS).setName("connectorName").build();
    doReturn(mockedVisitor).when(mockedFactory).obtainEntityReferenceExtractorVisitor(any(), any(), any(), any());
    doReturn(Collections.singleton(referredEntityDetail)).when(mockedVisitor).getEntityReferenceSet();
    environmentEntitySetupUsageHelper.updateSetupUsages(environment, Collections.singleton(referredEntityDetail), null);

    ArgumentCaptor<EntityDetailProtoDTO> referredByCaptor = ArgumentCaptor.forClass(EntityDetailProtoDTO.class);
    ArgumentCaptor<Set> referredCaptor = ArgumentCaptor.forClass(Set.class);
    verify(environmentSetupUsagePublisher, times(1))
        .publishEnvironmentEntitySetupUsage(
            referredByCaptor.capture(), referredCaptor.capture(), eq(ACCOUNT), eq(null));

    final EntityDetailProtoDTO referredByEntity = referredByCaptor.getValue();
    verifyInfrastructureReferredByEntity(environment, referredByEntity);
    final Set<EntityDetailProtoDTO> referredEntityProtoSet = (Set<EntityDetailProtoDTO>) referredCaptor.getValue();
    assertThat(referredEntityProtoSet).hasSize(1);
    final Object[] referredEntityProtoArray = referredEntityProtoSet.toArray();
    final EntityDetailProtoDTO referredEntityProto = (EntityDetailProtoDTO) referredEntityProtoArray[0];
    assertThat(referredEntityProto.getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);
    assertThat(referredEntityProto.getName()).isEqualTo(referredEntityDetail.getName());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testDeleteSetupUsages() {
    final Environment environment = getEnvironmentEntity();
    environmentEntitySetupUsageHelper.deleteSetupUsages(environment);

    ArgumentCaptor<EntityDetailProtoDTO> captor = ArgumentCaptor.forClass(EntityDetailProtoDTO.class);
    verify(environmentSetupUsagePublisher, times(1)).deleteEnvironmentSetupUsages(captor.capture(), eq(ACCOUNT));
    final EntityDetailProtoDTO entityDetailProtoDTO = captor.getValue();
    verifyInfrastructureReferredByEntity(environment, entityDetailProtoDTO);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetReferredEntities_WithInvalidReference() throws IOException {
    doReturn(mockedVisitor).when(mockedFactory).obtainEntityReferenceExtractorVisitor(any(), any(), any(), any());
    doReturn(Collections.EMPTY_SET).when(mockedVisitor).getEntityReferenceSet();

    doThrow(
        new InvalidRequestException("The org level connectors cannot be used at account level. Ref: [org.connectorId]"))
        .when(mockedVisitor)
        .walkElementTree(any());

    // account level environment
    String yaml = readFile("ManifestYamlWithoutSpec.yaml");
    final Environment environment =
        Environment.builder().accountId(ACCOUNT).identifier("infra1").name("infra1").yaml(yaml).build();

    assertThatThrownBy(() -> environmentEntitySetupUsageHelper.getAllReferredEntities(environment))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The org level connectors cannot be used at account level. Ref: [org.connectorId]");
  }
  private void verifyInfrastructureReferredByEntity(
      Environment environment, EntityDetailProtoDTO entityDetailProtoDTO) {
    assertThat(entityDetailProtoDTO).isNotNull();
    assertThat(entityDetailProtoDTO.getType()).isEqualTo(EntityTypeProtoEnum.ENVIRONMENT);
    assertThat(entityDetailProtoDTO.getName()).isEqualTo(environment.getName());
  }
  private Environment getEnvironmentEntity() {
    return Environment.builder()
        .accountId(ACCOUNT)
        .orgIdentifier(ORG)
        .projectIdentifier(PROJECT)
        .identifier("environmentId")
        .name("environmentName")
        .build();
  }
  private String readFile(String fileName) throws IOException {
    final URL testFile = classLoader.getResource(fileName);
    return Resources.toString(testFile, Charsets.UTF_8);
  }
}
