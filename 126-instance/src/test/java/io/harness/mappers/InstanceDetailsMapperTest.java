/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.entities.ArtifactDetails;
import io.harness.models.InstanceDetailsDTO;
import io.harness.rule.Owner;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class InstanceDetailsMapperTest extends InstancesTestBase {
  private static final String ARTIFACT_ID = "primary";
  private static final String BUILD_ID = "10";
  private static final String DISPLAY_NAME = "harness/todolist-sample";
  @Inject InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService;
  private InstanceDetailsMapper instanceDetailsMapper;

  @Before
  public void setup() {
    instanceDetailsMapper = new InstanceDetailsMapper(instanceSyncHandlerFactoryService);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToInstanceDetailsDTOWhenArtifactDisplayNameIsPresent() {
    ArtifactDetails artifactDetails =
        ArtifactDetails.builder().artifactId(ARTIFACT_ID).tag(BUILD_ID).displayName(DISPLAY_NAME).build();
    InstanceDTO instanceDTO = createInstanceDTO(artifactDetails);

    List<InstanceDetailsDTO> instanceDetailsDTOList =
        instanceDetailsMapper.toInstanceDetailsDTOList(Collections.singletonList(instanceDTO));

    assertEquals(1, instanceDetailsDTOList.size());
    assertEquals(DISPLAY_NAME, instanceDetailsDTOList.get(0).getArtifactName());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToInstanceDetailsDTOWhenArtifactDisplayNameIsAbsent() {
    ArtifactDetails artifactDetails = ArtifactDetails.builder().artifactId(ARTIFACT_ID).tag(BUILD_ID).build();
    InstanceDTO instanceDTO = createInstanceDTO(artifactDetails);

    List<InstanceDetailsDTO> instanceDetailsDTOList =
        instanceDetailsMapper.toInstanceDetailsDTOList(Collections.singletonList(instanceDTO));

    assertEquals(1, instanceDetailsDTOList.size());
    assertEquals(BUILD_ID, instanceDetailsDTOList.get(0).getArtifactName());
  }

  private InstanceDTO createInstanceDTO(ArtifactDetails artifactDetails) {
    return InstanceDTO.builder()
        .instanceInfoDTO(K8sInstanceInfoDTO.builder().build())
        .primaryArtifact(artifactDetails)
        .build();
  }
}
