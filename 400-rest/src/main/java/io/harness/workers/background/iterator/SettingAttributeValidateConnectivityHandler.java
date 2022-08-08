/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.workers.background.iterator;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofHours;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.WingsException;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.service.impl.SettingValidationService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SettingAttributeValidateConnectivityHandler implements Handler<SettingAttribute> {
  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private SettingsService settingsService;
  @Inject private SettingValidationService settingValidationService;
  @Inject private MorphiaPersistenceProvider<SettingAttribute> persistenceProvider;

  public void registerIterators(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PumpExecutorOptions.builder()
            .name("SettingAttributeValidateConnectivity")
            .poolSize(threadPoolSize)
            .interval(Duration.ofMinutes(10))
            .build(),
        SettingAttributeValidateConnectivityHandler.class,
        MongoPersistenceIterator.<SettingAttribute, MorphiaFilterExpander<SettingAttribute>>builder()
            .clazz(SettingAttribute.class)
            .fieldName(SettingAttributeKeys.nextIteration)
            .targetInterval(ofHours(3))
            .acceptableNoAlertDelay(ofHours(1))
            .handler(this)
            .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
            .filterExpander(query
                -> query.field(SettingAttributeKeys.value_type)
                       .in(asList(SettingVariableTypes.AWS.name(), SettingVariableTypes.GCP.name(),
                           SettingVariableTypes.AZURE.name(), SettingVariableTypes.DOCKER.name(),
                           SettingVariableTypes.ECR.name(), SettingVariableTypes.GCR.name(),
                           SettingVariableTypes.ACR.name(), SettingVariableTypes.ARTIFACTORY.name(),
                           SettingVariableTypes.NEXUS.name(), SettingVariableTypes.JENKINS.name(),
                           SettingVariableTypes.BAMBOO.name(), SettingVariableTypes.GCS.name(),
                           SettingVariableTypes.AMAZON_S3.name(), SettingVariableTypes.AZURE_ARTIFACTS_PAT.name(),
                           SettingVariableTypes.HTTP_HELM_REPO.name(), SettingVariableTypes.AMAZON_S3_HELM_REPO.name(),
                           SettingVariableTypes.OCI_HELM_REPO.name(), SettingVariableTypes.GCS_HELM_REPO.name(),
                           SettingVariableTypes.SMB.name(), SettingVariableTypes.SFTP.name(),
                           SettingVariableTypes.CUSTOM.name())))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(SettingAttribute settingAttribute) {
    log.info("Received validation connectivity check for setting attribute: {}", settingAttribute.getUuid());
    boolean valid = false;
    String errMsg = "";
    try {
      valid = settingValidationService.validate(settingAttribute);
    } catch (InvalidArtifactServerException ex) {
      errMsg = ExceptionUtils.getMessage(ex);
    } catch (WingsException ex) {
      if (ex.getCode() == ErrorCode.INVALID_ARTIFACT_SERVER || ex.getCode() == ErrorCode.INVALID_CLOUD_PROVIDER) {
        errMsg = ExceptionUtils.getMessage(ex);
      } else {
        // Unknown error. Most likely a delegate error - ignore and mark result as valid.
        valid = true;
      }
    } catch (Exception ex) {
      // Unknown error. Most likely a delegate error - ignore and mark result as valid.
      valid = true;
    }

    if (valid) {
      if (isNotBlank(settingAttribute.getConnectivityError())) {
        // Only update if previously there was a connectivity error.
        settingsService.update(settingAttribute, true);
      }
      return;
    }

    if (isBlank(errMsg)) {
      errMsg = "Error occurred when trying to validate the setting attribute";
    }

    if (errMsg.equals(settingAttribute.getConnectivityError())) {
      // Only update if previously there was no error or a different error.
      return;
    }

    settingAttribute.setConnectivityError(errMsg);
    settingsService.update(settingAttribute, false);
  }
}
