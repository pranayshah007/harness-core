/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import io.harness.ccm.views.service.PolicyExecutionService;
import static io.harness.rule.OwnerRule.SAHIBA;

import io.harness.telemetry.TelemetryReporter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.remote.resources.governance.GovernancePolicyResource;
import io.harness.ccm.views.dto.CreatePolicyDTO;
import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.PolicyCloudProviderType;
import io.harness.ccm.views.entities.PolicyStoreType;
import io.harness.ccm.views.service.GovernancePolicyService;
import io.harness.ccm.views.service.PolicyEnforcementService;
import io.harness.ccm.views.service.PolicyPackService;
import io.harness.connector.ConnectorResourceClient;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GovernancePolicyResourceTest extends CategoryTest {
  private GovernancePolicyService governancePolicyService = mock(GovernancePolicyService.class);
  private PolicyPackService policyPackService = mock(PolicyPackService.class);
  private PolicyEnforcementService policyEnforcementService = mock(PolicyEnforcementService.class);
  private CCMRbacHelper rbacHelper = mock(CCMRbacHelper.class);
  private ConnectorResourceClient connectorResourceClient = mock(ConnectorResourceClient.class);
  private PolicyExecutionService policyExecutionService = mock(PolicyExecutionService.class);
  private TelemetryReporter telemetryReporter= mock(TelemetryReporter.class);

  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String uuid = "UUID";
  private final String NAME = "NAME";
  private final String POLICY = "POLICY";
  private final List<String> TAGS = Arrays.asList(new String[] {"TAGS"});
  private final String DESCRIPTION = "DESCRIPTION";
  private final String ORG_PARAM_MESSAGE = "ORG_PARAM_MESSAGE";
  private final String PROJECT_PARAM_MESSAGE = "PROJECT_PARAM_MESSAGE";
  private final String STORE_TYPE = "INLINE";
  private final String CLOUD_PROVIDER = "AWS";
  private final Boolean OTTB = false;
  private final Boolean DELETED = false;
  private final Boolean STABLE = true;
  private final String VERSION = "VERSION";

  private Policy policy;
  private GovernancePolicyResource policyManagement;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    policy = Policy.builder()
                 .uuid(uuid)
                 .accountId(ACCOUNT_ID)
                 .name(NAME)
                 .description(DESCRIPTION)
                 .policyYaml(POLICY)
                 .tags(TAGS)
                 .cloudProvider(PolicyCloudProviderType.valueOf(CLOUD_PROVIDER))
                 .orgIdentifier(ORG_PARAM_MESSAGE)
                 .projectIdentifier(PROJECT_PARAM_MESSAGE)
                 .isOOTB(OTTB)
                 .deleted(DELETED)
                 .isStablePolicy(STABLE)
                 .storeType(PolicyStoreType.valueOf(STORE_TYPE))
                 .versionLabel(VERSION)
                 .build();
    policyManagement = new GovernancePolicyResource(
        governancePolicyService, rbacHelper, policyEnforcementService, policyPackService, connectorResourceClient,policyExecutionService,telemetryReporter);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testCreatePolicy() {
    policyManagement.create(ACCOUNT_ID, CreatePolicyDTO.builder().policy(policy).build());
    verify(governancePolicyService).save(policy);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testUpadtePolicy() {
    policyManagement.updatePolicy(ACCOUNT_ID, CreatePolicyDTO.builder().policy(policy).build());
    verify(governancePolicyService).update(policy, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void deletePolicy() {
    policyManagement.delete(ACCOUNT_ID, uuid);
    verify(governancePolicyService).delete(ACCOUNT_ID, uuid);
  }
}
