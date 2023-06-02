/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.terraform.TerraformPlanParam;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class TerraformPlanHelperTest extends WingsBaseTest {
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private ExecutionContext context;
  @Inject @InjectMocks @Spy private TerraformPlanHelper terraformPlanHelper;

  private EncryptedRecordData data;
  private SweepingOutputInstance sweepingOutput;
  private final String planName = "terraformPlan";

  @Before
  public void setup() throws IllegalAccessException {
    data = EncryptedRecordData.builder().uuid("1").name("name").build();
    sweepingOutput = SweepingOutputInstance.builder()
                         .uuid("1")
                         .value(TerraformPlanParam.builder().encryptedRecordData(data).build())
                         .build();
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());
    when(context.getAppId()).thenReturn(APP_ID);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveEncryptedTfPlanToSweepingOutput() {
    doReturn(null).when(terraformPlanHelper).getEncryptedTfPlanFromSweepingOutput(any(), any());
    doReturn(SweepingOutputInstance.builder())
        .when(context)
        .prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class));
    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    terraformPlanHelper.saveEncryptedTfPlanToSweepingOutput(data, context, planName);

    verify(terraformPlanHelper, never()).deleteEncryptedTfPlanFromSweepingOutput(any(), any());
    verify(sweepingOutputService, times(1)).save(captor.capture());
    SweepingOutputInstance sweepingOutput = captor.getValue();
    TerraformPlanParam terraformPlanParam = (TerraformPlanParam) sweepingOutput.getValue();
    assertThat(terraformPlanParam.getEncryptedRecordData().getUuid()).isEqualTo("1");
    assertThat(terraformPlanParam.getEncryptedRecordData().getName()).isEqualTo("name");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveAlreadySavedTfPlanToSweepingOutput() {
    doReturn(data).when(terraformPlanHelper).getEncryptedTfPlanFromSweepingOutput(any(), any());
    doNothing().when(terraformPlanHelper).deleteEncryptedTfPlanFromSweepingOutput(any(), any());
    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    terraformPlanHelper.saveEncryptedTfPlanToSweepingOutput(data, context, planName);

    verify(terraformPlanHelper, times(1)).deleteEncryptedTfPlanFromSweepingOutput(any(), any());
    verify(sweepingOutputService, times(1)).save(captor.capture());
    SweepingOutputInstance sweepingOutput = captor.getValue();
    TerraformPlanParam terraformPlanParam = (TerraformPlanParam) sweepingOutput.getValue();
    assertThat(terraformPlanParam.getEncryptedRecordData()).isEqualTo(data);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetEncryptedTfPlanFromSweepingOutput() {
    doReturn(sweepingOutput).when(sweepingOutputService).find(any());
    EncryptedRecordData tfPlanFromSweepingOutput =
        terraformPlanHelper.getEncryptedTfPlanFromSweepingOutput(context, planName);
    assertThat(tfPlanFromSweepingOutput.getUuid()).isEqualTo(data.getUuid());
    assertThat(tfPlanFromSweepingOutput.getName()).isEqualTo(data.getName());

    doReturn(null).when(sweepingOutputService).find(any());
    assertThat(terraformPlanHelper.getEncryptedTfPlanFromSweepingOutput(context, planName)).isNull();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteEncryptedTfPlanFromSweepingOutput() {
    doReturn(sweepingOutput).when(sweepingOutputService).find(any());
    terraformPlanHelper.deleteEncryptedTfPlanFromSweepingOutput(context, planName);
    verify(sweepingOutputService, times(1)).deleteById(APP_ID, "1");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteEncryptedTfPlanFromSweepingOutputWithNullValue() {
    doReturn(null).when(sweepingOutputService).find(any());
    terraformPlanHelper.deleteEncryptedTfPlanFromSweepingOutput(context, planName);
    verify(sweepingOutputService, never()).deleteById(APP_ID, "1");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRemoveTfPlanJsonFileIdFromSweepingOutput() {
    sweepingOutput = SweepingOutputInstance.builder()
                         .uuid("1")
                         .value(TerraformPlanParam.builder()
                                    .tfplanHumanReadable("HumanReadablePlan")
                                    .tfplanHumanReadableFileId("HumanReadablePlanId")
                                    .tfPlanJsonFileId("tfJsonFileId")
                                    .build())
                         .build();
    doReturn(sweepingOutput).when(sweepingOutputService).find(any());

    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    terraformPlanHelper.removeTfPlanJsonFileIdFromSweepingOutput(
        context, "terraformApply", SweepingOutputInstance.Scope.PIPELINE);
    verify(sweepingOutputService, times(1)).deleteById(APP_ID, "1");
    verify(sweepingOutputService, times(1)).save(captor.capture());
    SweepingOutputInstance sweepingOutput = captor.getValue();
    TerraformPlanParam terraformPlanParam = (TerraformPlanParam) sweepingOutput.getValue();
    assertThat(terraformPlanParam.getTfplanHumanReadableFileId()).isEqualTo("HumanReadablePlanId");
    assertThat(terraformPlanParam.getTfplanHumanReadable()).isEqualTo("HumanReadablePlan");
    assertThat(terraformPlanParam.getTfPlanJsonFileId()).isEqualTo(null);
  }
}
