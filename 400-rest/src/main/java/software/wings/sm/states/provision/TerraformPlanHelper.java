/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.terraform.TerraformPlanParam;
import io.harness.encryptors.clients.AwsKmsEncryptor;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.bson.BSONException;

@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class TerraformPlanHelper {
  @Inject protected SweepingOutputService sweepingOutputService;
  @Inject protected WingsPersistence wingsPersistence;
  @Inject protected SecretManager secretManager;
  @Inject protected AwsKmsEncryptor awsKmsEncryptor;

  public void saveEncryptedTfPlanToSweepingOutput(EncryptedRecordData data, ExecutionContext context, String planName) {
    try {
      EncryptedRecordData encryptedTfPlan = getEncryptedTfPlanFromSweepingOutput(context, planName);
      if (encryptedTfPlan != null) {
        deleteEncryptedTfPlanFromSweepingOutput(context, planName);
      }
      sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                     .name(planName)
                                     .value(TerraformPlanParam.builder().encryptedRecordData(data).build())
                                     .build());
    } catch (BSONException e) {
      throw new WingsException(e.getMessage(), e);
    }
  }

  public EncryptedRecordData getEncryptedTfPlanFromSweepingOutput(ExecutionContext context, String planName) {
    SweepingOutputInstance sweepingOutputInstance =
        sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder().name(planName).build());

    if (sweepingOutputInstance != null) {
      return ((TerraformPlanParam) sweepingOutputInstance.getValue()).getEncryptedRecordData();
    }
    return null;
  }

  @VisibleForTesting
  void deleteEncryptedTfPlanFromSweepingOutput(ExecutionContext context, String planName) {
    SweepingOutputInstance sweepingOutputInstance =
        sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder().name(planName).build());
    if (sweepingOutputInstance != null) {
      // delete the plan reference from sweeping output if present
      sweepingOutputService.deleteById(context.getAppId(), sweepingOutputInstance.getUuid());
    }
  }
}
