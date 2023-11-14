/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class GovernanceAiEngineServiceImplTest extends CategoryTest {
  @InjectMocks @Inject private GovernanceAiEngineServiceImpl governanceAiEngineService;
  private final Set<String> AWS_RESOURCES = new HashSet<>() {
    {
      add("ami");
      add("asg");
      add("app-elb");
      add("cache-cluster");
      add("ebs");
      add("ebs-snapshot");
      add("ec2");
      add("ec2-host");
      add("ec2-reserved");
      add("ecs-service");
      add("efs");
      add("elastic-ip");
      add("elb");
      add("glue-job");
      add("iam-policy");
      add("iam-user");
      add("lambda");
      add("launch-config");
      add("launch-template-version");
      add("log-group");
      add("network-addr");
      add("rds");
      add("rds-cluster-snapshot");
      add("rds-snapshot");
      add("rds-subnet-group");
      add("redshift-snapshot");
      add("redshift");
      add("s3");
      add("secrets-manager");
    }
  };
  private final Set<String> AZURE_RESOURCES = new HashSet<>() {
    {
      add("azure.cosmosdb");
      add("azure.disk");
      add("azure.keyvault");
      add("azure.loadbalancer");
      add("azure.networkinterface");
      add("azure.publicip");
      add("azure.resourcegroup");
      add("azure.sql-database");
      add("azure.sql-server");
      add("azure.storage-container");
      add("azure.storage");
      add("azure.vm");
    }
  };

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void getGovernancePromptResources_AWS() {
    final Set<String> result = governanceAiEngineService.getGovernancePromptResources(RuleCloudProviderType.AWS);
    assertThat(result).isEqualTo(AWS_RESOURCES);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void getGovernancePromptResources_AZURE() {
    final Set<String> result = governanceAiEngineService.getGovernancePromptResources(RuleCloudProviderType.AZURE);
    assertThat(result).isEqualTo(AZURE_RESOURCES);
  }
}
