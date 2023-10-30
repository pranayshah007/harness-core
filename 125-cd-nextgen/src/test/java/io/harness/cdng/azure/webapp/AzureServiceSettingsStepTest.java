/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.azure.config.ApplicationSettingsOutcome;
import io.harness.cdng.azure.config.ConnectionStringsOutcome;
import io.harness.cdng.azure.config.StartupCommandOutcome;
import io.harness.cdng.azure.config.yaml.ApplicationSettingsConfiguration;
import io.harness.cdng.azure.config.yaml.ConnectionStringsConfiguration;
import io.harness.cdng.azure.config.yaml.StartupCommandConfiguration;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.steps.ManifestsStepV2;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.S3UrlStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.AzureWebAppServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretusage.SecretRuntimeUsageService;
import io.harness.steps.EntityReferenceExtractorUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AzureServiceSettingsStepTest {
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private AzureHelperService azureHelperService;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private CDExpressionResolver expressionResolver;
  @Mock EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock private SecretRuntimeUsageService secretRuntimeUsageService;
  @Mock private PipelineRbacHelper pipelineRbacHelper;

  private final ApplicationSettingsConfiguration appSettingConfig =
      ApplicationSettingsConfiguration.builder()
          .store(StoreConfigWrapper.builder()
                     .spec(GithubStore.builder().branch(pf("main")).connectorRef(pf("gitconnector")).build())
                     .build())
          .build();
  private final ConnectionStringsConfiguration connectionStringConfig =
      ConnectionStringsConfiguration.builder()
          .store(StoreConfigWrapper.builder().spec(InlineStoreConfig.builder().content(pf("a:b")).build()).build())
          .build();
  private final StartupCommandConfiguration startupCommandConfig =
      StartupCommandConfiguration.builder()
          .store(StoreConfigWrapper.builder()
                     .spec(S3UrlStoreConfig.builder().connectorRef(pf("aws")).region(pf("us-east-1")).build())
                     .build())
          .build();

  @InjectMocks private final AzureServiceSettingsStep step = new AzureServiceSettingsStep();
  private final EmptyStepParameters stepParameters = new EmptyStepParameters();

  private AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSync_0() {
    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("applicationSettings").build());
    listEntityDetail.add(EntityDetail.builder().name("connectionStrings").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();

    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));

    doReturn(getServiceConfig(appSettingConfig, connectionStringConfig, startupCommandConfig))
        .when(cdStepHelper)
        .fetchServiceConfigFromSweepingOutput(any(Ambiance.class));

    StepResponse response = step.executeSync(buildAmbiance(), stepParameters, null, null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    Map<Class, Outcome> outcomes = response.getStepOutcomes()
                                       .stream()
                                       .map(StepResponse.StepOutcome::getOutcome)
                                       .collect(Collectors.toMap(Outcome::getClass, Function.identity()));

    ApplicationSettingsOutcome appSettingsOutcome =
        (ApplicationSettingsOutcome) outcomes.get(ApplicationSettingsOutcome.class);
    ConnectionStringsOutcome connStringsOutcome =
        (ConnectionStringsOutcome) outcomes.get(ConnectionStringsOutcome.class);
    StartupCommandOutcome startupCommandOutcome = (StartupCommandOutcome) outcomes.get(StartupCommandOutcome.class);

    assertThat(appSettingsOutcome.getStore()).isEqualTo(appSettingConfig.getStore().getSpec());
    assertThat(connStringsOutcome.getStore()).isEqualTo(connectionStringConfig.getStore().getSpec());
    assertThat(startupCommandOutcome.getStore()).isEqualTo(startupCommandConfig.getStore().getSpec());
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(any(), any(List.class), any(Boolean.class));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSync_1() {
    doReturn(getServiceConfig(appSettingConfig, null, null))
        .when(cdStepHelper)
        .fetchServiceConfigFromSweepingOutput(any(Ambiance.class));

    StepResponse response = step.executeSync(buildAmbiance(), stepParameters, null, null);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    Map<Class, Outcome> outcomes = response.getStepOutcomes()
                                       .stream()
                                       .map(StepResponse.StepOutcome::getOutcome)
                                       .collect(Collectors.toMap(Outcome::getClass, Function.identity()));

    ApplicationSettingsOutcome appSettingsOutcome =
        (ApplicationSettingsOutcome) outcomes.get(ApplicationSettingsOutcome.class);
    ConnectionStringsOutcome connStringsOutcome =
        (ConnectionStringsOutcome) outcomes.get(ConnectionStringsOutcome.class);
    StartupCommandOutcome startupCommandOutcome = (StartupCommandOutcome) outcomes.get(StartupCommandOutcome.class);

    assertThat(appSettingsOutcome.getStore()).isEqualTo(appSettingConfig.getStore().getSpec());
    assertThat(connStringsOutcome).isEqualTo(null);
    assertThat(startupCommandOutcome).isEqualTo(null);
  }

  private Optional<NGServiceV2InfoConfig> getServiceConfig(ApplicationSettingsConfiguration appSettings,
      ConnectionStringsConfiguration connectionStringConfig, StartupCommandConfiguration startupCommand) {
    NGServiceV2InfoConfig config =
        NGServiceV2InfoConfig.builder()
            .identifier("service-id")
            .name("service-name")
            .serviceDefinition(ServiceDefinition.builder()
                                   .type(ServiceDefinitionType.AZURE_WEBAPP)
                                   .serviceSpec(AzureWebAppServiceSpec.builder()
                                                    .applicationSettings(appSettings)
                                                    .connectionStrings(connectionStringConfig)
                                                    .startupCommand(startupCommand)
                                                    .build())
                                   .build())
            .build();
    return Optional.of(config);
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(generateUuid())
                   .setSetupId(generateUuid())
                   .setStepType(ManifestsStepV2.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .putAllSetupAbstractions(
            Map.of("accountId", "ACCOUNT_ID", "orgIdentifier", "ORG_ID", "projectIdentifier", "PROJECT_ID"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .build();
  }

  private ParameterField<String> pf(String val) {
    return ParameterField.createValueField(val);
  }
}
