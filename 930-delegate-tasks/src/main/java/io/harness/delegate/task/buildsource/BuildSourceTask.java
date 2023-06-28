/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.buildsource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.DELEGATE;

import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.expression.RegexFunctor;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.delegatetasks.buildsource.BuildCollectParameters;
import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.delegatetasks.buildsource.BuildSourceParameters.BuildSourceRequestType;
import software.wings.delegatetasks.buildsource.BuildSourceResponse;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.ServiceClassLocator;
import software.wings.service.intfc.BuildService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class BuildSourceTask extends AbstractDelegateRunnableTask {
  @Inject private Map<Class<? extends SettingValue>, Class<? extends BuildService>> buildServiceMap;
  @Inject private ServiceClassLocator serviceLocator;
  @Inject private Injector injector;
  @Inject private EncryptionService encryptionService;

  @Inject private TimeLimiter timeLimiter;

  public BuildSourceTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  private BuildSourceExecutionResponse runInternal(BuildSourceParameters buildSourceRequest) {
    int limit = buildSourceRequest.getLimit();
    String artifactStreamType = buildSourceRequest.getArtifactStreamType();
    SettingValue settingValue = buildSourceRequest.getSettingValue();
    BuildService service = getBuildService(artifactStreamType);
    ArtifactStreamAttributes artifactStreamAttributes = buildSourceRequest.getArtifactStreamAttributes();
    artifactStreamAttributes.setCollection(buildSourceRequest.isCollection());
    if (isNotEmpty(buildSourceRequest.getSavedBuildDetailsKeys())) {
      artifactStreamAttributes.setSavedBuildDetailsKeys(buildSourceRequest.getSavedBuildDetailsKeys());
    }
    String appId = buildSourceRequest.getAppId();
    List<EncryptedDataDetail> encryptedDataDetails = buildSourceRequest.getEncryptedDataDetails();
    BuildSourceRequestType buildSourceRequestType = buildSourceRequest.getBuildSourceRequestType();

    List<BuildDetails> buildDetails = new ArrayList<>();

    // if artifact collection is working fine with cache credentials then we continue fetching from
    if (settingValue instanceof EncryptableSetting) {
      if (buildSourceRequest.isShouldFetchSecretFromCache()) {
        encryptionService.decrypt((EncryptableSetting) settingValue, encryptedDataDetails, true);
      } else {
        encryptionService.decrypt((EncryptableSetting) settingValue, encryptedDataDetails, false);
      }
    }

    switch (buildSourceRequestType) {
      case GET_BUILDS:
        buildDetails = getBuilds(
            limit, artifactStreamType, settingValue, service, artifactStreamAttributes, appId, encryptedDataDetails);
        break;
      case GET_LAST_SUCCESSFUL_BUILD:
        BuildDetails lastSuccessfulBuild =
            service.getLastSuccessfulBuild(appId, artifactStreamAttributes, settingValue, encryptedDataDetails);
        if (lastSuccessfulBuild != null) {
          buildDetails.add(lastSuccessfulBuild);
        }
        break;
      case GET_BUILD:
        BuildCollectParameters buildCollectParameters = buildSourceRequest.getBuildCollectParameters();
        if (buildCollectParameters == null) {
          throw new InvalidRequestException("Build collection parameters not set correctly");
        }
        BuildDetails buildDetail = getBuild(buildCollectParameters, limit, artifactStreamType, settingValue, service,
            artifactStreamAttributes, appId, encryptedDataDetails);
        if (buildDetail != null) {
          buildDetails.add(buildDetail);
        } else {
          throw new InvalidRequestException(
              String.format("Could not find requested build number %s for artifact stream type %s",
                  buildCollectParameters.getBuildNo(), artifactStreamType));
        }
        break;
      default:
        throw new InvalidRequestException(
            String.format("Unsupported build source request type: %s", buildSourceRequestType));
    }

    // NOTE: Here BuildSourceExecutionResponse::buildSourceResponse::stable is marked always true. When artifact
    // streams are capable of pagination we'll need to get that from the getBuilds function above.
    return BuildSourceExecutionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .buildSourceResponse(BuildSourceResponse.builder().buildDetails(buildDetails).stable(true).build())
        .build();
  }

  @Override
  public BuildSourceExecutionResponse run(TaskParameters parameters) {
    try {
      BuildSourceParameters buildSourceRequest = (BuildSourceParameters) parameters;
      if (buildSourceRequest.isTimeoutSupported()) {
        return HTimeLimiter.callInterruptible(
            timeLimiter, Duration.ofMillis(buildSourceRequest.getTimeout()), () -> runInternal(buildSourceRequest));
      } else {
        return runInternal(buildSourceRequest);
      }
    } catch (TimeoutException te) {
      ExceptionLogger.logProcessedMessages(te, DELEGATE, log);
      return BuildSourceExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(te))
          .isTimeoutError(true)
          .build();
    } catch (WingsException ex) {
      ExceptionLogger.logProcessedMessages(ex, DELEGATE, log);
      return BuildSourceExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(ex))
          .build();
    } catch (Exception ex) {
      log.error("Exception in processing BuildSource task [%s]", ex);
      return BuildSourceExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(ex))
          .build();
    }
  }

  private BuildDetails getBuild(BuildCollectParameters buildCollectParameters, int limit, String artifactStreamType,
      SettingValue settingValue, BuildService service, ArtifactStreamAttributes artifactStreamAttributes, String appId,
      List<EncryptedDataDetail> encryptedDataDetails) {
    BuildDetails buildDetail = null;
    boolean isRegex = buildCollectParameters.isRegex();
    String buildNo = buildCollectParameters.getBuildNo();
    if (isEmpty(buildNo)) {
      List<BuildDetails> allBuilds = getBuilds(
          limit, artifactStreamType, settingValue, service, artifactStreamAttributes, appId, encryptedDataDetails);
      if (isNotEmpty(allBuilds) && allBuilds.size() > 0) {
        buildDetail = allBuilds.get(allBuilds.size() - 1);
      }
    } else {
      try {
        buildDetail = service.getBuild(appId, artifactStreamAttributes, settingValue, encryptedDataDetails,
            buildCollectParameters.getBuildNo(), buildCollectParameters.isRegex());
      } catch (UnsupportedOperationException e) {
        log.warn("getBuild not implemented for {}. Getting all builds instead.", artifactStreamType);
        List<BuildDetails> allBuilds = getBuilds(
            limit, artifactStreamType, settingValue, service, artifactStreamAttributes, appId, encryptedDataDetails);
        if (isNotEmpty(allBuilds)) {
          Optional<BuildDetails> build = allBuilds.stream()
                                             .filter(b -> {
                                               if (isRegex) {
                                                 return new RegexFunctor().match(buildNo, b.getNumber());
                                               }
                                               return buildNo.equals(b.getNumber());
                                             })
                                             .findFirst();
          if (build.isPresent()) {
            buildDetail = build.get();
          }
        }
      }
    }
    return buildDetail;
  }

  private List<BuildDetails> getBuilds(int limit, String artifactStreamType, SettingValue settingValue,
      BuildService service, ArtifactStreamAttributes artifactStreamAttributes, String appId,
      List<EncryptedDataDetail> encryptedDataDetails) {
    List<BuildDetails> buildDetails;
    if (ArtifactStreamType.CUSTOM.name().equals(artifactStreamType)) {
      buildDetails = service.getBuilds(artifactStreamAttributes);
    } else {
      boolean enforceLimitOnResults =
          limit != -1 && ARTIFACTORY.name().equals(artifactStreamType); // TODO: supported for Artifactory only
      buildDetails = enforceLimitOnResults
          ? service.getBuilds(appId, artifactStreamAttributes, settingValue, encryptedDataDetails, limit)
          : service.getBuilds(appId, artifactStreamAttributes, settingValue, encryptedDataDetails);
    }
    return buildDetails;
  }

  private BuildService getBuildService(String artifactStreamType) {
    Class<? extends BuildService> buildServiceClass = serviceLocator.getBuildServiceClass(artifactStreamType);
    return injector.getInstance(Key.get(buildServiceClass));
  }
}
