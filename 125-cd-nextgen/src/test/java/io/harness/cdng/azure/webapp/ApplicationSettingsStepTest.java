/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.azure.config.ApplicationSettingsOutcome;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStoreFile;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.steps.ServiceStepsHelper;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class ApplicationSettingsStepTest extends CDNGTestBase {
  private static final String FILE_PATH = "file/path";
  private static final String MASTER = "master";
  private static final String COMMIT_ID = "commitId";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String REPO_NAME = "repoName";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  @Mock private NGLogCallback logCallback;

  @Mock private AzureHelperService azureHelperService;
  @Mock private ServiceStepsHelper serviceStepsHelper;

  @InjectMocks private ApplicationSettingsStep applicationSettingsStep;

  @Before
  public void setup() {
    doReturn(logCallback).when(serviceStepsHelper).getServiceLogCallback(any(Ambiance.class));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(applicationSettingsStep.getStepParametersClass()).isEqualTo(ApplicationSettingsParameters.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteSyncHarnessStore() {
    Ambiance ambiance = getAmbiance();
    StoreConfigWrapper storeConfigWrapper = getStoreConfigWrapper();

    ApplicationSettingsParameters stepParameters =
        ApplicationSettingsParameters.builder().applicationSettings(storeConfigWrapper).build();
    StepResponse response =
        applicationSettingsStep.executeSync(ambiance, stepParameters, getStepInputPackage(), getPassThroughData());

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);

    StepResponse.StepOutcome[] stepOutcomes = response.getStepOutcomes().toArray(new StepResponse.StepOutcome[1]);
    ApplicationSettingsOutcome applicationSettingsOutcome = (ApplicationSettingsOutcome) stepOutcomes[0].getOutcome();
    assertThat(applicationSettingsOutcome.getStore()).isEqualTo(storeConfigWrapper.getSpec());

    assertThat(applicationSettingsOutcome.getStore().getKind()).isEqualTo(StoreConfigType.HARNESS.getDisplayName());
    HarnessStore store = (HarnessStore) applicationSettingsOutcome.getStore();
    HarnessStoreFile harnessStoreFile = store.getFiles().getValue().get(0);

    assertThat(harnessStoreFile.getPath().getValue()).isEqualTo(FILE_PATH);
    verify(azureHelperService).validateSettingsStoreReferences(storeConfigWrapper, ambiance, ApplicationSettingsStep.ENTITY_TYPE);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteSyncGitStore() {
    Ambiance ambiance = getAmbiance();

    StoreConfigWrapper storeConfigWrapper = getStoreConfigWrapperWithGitStore();
    ApplicationSettingsParameters stepParameters =
        ApplicationSettingsParameters.builder().applicationSettings(storeConfigWrapper).build();

    StepResponse response =
        applicationSettingsStep.executeSync(ambiance, stepParameters, getStepInputPackage(), getPassThroughData());

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);

    StepResponse.StepOutcome[] stepOutcomes = response.getStepOutcomes().toArray(new StepResponse.StepOutcome[1]);
    ApplicationSettingsOutcome applicationSettingsOutcome = (ApplicationSettingsOutcome) stepOutcomes[0].getOutcome();
    assertThat(applicationSettingsOutcome.getStore()).isEqualTo(storeConfigWrapper.getSpec());

    assertThat(applicationSettingsOutcome.getStore().getKind()).isEqualTo(StoreConfigType.GIT.getDisplayName());
    GitStore store = (GitStore) applicationSettingsOutcome.getStore();
    assertThat(store.getBranch().getValue()).isEqualTo(MASTER);
    assertThat(store.getCommitId().getValue()).isEqualTo(COMMIT_ID);
    assertThat(store.getConnectorRef().getValue()).isEqualTo(CONNECTOR_REF);
    assertThat(store.getRepoName().getValue()).isEqualTo(REPO_NAME);
    verify(azureHelperService).validateSettingsStoreReferences(storeConfigWrapper, ambiance, ApplicationSettingsStep.ENTITY_TYPE);
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
        .build();
  }

  private StepInputPackage getStepInputPackage() {
    return StepInputPackage.builder().build();
  }

  private StepExceptionPassThroughData getPassThroughData() {
    return StepExceptionPassThroughData.builder().build();
  }

  private StoreConfigWrapper getStoreConfigWrapper() {
    return StoreConfigWrapper.builder()
        .type(StoreConfigType.HARNESS)
        .spec(HarnessStore.builder().files(getFiles()).build())
        .build();
  }

  private ParameterField<List<HarnessStoreFile>> getFiles() {
    return ParameterField.createValueField(Collections.singletonList(getHarnessFile()));
  }

  private HarnessStoreFile getHarnessFile() {
    return HarnessStoreFile.builder()
        .path(ParameterField.createValueField(FILE_PATH))
        .build();
  }

  private StoreConfigWrapper getStoreConfigWrapperWithGitStore() {
    return StoreConfigWrapper.builder()
        .type(StoreConfigType.GIT)
        .spec(GitStore.builder()
                  .branch(ParameterField.createValueField(MASTER))
                  .commitId(ParameterField.createValueField(COMMIT_ID))
                  .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                  .repoName(ParameterField.createValueField(REPO_NAME))
                  .build())
        .build();
  }
}
