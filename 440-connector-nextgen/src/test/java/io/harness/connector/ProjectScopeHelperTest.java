/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterConfig;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.ProjectService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;

public class ProjectScopeHelperTest extends CategoryTest {
  @InjectMocks ProjectScopeHelper projectScopeHelper;
  @Mock ProjectService projectService;
  String projectIdentifier1, projectIdentifier2, projectIdentifier3;
  Connector connector1, connector2, connector3;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    projectIdentifier1 = "project1";
    projectIdentifier2 = "project2";
    projectIdentifier3 = "project3";
    connector1 = KubernetesClusterConfig.builder().build();
    connector2 = KubernetesClusterConfig.builder().build();
    connector3 = KubernetesClusterConfig.builder().build();

    connector1.setProjectIdentifier(projectIdentifier1);
    connector2.setProjectIdentifier(projectIdentifier2);
    connector3.setProjectIdentifier(projectIdentifier3);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void createProjectIdentifierProjectNameMap() {
    String projectName1 = "project 1";
    String projectName2 = "project 2";
    String projectName3 = "project 3";
    Project project1 = Project.builder().name(projectName1).identifier(projectIdentifier1).build();
    Project project2 = Project.builder().name(projectName2).identifier(projectIdentifier2).build();
    Project project3 = Project.builder().name(projectName3).identifier(projectIdentifier3).build();

    List<String> projectsList = Arrays.asList(projectIdentifier1, projectIdentifier2, projectIdentifier3);
    when(projectService.list(any(), any()))
        .thenReturn(new PageImpl<Project>(Arrays.asList(project1, project2, project3)));
    Map<String, String> projectIdprojectNameMap =
        projectScopeHelper.createProjectIdentifierProjectNameMap(projectsList);
    assertThat(projectIdprojectNameMap.size()).isEqualTo(3);

    assertThat(projectIdprojectNameMap.get(projectIdentifier1)).isEqualTo(projectName1);
    assertThat(projectIdprojectNameMap.get(projectIdentifier2)).isEqualTo(projectName2);
    assertThat(projectIdprojectNameMap.get(projectIdentifier3)).isEqualTo(projectName3);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void getProjectIdentifiers() {
    List<String> connectorList =
        projectScopeHelper.getProjectIdentifiers(Arrays.asList(connector1, connector2, connector3));
    assertThat(connectorList.size()).isEqualTo(3);
    assertThat(connectorList).contains(projectIdentifier1);
    assertThat(connectorList).contains(projectIdentifier2);
    assertThat(connectorList).contains(projectIdentifier2);
  }
}
