/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.EntityReferenceExtractorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ConfigFilesStepV2Test extends CategoryTest {
  @Mock private NGLogCallback mockNgLogCallback;
  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private CDExpressionResolver expressionResolver;
  @Mock private ConnectorService connectorService;
  @Mock private ExecutionSweepingOutputService mockSweepingOutputService;
  @Mock private CDStepHelper cdStepHelper;
  @Mock EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @InjectMocks private final ConfigFilesStepV2 step = new ConfigFilesStepV2();
  private AutoCloseable mocks;
  private static final String ACCOUNT_ID = "accountId";
  private static final String SVC_ID = "SVC_ID";
  private static final String ENV_ID = "ENV_ID";

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                             .build()))
        .when(connectorService)
        .get(anyString(), anyString(), anyString(), anyString());

    // mock serviceStepsHelper
    doReturn(mockNgLogCallback).when(serviceStepsHelper).getServiceLogCallback(Mockito.any());
    doReturn(mockNgLogCallback).when(serviceStepsHelper).getServiceLogCallback(Mockito.any(), Mockito.anyBoolean());
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
  public void executeNoFiles() {
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgConfigFilesMetadataSweepingOutput.builder()
                             .finalSvcConfigFiles(new ArrayList<>())
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT)));

    StepResponse stepResponse = step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
    verify(mockSweepingOutputService, never()).consume(any(), anyString(), any(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSync() {
    ConfigFileWrapper file1 = sampleConfigFile("file1");
    ConfigFileWrapper file2 = sampleConfigFile("file2");
    ConfigFileWrapper file3 = sampleConfigFile("file3");

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgConfigFilesMetadataSweepingOutput.builder()
                             .finalSvcConfigFiles(Arrays.asList(file1, file2, file3))
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT)));
    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("configSecret1").build());
    listEntityDetail.add(EntityDetail.builder().name("configSecret2").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();

    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));
    StepResponse stepResponse = step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);

    ArgumentCaptor<ConfigFilesOutcome> captor = ArgumentCaptor.forClass(ConfigFilesOutcome.class);
    verify(mockSweepingOutputService, times(1)).consume(any(), eq("configFiles"), captor.capture(), eq("STAGE"));
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(any(), any(List.class), any(Boolean.class));
    ConfigFilesOutcome outcome = captor.getValue();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.keySet()).containsExactlyInAnyOrder("file1", "file2", "file3");
    assertThat(outcome.get("file1").getOrder()).isEqualTo(1);
    assertThat(outcome.get("file2").getOrder()).isEqualTo(2);
    assertThat(outcome.get("file3").getOrder()).isEqualTo(3);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeWithInvalidConfigFiles() {
    ConfigFileWrapper file1 =
        ConfigFileWrapper.builder().configFile(ConfigFile.builder().identifier("identifier").build()).build();

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgConfigFilesMetadataSweepingOutput.builder()
                             .finalSvcConfigFiles(Arrays.asList(file1))
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT)));
    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("configSecret1").build());
    listEntityDetail.add(EntityDetail.builder().name("configSecret2").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();

    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null))
        .withMessageContaining("configFiles[0].configFile.spec: must not be null");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncConnectorNotFound() {
    doReturn(Optional.empty()).when(connectorService).get(anyString(), anyString(), anyString(), anyString());
    ConfigFileWrapper file1 = sampleConfigFile("file404");

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgConfigFilesMetadataSweepingOutput.builder()
                             .finalSvcConfigFiles(Collections.singletonList(file1))
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT)));

    try {
      step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).contains("Connector not found");
      assertThat(ex.getMessage()).contains("my-connector");
      return;
    }

    fail("expected to throw exception");
  }

  private ConfigFileWrapper sampleConfigFile(String identifier) {
    return ConfigFileWrapper.builder()
        .configFile(ConfigFile.builder()
                        .identifier(identifier)
                        .spec(ConfigFileAttributes.builder()
                                  .store(ParameterField.createValueField(
                                      StoreConfigWrapper.builder()
                                          .spec(GitStore.builder()
                                                    .connectorRef(ParameterField.createValueField("my-connector"))
                                                    .build())
                                          .type(StoreConfigType.GIT)
                                          .build()))
                                  .build())
                        .build())
        .build();
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(generateUuid())
                   .setSetupId(generateUuid())
                   .setStepType(ConfigFilesStepV2.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .putAllSetupAbstractions(
            Map.of("accountId", ACCOUNT_ID, "orgIdentifier", "ORG_ID", "projectIdentifier", "PROJECT_ID"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .build();
  }
}
