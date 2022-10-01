/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.impl.ServiceEntityServiceImpl;
import io.harness.ng.core.service.services.impl.ServiceEntitySetupUsageHelper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.service.spring.ServiceRepository;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.CDC)
public class RefreshInputsHelperTest extends NgManagerTestBase {
  private static final String RESOURCE_PATH_PREFIX = "refresh/validate/";
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  @InjectMocks RefreshInputsHelper refreshInputsHelper;
  @InjectMocks EntityFetchHelper entityFetchHelper;
  @Mock ServiceRepository serviceRepository;
  @Mock EntitySetupUsageService entitySetupUsageService;
  @Mock Producer eventProducer;
  @Mock TransactionTemplate transactionTemplate;
  @Mock OutboxService outboxService;
  @Mock ServiceOverrideService serviceOverrideService;
  @Mock ServiceEntitySetupUsageHelper entitySetupUsageHelper;
  ServiceEntityServiceImpl serviceEntityService;

  @Before
  public void setup() {
    serviceEntityService = spy(new ServiceEntityServiceImpl(serviceRepository, entitySetupUsageService, eventProducer,
        outboxService, transactionTemplate, serviceOverrideService, entitySetupUsageHelper));
    on(entityFetchHelper).set("serviceEntityService", serviceEntityService);
    on(refreshInputsHelper).set("serviceEntityService", serviceEntityService);
    on(refreshInputsHelper).set("entityFetchHelper", entityFetchHelper);
  }

  private String readFile(String filename) {
    String relativePath = RESOURCE_PATH_PREFIX + filename;
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(relativePath)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshInputsForPipelineYamlWithValidService() {
    String pipelineYmlWithService = readFile("pipeline-with-single-service.yaml");
    String serviceYaml = readFile("serverless-service-valid.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    String refreshedYaml = refreshInputsHelper.refreshInputs(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService);
    assertThat(refreshedYaml).isNotNull().isNotEmpty();
    assertThat(refreshedYaml).isEqualTo(pipelineYmlWithService);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshInputsForPipelineYamlWithServiceInputsEmptyInServiceAndNoServiceInputsInLinkedYaml() {
    String pipelineYmlWithService = readFile("pipeline-with-no-serviceInputs.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    String refreshedYaml = refreshInputsHelper.refreshInputs(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService);
    assertThat(refreshedYaml).isNotNull().isNotEmpty();
    assertThat(refreshedYaml).isEqualTo(pipelineYmlWithService);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshInputsForPipelineYamlWithInvalidServiceHavingFixedPrimaryArtifactRef() {
    String pipelineYmlWithService = readFile("pipeline-with-single-service.yaml");
    String serviceYaml = readFile("serverless-service.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    String refreshedYaml = refreshInputsHelper.refreshInputs(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService);
    assertThat(refreshedYaml).isNotNull().isNotEmpty();
    assertThat(refreshedYaml).isEqualTo(readFile("pipeline-with-single-service-refreshed.yaml"));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshInputsForPipelineYamlWithServiceRuntimeAndServiceInputsFixed() {
    String pipelineYmlWithService = readFile("pipeline-with-svc-runtime-serviceInputs-fixed.yaml");

    String refreshedYaml = refreshInputsHelper.refreshInputs(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService);
    assertThat(refreshedYaml).isNotNull();
    assertThat(refreshedYaml).isEqualTo(readFile("pipeline-with-svc-runtime-serviceInputs-fixed-refreshed.yaml"));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshInputsForPipelineYamlWithPrimaryRefFixedAndSourcesRuntime() {
    String pipelineYmlWithService = readFile("pipeline-with-primaryRef-fixed-source-runtime.yaml");
    String serviceYaml = readFile("serverless-service.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    String refreshedYaml = refreshInputsHelper.refreshInputs(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService);
    assertThat(refreshedYaml).isNotNull();
    assertThat(refreshedYaml).isEqualTo(readFile("pipeline-with-primaryRef-fixed-source-runtime-refreshed.yaml"));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithServiceInputsEmptyInService() {
    String pipelineYmlWithService = readFile("pipeline-with-single-service.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    String refreshedYaml = refreshInputsHelper.refreshInputs(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService);
    assertThat(refreshedYaml).isNotNull();
    assertThat(refreshedYaml).isEqualTo(readFile("pipeline-with-single-service-refreshed-with-no-serviceInputs.yaml"));
  }
}
