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
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.services.OrganizationService;
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

public class OrgScopeHelperTest extends CategoryTest {
  @InjectMocks OrgScopeHelper orgScopeHelper;
  @Mock OrganizationService organizationService;
  String orgIdentifier1, orgIdentifier2, orgIdentifier3;
  Connector connector1, connector2, connector3;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    orgIdentifier1 = "orgIdentifier1";
    orgIdentifier2 = "orgIdentifier2";
    orgIdentifier3 = "orgIdentifier3";

    connector1 = KubernetesClusterConfig.builder().build();
    connector2 = KubernetesClusterConfig.builder().build();
    connector3 = KubernetesClusterConfig.builder().build();

    connector1.setOrgIdentifier(orgIdentifier1);
    connector2.setOrgIdentifier(orgIdentifier2);
    connector3.setOrgIdentifier(orgIdentifier3);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void createOrgIdentifierOrgNameMapTest() {
    String orgName1 = "Org 1";
    String orgName2 = "Org 2";
    String orgName3 = "Org 3";
    Organization org1 = Organization.builder().name(orgName1).identifier(orgIdentifier1).build();
    Organization org2 = Organization.builder().name(orgName2).identifier(orgIdentifier2).build();
    Organization org3 = Organization.builder().name(orgName3).identifier(orgIdentifier3).build();

    List<String> orgIdentifiersList = Arrays.asList(orgIdentifier1, orgIdentifier2, orgIdentifier3);
    when(organizationService.list(any(), any()))
        .thenReturn(new PageImpl<Organization>(Arrays.asList(org1, org2, org3)));
    Map<String, String> orgIdOrgNameMap = orgScopeHelper.createOrgIdentifierOrgNameMap(orgIdentifiersList);
    assertThat(orgIdOrgNameMap.size()).isEqualTo(3);

    assertThat(orgIdOrgNameMap.get(orgIdentifier1)).isEqualTo(orgName1);
    assertThat(orgIdOrgNameMap.get(orgIdentifier2)).isEqualTo(orgName2);
    assertThat(orgIdOrgNameMap.get(orgIdentifier3)).isEqualTo(orgName3);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void getOrgIdentifiersTest() {
    List<String> connectorList = orgScopeHelper.getOrgIdentifiers(Arrays.asList(connector1, connector2, connector3));
    assertThat(connectorList.size()).isEqualTo(3);
    assertThat(connectorList).contains(orgIdentifier1);
    assertThat(connectorList).contains(orgIdentifier2);
    assertThat(connectorList).contains(orgIdentifier3);
  }
}
