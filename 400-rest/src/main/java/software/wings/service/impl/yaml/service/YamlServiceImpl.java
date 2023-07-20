/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.USER;
import static io.harness.pcf.model.PcfConstants.PCF_CONFIG_FILE_EXTENSION;
import static io.harness.threading.Morpheus.quietSleep;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.yaml.YamlConstants.APP_SETTINGS_FILE;
import static software.wings.beans.yaml.YamlConstants.CONN_STRINGS_FILE;
import static software.wings.beans.yaml.YamlConstants.ENVIRONMENTS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.GIT_YAML_LOG_PREFIX;
import static software.wings.beans.yaml.YamlConstants.INDEX_YAML;
import static software.wings.beans.yaml.YamlConstants.KUSTOMIZE_PATCHES_FILE;
import static software.wings.beans.yaml.YamlConstants.OC_PARAMS_FILE;
import static software.wings.beans.yaml.YamlConstants.SETUP_FOLDER_PATH;
import static software.wings.beans.yaml.YamlConstants.VALUES_YAML_KEY;
import static software.wings.beans.yaml.YamlConstants.YAML_EXTENSION;
import static software.wings.beans.yaml.YamlType.ACCOUNT_DEFAULTS;
import static software.wings.beans.yaml.YamlType.APPLICATION;
import static software.wings.beans.yaml.YamlType.APPLICATION_DEFAULTS;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_APP_SERVICE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_APP_SETTINGS_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_APP_SETTINGS_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_CONN_STRINGS_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_CONN_STRINGS_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_HELM_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_HELM_OVERRIDES_ALL_SERVICE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_KUSTOMIZE_PATCHES_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_KUSTOMIZE_PATCHES_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_KUSTOMIZE_PATCHES_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_OC_PARAMS_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_OC_PARAMS_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_OC_PARAMS_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_PCF_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_PCF_OVERRIDES_ALL_SERVICE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_VALUES_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_VALUES_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_MANIFEST_VALUES_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.APPLICATION_TEMPLATE_LIBRARY;
import static software.wings.beans.yaml.YamlType.ARTIFACT_SERVER;
import static software.wings.beans.yaml.YamlType.ARTIFACT_SERVER_ARTIFACT_STREAM_OVERRIDE;
import static software.wings.beans.yaml.YamlType.ARTIFACT_SERVER_OVERRIDE;
import static software.wings.beans.yaml.YamlType.ARTIFACT_STREAM;
import static software.wings.beans.yaml.YamlType.CLOUD_PROVIDER;
import static software.wings.beans.yaml.YamlType.CLOUD_PROVIDER_ARTIFACT_STREAM_OVERRIDE;
import static software.wings.beans.yaml.YamlType.CLOUD_PROVIDER_OVERRIDE;
import static software.wings.beans.yaml.YamlType.COLLABORATION_PROVIDER;
import static software.wings.beans.yaml.YamlType.COMMAND;
import static software.wings.beans.yaml.YamlType.CONFIG_FILE;
import static software.wings.beans.yaml.YamlType.CONFIG_FILE_CONTENT;
import static software.wings.beans.yaml.YamlType.CONFIG_FILE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.CONFIG_FILE_OVERRIDE_CONTENT;
import static software.wings.beans.yaml.YamlType.CV_CONFIGURATION;
import static software.wings.beans.yaml.YamlType.DEPLOYMENT_SPECIFICATION;
import static software.wings.beans.yaml.YamlType.ENVIRONMENT;
import static software.wings.beans.yaml.YamlType.EVENT_RULE;
import static software.wings.beans.yaml.YamlType.GLOBAL_TEMPLATE_LIBRARY;
import static software.wings.beans.yaml.YamlType.INFRA_DEFINITION;
import static software.wings.beans.yaml.YamlType.INFRA_MAPPING;
import static software.wings.beans.yaml.YamlType.LOADBALANCER_PROVIDER;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_APP_SERVICE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_APP_SETTINGS_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_APP_SETTINGS_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_CONN_STRINGS_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_CONN_STRINGS_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_KUSTOMIZE_PATCHES_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_KUSTOMIZE_PATCHES_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_KUSTOMIZE_PATCHES_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_PCF_OVERRIDE_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_PCF_OVERRIDE_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_VALUES_ENV_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_VALUES_ENV_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.MANIFEST_FILE_VALUES_SERVICE_OVERRIDE;
import static software.wings.beans.yaml.YamlType.NOTIFICATION_GROUP;
import static software.wings.beans.yaml.YamlType.PIPELINE;
import static software.wings.beans.yaml.YamlType.PROVISIONER;
import static software.wings.beans.yaml.YamlType.SERVICE;
import static software.wings.beans.yaml.YamlType.SOURCE_REPO_PROVIDER;
import static software.wings.beans.yaml.YamlType.TAG;
import static software.wings.beans.yaml.YamlType.TRIGGER;
import static software.wings.beans.yaml.YamlType.VERIFICATION_PROVIDER;
import static software.wings.beans.yaml.YamlType.WORKFLOW;
import static software.wings.security.UserThreadLocal.userGuard;

import static java.lang.String.format;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HarnessException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.YamlException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.ChangeType;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.rest.RestResponse;
import io.harness.rest.RestResponse.Builder;
import io.harness.yaml.BaseYaml;

import software.wings.audit.AuditHeader;
import software.wings.beans.Application;
import software.wings.beans.Base;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.EntityInformation;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlConstants;
import software.wings.beans.yaml.YamlType;
import software.wings.common.AuditHelper;
import software.wings.dl.WingsPersistence;
import software.wings.exception.InvalidYamlNameException;
import software.wings.exception.YamlProcessingException;
import software.wings.exception.YamlProcessingException.ChangeWithErrorMsg;
import software.wings.resources.yaml.YamlAuthHandler;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.yaml.YamlProcessingLogContext;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.tag.HarnessTagYamlHelper;
import software.wings.service.impl.yaml.util.YamlWorkflowValidator;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuditService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.service.intfc.yaml.YamlResourceService;
import software.wings.service.intfc.yaml.YamlSuccessfulChangeService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.service.intfc.yaml.sync.YamlService;
import software.wings.yaml.FileOperationStatus;
import software.wings.yaml.YamlOperationResponse;
import software.wings.yaml.YamlOperationResponse.YamlOperationResponseBuilder;
import software.wings.yaml.YamlPayload;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.scanner.ScannerException;

/**
 * @author rktummala on 10/16/17
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
public class YamlServiceImpl<Y extends BaseYaml, B extends Base> implements YamlService<Y, B> {
  /**
   * We need to evict UserPermissionCache and UserRestrictionCache for some yaml operations.
   * All the rbac*YamlTypes define the entity types in which we have to refresh the cache.
   */
  private static final Set<YamlType> rbacCreateYamlTypes = Sets.newHashSet(YamlType.APPLICATION, YamlType.SERVICE,
      YamlType.ENVIRONMENT, YamlType.PROVISIONER, YamlType.WORKFLOW, YamlType.PIPELINE);
  private static final Set<YamlType> rbacUpdateYamlTypes =
      Sets.newHashSet(YamlType.ENVIRONMENT, YamlType.WORKFLOW, YamlType.PIPELINE);
  private static final Set<YamlType> rbacDeleteYamlTypes = rbacCreateYamlTypes;

  private static final int YAML_MAX_PARALLEL_COUNT = 20;
  private static final String AMI_FILTERS = "amiFilters";
  private static final String PHASES = "phases";
  private static final String AMI_TAGS = "amiTags";
  private static final String NAME = "name";
  private static final String DEFAULT_YAML = "harnessApiVersion: '1.0'";

  @Inject private YamlHandlerFactory yamlHandlerFactory;

  @Inject private transient YamlGitService yamlGitService;
  @Inject private AuthService authService;
  @Inject private ExecutorService executorService;
  @Inject private TimeLimiter timeLimiter;
  @Inject private YamlResourceService yamlResourceService;
  @Inject private WorkflowService workflowService;
  @Inject private AppService appService;
  @Inject private FailedCommitStore failedCommitStore;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private YamlPushService yamlPushService;
  @Inject private HarnessTagYamlHelper harnessTagYamlHelper;
  @Inject private GitSyncService gitSyncService;
  @Inject private YamlHelper yamlHelper;
  @Inject YamlSuccessfulChangeService yamlSuccessfulChangeService;
  @Inject private AuditHelper auditHelper;
  @Inject private AuditService auditService;
  @Inject YamlAuthHandler yamlAuthHandler;
  private final List<YamlType> yamlProcessingOrder = getEntityProcessingOrder();

  private List<YamlType> getEntityProcessingOrder() {
    return Lists.newArrayList(SOURCE_REPO_PROVIDER, ACCOUNT_DEFAULTS, TAG, CLOUD_PROVIDER, CLOUD_PROVIDER_OVERRIDE,
        ARTIFACT_SERVER, ARTIFACT_SERVER_OVERRIDE, COLLABORATION_PROVIDER, LOADBALANCER_PROVIDER, VERIFICATION_PROVIDER,
        NOTIFICATION_GROUP, GLOBAL_TEMPLATE_LIBRARY, APPLICATION, APPLICATION_DEFAULTS, APPLICATION_TEMPLATE_LIBRARY,
        SERVICE, PROVISIONER, ARTIFACT_STREAM, ARTIFACT_SERVER_ARTIFACT_STREAM_OVERRIDE,
        CLOUD_PROVIDER_ARTIFACT_STREAM_OVERRIDE, COMMAND, DEPLOYMENT_SPECIFICATION, CONFIG_FILE_CONTENT, CONFIG_FILE,
        APPLICATION_MANIFEST, APPLICATION_MANIFEST_APP_SERVICE, MANIFEST_FILE, MANIFEST_FILE_APP_SERVICE,
        APPLICATION_MANIFEST_VALUES_SERVICE_OVERRIDE, APPLICATION_MANIFEST_KUSTOMIZE_PATCHES_SERVICE_OVERRIDE,
        APPLICATION_MANIFEST_OC_PARAMS_SERVICE_OVERRIDE, MANIFEST_FILE_VALUES_SERVICE_OVERRIDE,
        MANIFEST_FILE_KUSTOMIZE_PATCHES_SERVICE_OVERRIDE, ENVIRONMENT, INFRA_MAPPING, CV_CONFIGURATION,
        INFRA_DEFINITION, CONFIG_FILE_OVERRIDE_CONTENT, CONFIG_FILE_OVERRIDE, APPLICATION_MANIFEST_VALUES_ENV_OVERRIDE,
        APPLICATION_MANIFEST_KUSTOMIZE_PATCHES_ENV_OVERRIDE, APPLICATION_MANIFEST_VALUES_ENV_SERVICE_OVERRIDE,
        APPLICATION_MANIFEST_KUSTOMIZE_PATCHES_ENV_SERVICE_OVERRIDE, APPLICATION_MANIFEST_OC_PARAMS_ENV_OVERRIDE,
        APPLICATION_MANIFEST_OC_PARAMS_ENV_SERVICE_OVERRIDE, APPLICATION_MANIFEST_PCF_OVERRIDES_ALL_SERVICE,
        APPLICATION_MANIFEST_PCF_ENV_SERVICE_OVERRIDE, APPLICATION_MANIFEST_APP_SETTINGS_ENV_OVERRIDE,
        APPLICATION_MANIFEST_APP_SETTINGS_ENV_SERVICE_OVERRIDE, APPLICATION_MANIFEST_CONN_STRINGS_ENV_OVERRIDE,
        APPLICATION_MANIFEST_CONN_STRINGS_ENV_SERVICE_OVERRIDE, APPLICATION_MANIFEST_HELM_OVERRIDES_ALL_SERVICE,
        APPLICATION_MANIFEST_HELM_ENV_SERVICE_OVERRIDE, MANIFEST_FILE_VALUES_ENV_OVERRIDE,
        MANIFEST_FILE_KUSTOMIZE_PATCHES_ENV_OVERRIDE, MANIFEST_FILE_VALUES_ENV_SERVICE_OVERRIDE,
        MANIFEST_FILE_KUSTOMIZE_PATCHES_ENV_SERVICE_OVERRIDE, MANIFEST_FILE_PCF_OVERRIDE_ENV_OVERRIDE,
        MANIFEST_FILE_PCF_OVERRIDE_ENV_SERVICE_OVERRIDE, MANIFEST_FILE_APP_SETTINGS_ENV_OVERRIDE,
        MANIFEST_FILE_APP_SETTINGS_ENV_SERVICE_OVERRIDE, MANIFEST_FILE_CONN_STRINGS_ENV_OVERRIDE,
        MANIFEST_FILE_CONN_STRINGS_ENV_SERVICE_OVERRIDE, WORKFLOW, PIPELINE, TRIGGER, YamlType.GOVERNANCE_CONFIG,
        EVENT_RULE);
  }

  @Override
  public List<ChangeContext> processChangeSet(List<Change> changeList) throws YamlProcessingException {
    return processChangeSet(changeList, true);
  }

  @AllArgsConstructor
  public static class ChangeContextErrorMap {
    Map<String, ChangeWithErrorMsg> errorMsgMap;
    List<ChangeContext> changeContextList;
  }

  @Override
  public List<ChangeContext> processChangeSet(List<Change> changeList, boolean isGitSyncPath)
      throws YamlProcessingException {
    // e.g. remove files outside of setup folder. (checking filePath)
    changeList = filterInvalidFilePaths(changeList);

    // compute the order of processing
    sortByProcessingOrder(changeList);

    // validate
    ChangeContextErrorMap validationResponseMap = validate(changeList);

    // sorting it again
    sortByProcessingOrder(validationResponseMap);
    // process in the given order
    Map<String, ChangeWithErrorMsg> processingErrorMap =
        process(validationResponseMap.changeContextList, isGitSyncPath);

    Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap =
        ImmutableMap.<String, ChangeWithErrorMsg>builder()
            .putAll(emptyIfNull(processingErrorMap))
            .putAll(emptyIfNull(validationResponseMap.errorMsgMap))
            .build();
    ensureNoError(failedYamlFileChangeMap, validationResponseMap.changeContextList, changeList);

    log.info(GIT_YAML_LOG_PREFIX + "Processed all the changes from GIT without any error.");
    return validationResponseMap.changeContextList;
  }

  private void ensureNoError(Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap,
      List<ChangeContext> changeContextList, List<Change> changeList) throws YamlProcessingException {
    if (!failedYamlFileChangeMap.isEmpty()) {
      throw new YamlProcessingException("Error while processing some yaml files in the changeset.",
          failedYamlFileChangeMap, changeContextList, changeList);
    }
  }

  @VisibleForTesting
  List<Change> filterInvalidFilePaths(List<Change> changeList) {
    return changeList.stream().filter(change -> change.getFilePath().startsWith(SETUP_FOLDER_PATH)).collect(toList());
  }

  @Override
  public RestResponse<B> update(YamlPayload yamlPayload, String accountId, String entityId) {
    GitFileChange change = GitFileChange.Builder.aGitFileChange()
                               .withChangeType(ChangeType.MODIFY)
                               .withFileContent(yamlPayload.getYaml())
                               .withFilePath(yamlPayload.getPath())
                               .withAccountId(accountId)
                               .withEntityId(entityId)
                               .build();
    RestResponse rr = new RestResponse<>();
    List<GitFileChange> gitFileChangeList = asList(change);

    try {
      List<ChangeContext> changeContextList = processChangeSet(asList(change));
      notNullCheck("Change Context List is null", changeContextList);
      boolean empty = isEmpty(changeContextList);
      if (!empty) {
        // We only sent one

        ChangeContext changeContext = changeContextList.get(0);
        Object base = changeContext.getYamlSyncHandler().get(
            changeContext.getChange().getAccountId(), changeContext.getChange().getFilePath(), changeContext);
        rr.setResource(base);
        yamlGitService.removeGitSyncErrors(accountId, gitFileChangeList, false);
      } else {
        throw new WingsException(ErrorCode.GENERAL_YAML_ERROR, USER)
            .addParam("message", "Update failed. Reason: " + yamlPayload.getName());
      }

      return rr;
    } catch (YamlProcessingException ex) {
      Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap = ex.getFailedYamlFileChangeMap();
      String errorMsg;
      if (isNotEmpty(failedYamlFileChangeMap)) {
        ChangeWithErrorMsg changeWithErrorMsg = failedYamlFileChangeMap.get(change.getFilePath());
        if (changeWithErrorMsg != null) {
          errorMsg = changeWithErrorMsg.getErrorMsg();
        } else {
          errorMsg = "Internal error";
        }
      } else {
        errorMsg = "Internal error";
      }

      throw new YamlException("Update failed. Reason: " + errorMsg, ex, USER);
    }
  }

  @Override
  public RestResponse processYamlFilesAsZip(String accountId, InputStream fileInputStream, String yamlPath) {
    try {
      Future<RestResponse> future =
          Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("process-yamls-as-zip").build())
              .submit(() -> {
                try {
                  List changeList = getChangesForZipFile(accountId, fileInputStream, yamlPath);

                  List<ChangeContext> changeSets = processChangeSet(changeList);
                  Map<String, Object> metaDataMap = new HashMap<>();
                  metaDataMap.put("yamlFilesProcessed", changeSets.size());
                  return Builder.aRestResponse().withMetaData(metaDataMap).build();
                } catch (YamlProcessingException ex) {
                  log.warn("Unable to process zip upload for account {}.", accountId, ex);
                  // gitToHarness is false, as this is not initiated from git
                  yamlGitService.processFailedChanges(accountId, ex.getFailedYamlFileChangeMap(), false);
                }
                return Builder.aRestResponse()
                    .withResponseMessages(asList(ResponseMessage.builder().code(ErrorCode.DEFAULT_ERROR_CODE).build()))
                    .build();
              });
      return future.get(30, TimeUnit.SECONDS);
    } catch (Exception e) {
      return Builder.aRestResponse()
          .withResponseMessages(asList(ResponseMessage.builder().code(ErrorCode.DEFAULT_ERROR_CODE).build()))
          .build();
    }
  }

  @Override
  public void syncYamlTemplate(String accountId) {
    executorService.submit(() -> {
      try {
        syncYamlForTemplates(accountId);
      } catch (WingsException ex) {
        ExceptionLogger.logProcessedMessages(ex, MANAGER, log);
      } catch (Exception e) {
        log.error("Exception while performing template sync for account {}", accountId, e);
      }
    });
  }

  public String obtainAppIdFromGitFileChange(String accountId, String yamlFilePath) {
    String appId = GLOBAL_APP_ID;

    // Fetch appName from yamlPath, e.g. Setup/Applications/App1/Services/S1/index.yaml -> App1,
    // Setup/Artifact Servers/server.yaml -> null
    String appName = yamlHelper.getAppName(yamlFilePath);
    if (StringUtils.isNotBlank(appName)) {
      Application app = appService.getAppByName(accountId, appName);
      if (app != null) {
        appId = app.getUuid();
      }
    }

    return appId;
  }

  private void syncYamlForTemplates(String accountId) {
    List<String> appIdsList = appService.getAppIdsByAccountId(accountId);
    appIdsList.add(GLOBAL_APP_ID);
    appIdsList.forEach(appId -> {
      try {
        log.info(GIT_YAML_LOG_PREFIX + "Pushing templates for app {}", appId);
        yamlGitService.syncForTemplates(accountId, appId);
        log.info(GIT_YAML_LOG_PREFIX + "Pushed templates for app {}", appId);
      } catch (Exception ex) {
        log.error(GIT_YAML_LOG_PREFIX + "Failed to push templates for app {}", appId, ex);
      }
    });
    log.info(GIT_YAML_LOG_PREFIX + "Completed pushing templates for account {}", accountId);
  }

  protected List<GitFileChange> getChangesForZipFile(String accountId, InputStream fileInputStream, String yamlPath)
      throws IOException {
    List<GitFileChange> changeList = Lists.newArrayList();
    File tempFile = File.createTempFile(accountId + "_" + System.currentTimeMillis() + "_yaml", ".tmp");
    ZipFile zipFile = null;
    try {
      OutputStream outputStream = new FileOutputStream(tempFile);
      IOUtils.copy(fileInputStream, outputStream);
      outputStream.close();

      zipFile = new ZipFile(tempFile.getAbsoluteFile());

      Enumeration<? extends ZipEntry> entries = zipFile.entries();

      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        File currFile = new File(entry.getName());
        try {
          if (!currFile.isHidden() && !entry.isDirectory()
              && (entry.getName().endsWith(YAML_EXTENSION)
                  || entry.getName().contains(YamlConstants.CONFIG_FILES_FOLDER))) {
            InputStream stream = zipFile.getInputStream(entry);
            StringWriter writer = new StringWriter();
            IOUtils.copy(stream, writer, "UTF-8");
            GitFileChange change =
                GitFileChange.Builder.aGitFileChange()
                    .withAccountId(accountId)
                    .withChangeType(ChangeType.ADD)
                    .withFileContent(writer.toString())
                    .withFilePath((yamlPath != null ? yamlPath + File.separatorChar : "") + entry.getName())
                    .build();
            changeList.add(change);
          }
        } finally {
          FileUtils.deleteQuietly(currFile);
        }
      }
    } finally {
      if (zipFile != null) {
        zipFile.close();
      }
      FileUtils.deleteQuietly(tempFile);
    }
    return changeList;
  }

  /**
   * @param changeList
   */
  @Override
  public void sortByProcessingOrder(List<Change> changeList) {
    changeList.sort(new FilePathComparator());
  }

  @VisibleForTesting
  void sortByProcessingOrder(ChangeContextErrorMap validationResponseMap) {
    List<ChangeContext> changeContextList = validationResponseMap.changeContextList;
    changeContextList.sort(new ChangeContextComparator());
  }

  private ChangeContext validateManifestFile(String yamlFilePath, Change change) {
    ChangeContext.Builder changeContextBuilder = ChangeContext.Builder.aChangeContext().withChange(change);

    if (yamlFilePath.contains(
            YamlConstants.MANIFEST_FOLDER + YamlConstants.PATH_DELIMITER + YamlConstants.MANIFEST_FILE_FOLDER)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE));
      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.MANIFEST_FOLDER_APP_SERVICE + YamlConstants.PATH_DELIMITER
                   + YamlConstants.MANIFEST_FILE_FOLDER)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_APP_SERVICE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_APP_SERVICE));
      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.VALUES_FOLDER + YamlConstants.PATH_DELIMITER + VALUES_YAML_KEY)
        && !yamlFilePath.contains(ENVIRONMENTS_FOLDER)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_VALUES_SERVICE_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_VALUES_SERVICE_OVERRIDE));
      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.VALUES_FOLDER + YamlConstants.PATH_DELIMITER + VALUES_YAML_KEY)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_VALUES_ENV_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_VALUES_ENV_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(
                   YamlConstants.VALUES_FOLDER + YamlConstants.PATH_DELIMITER + YamlConstants.SERVICES_FOLDER)
        && yamlFilePath.contains(VALUES_YAML_KEY)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_VALUES_ENV_SERVICE_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_VALUES_ENV_SERVICE_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.OC_PARAMS_FOLDER + YamlConstants.PATH_DELIMITER + OC_PARAMS_FILE)
        && !yamlFilePath.contains(ENVIRONMENTS_FOLDER)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_OC_PARAMS_SERVICE_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_OC_PARAMS_SERVICE_OVERRIDE));
      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.OC_PARAMS_FOLDER + YamlConstants.PATH_DELIMITER + OC_PARAMS_FILE)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_OC_PARAMS_ENV_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_OC_PARAMS_ENV_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(
                   YamlConstants.OC_PARAMS_FOLDER + YamlConstants.PATH_DELIMITER + YamlConstants.SERVICES_FOLDER)
        && yamlFilePath.contains(OC_PARAMS_FILE)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_OC_PARAMS_ENV_SERVICE_OVERRIDE)
          .withYamlSyncHandler(
              yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_OC_PARAMS_ENV_SERVICE_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(
                   YamlConstants.KUSTOMIZE_PATCHES_FOLDER + YamlConstants.PATH_DELIMITER + KUSTOMIZE_PATCHES_FILE)
        && !yamlFilePath.contains(ENVIRONMENTS_FOLDER)) {
      changeContextBuilder.withYamlType(MANIFEST_FILE_KUSTOMIZE_PATCHES_SERVICE_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(MANIFEST_FILE_KUSTOMIZE_PATCHES_SERVICE_OVERRIDE));
      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(
                   YamlConstants.KUSTOMIZE_PATCHES_FOLDER + YamlConstants.PATH_DELIMITER + KUSTOMIZE_PATCHES_FILE)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_KUSTOMIZE_PATCHES_ENV_OVERRIDE)
          .withYamlSyncHandler(
              yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_KUSTOMIZE_PATCHES_ENV_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.KUSTOMIZE_PATCHES_FOLDER + YamlConstants.PATH_DELIMITER
                   + YamlConstants.SERVICES_FOLDER)
        && yamlFilePath.contains(KUSTOMIZE_PATCHES_FILE)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_KUSTOMIZE_PATCHES_ENV_SERVICE_OVERRIDE)
          .withYamlSyncHandler(
              yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_KUSTOMIZE_PATCHES_ENV_SERVICE_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(
                   YamlConstants.PCF_OVERRIDES_FOLDER + YamlConstants.PATH_DELIMITER + YamlConstants.SERVICES_FOLDER)
        && yamlFilePath.endsWith(PCF_CONFIG_FILE_EXTENSION)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_PCF_OVERRIDE_ENV_SERVICE_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_VALUES_ENV_SERVICE_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.PCF_OVERRIDES_FOLDER + YamlConstants.PATH_DELIMITER)
        && yamlFilePath.endsWith(PCF_CONFIG_FILE_EXTENSION)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_PCF_OVERRIDE_ENV_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_VALUES_ENV_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.AZURE_APP_SETTINGS_OVERRIDES_FOLDER + YamlConstants.PATH_DELIMITER
                   + APP_SETTINGS_FILE)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_APP_SETTINGS_ENV_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_APP_SETTINGS_ENV_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.AZURE_APP_SETTINGS_OVERRIDES_FOLDER + YamlConstants.PATH_DELIMITER
                   + YamlConstants.SERVICES_FOLDER)
        && yamlFilePath.contains(APP_SETTINGS_FILE)) {
      changeContextBuilder.withYamlType(YamlType.MANIFEST_FILE_APP_SETTINGS_ENV_SERVICE_OVERRIDE)
          .withYamlSyncHandler(
              yamlHandlerFactory.getYamlHandler(YamlType.MANIFEST_FILE_APP_SETTINGS_ENV_SERVICE_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.AZURE_CONN_STRINGS_OVERRIDES_FOLDER + YamlConstants.PATH_DELIMITER
                   + CONN_STRINGS_FILE)) {
      changeContextBuilder.withYamlType(MANIFEST_FILE_CONN_STRINGS_ENV_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(MANIFEST_FILE_CONN_STRINGS_ENV_OVERRIDE));

      return changeContextBuilder.build();
    } else if (yamlFilePath.contains(YamlConstants.AZURE_CONN_STRINGS_OVERRIDES_FOLDER + YamlConstants.PATH_DELIMITER
                   + YamlConstants.SERVICES_FOLDER)
        && yamlFilePath.contains(CONN_STRINGS_FILE)) {
      changeContextBuilder.withYamlType(MANIFEST_FILE_CONN_STRINGS_ENV_SERVICE_OVERRIDE)
          .withYamlSyncHandler(yamlHandlerFactory.getYamlHandler(MANIFEST_FILE_CONN_STRINGS_ENV_SERVICE_OVERRIDE));

      return changeContextBuilder.build();
    }

    return null;
  }

  private <T extends BaseYamlHandler> ChangeContextErrorMap validate(List<Change> changeList) {
    log.info(GIT_YAML_LOG_PREFIX + "Validating changeset");
    List<ChangeContext> changeContextList = Lists.newArrayList();
    Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap = Maps.newConcurrentMap();

    for (Change change : changeList) {
      String yamlFilePath = change.getFilePath();

      try {
        ChangeContext manifestFileChangeContext = validateManifestFile(yamlFilePath, change);

        validateServiceInPath(yamlFilePath, change);
        if (manifestFileChangeContext != null) {
          changeContextList.add(manifestFileChangeContext);
        } else if (yamlFilePath.endsWith(YAML_EXTENSION)) {
          validateYaml(change.getFileContent());
          YamlType yamlType = findYamlType(yamlFilePath);
          String yamlSubType = getYamlSubType(change.getFileContent());

          if (INFRA_MAPPING == yamlType) {
            continue;
          }

          if (!isProcessingAllowed(change, yamlType)) {
            log.warn("Skipping [{}], because processing is disabled for yamlType [{}]", change.getFilePath(), yamlType);
            continue;
          }

          T yamlSyncHandler = yamlHandlerFactory.getYamlHandler(yamlType, yamlSubType);
          Class yamlClass = yamlSyncHandler.getYamlClass();
          BaseYaml yaml = getYaml(change.getFileContent(), yamlClass, change.getAccountId());
          notNullCheck("Could not get yaml object for :" + yamlFilePath, yaml);

          ChangeContext.Builder changeContextBuilder = ChangeContext.Builder.aChangeContext()
                                                           .withChange(change)
                                                           .withYaml(yaml)
                                                           .withYamlType(yamlType)
                                                           .withYamlSyncHandler(yamlSyncHandler);
          ChangeContext changeContext = changeContextBuilder.build();
          changeContextList.add(changeContext);
        } else if (yamlFilePath.contains(YamlConstants.CONFIG_FILES_FOLDER)) {
          // Special handling for config files
          YamlType yamlType = findYamlType(yamlFilePath);
          if (YamlType.CONFIG_FILE_CONTENT == yamlType || YamlType.CONFIG_FILE_OVERRIDE_CONTENT == yamlType) {
            ChangeContext.Builder changeContextBuilder =
                ChangeContext.Builder.aChangeContext().withChange(change).withYamlType(yamlType);
            changeContextList.add(changeContextBuilder.build());
          } else {
            addToFailedYamlMap(failedYamlFileChangeMap, change, "Unsupported type: " + yamlType);
          }
        }
      } catch (ScannerException ex) {
        String message;
        Mark contextMark = ex.getContextMark();
        if (contextMark != null) {
          String snippet = contextMark.get_snippet();
          if (snippet != null) {
            message = "Not a well-formed yaml. The field " + snippet + " in line " + contextMark.getLine()
                + " doesn't end with :";
          } else {
            message = ExceptionUtils.getMessage(ex);
          }
        } else {
          message = ExceptionUtils.getMessage(ex);
        }
        log.warn(message, ex);
        addToFailedYamlMap(failedYamlFileChangeMap, change, message);
      } catch (UnrecognizedPropertyException ex) {
        String propertyName = ex.getPropertyName();
        if (propertyName != null) {
          String error = "Unrecognized field: " + propertyName;
          log.warn(error, ex);
          addToFailedYamlMap(failedYamlFileChangeMap, change, error);
        } else {
          log.warn("Unable to load yaml from string for file: " + yamlFilePath, ex);
          addToFailedYamlMap(failedYamlFileChangeMap, change, ExceptionUtils.getMessage(ex));
        }
      } catch (InvalidYamlNameException ex) {
        log.warn("Not a well formed yaml. Wrong field format", ex);
        addToFailedYamlMap(failedYamlFileChangeMap, change, ExceptionUtils.getMessage(ex));
      } catch (Exception ex) {
        log.warn("Unable to load yaml from string for file: " + yamlFilePath, ex);
        addToFailedYamlMap(failedYamlFileChangeMap, change, ExceptionUtils.getMessage(ex));
      }
    }

    if (failedYamlFileChangeMap.size() > 0) {
      log.error(
          GIT_YAML_LOG_PREFIX + "Error while validating some yaml files in the changeset", failedYamlFileChangeMap);
    }

    log.info(GIT_YAML_LOG_PREFIX + "Validated changeset");
    return new ChangeContextErrorMap(failedYamlFileChangeMap, changeContextList);
  }

  @VisibleForTesting
  boolean isProcessingAllowed(Change change, YamlType yamlType) {
    // If any yaml is to go behind feature flag it can be added here.
    return true;
  }

  @VisibleForTesting
  protected void validateServiceInPath(String yamlFilePath, Change change) {
    if (featureFlagService.isEnabled(FeatureName.VALIDATE_SERVICE_NAME_IN_FILE_PATH, change.getAccountId())) {
      if (yamlFilePath.contains(
              YamlConstants.VALUES_FOLDER + YamlConstants.PATH_DELIMITER + YamlConstants.SERVICES_FOLDER)
          && (yamlFilePath.contains(VALUES_YAML_KEY) || yamlFilePath.contains(INDEX_YAML))) {
        String appName = yamlHelper.getAppName(yamlFilePath);
        if (isNotEmpty(appName)) {
          Application app = appService.getAppByName(change.getAccountId(), appName);
          String serviceName = yamlHelper.getServiceNameForFileOverride(yamlFilePath);
          if (isNotEmpty(serviceName)) {
            Service svc = yamlHelper.getServiceByName(app.getAppId(), serviceName);
            if (svc == null) {
              throw new YamlException(String.format("Service with name %s not found in app %s.", serviceName, appName));
            }
          }
        }
      }
    }
  }
  private void addToFailedYamlMap(
      Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap, Change change, String errorMsg) {
    ChangeWithErrorMsg changeWithErrorMsg = ChangeWithErrorMsg.builder().change(change).errorMsg(errorMsg).build();
    failedYamlFileChangeMap.put(change.getFilePath(), changeWithErrorMsg);
  }

  /**
   * To find the yaml sub type, we need to look at the type field in the yaml payload
   * @param fileContent
   * @return
   */
  private String getYamlSubType(String fileContent) throws IOException {
    YamlReader reader = new YamlReader(fileContent);
    Object object = reader.read();
    Map map = (Map) object;
    return (String) map.get("type");
  }

  private Map<String, ChangeWithErrorMsg> process(List<ChangeContext> changeContextList, boolean gitSyncPath) {
    if (isEmpty(changeContextList)) {
      log.info("No changes to process in the change set");
      return null;
    }

    String accountId = changeContextList.get(0).getChange().getAccountId();
    Queue<Future> futures = new ConcurrentLinkedQueue<>();

    Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap = Maps.newConcurrentMap();
    YamlType previousYamlType = null;
    ChangeType previousChangeType = null;
    int numOfParallelChanges = 0;

    for (ChangeContext changeContext : changeContextList) {
      String yamlFilePath = changeContext.getChange().getFilePath();
      YamlType yamlType = changeContext.getYamlType();
      ChangeType changeType = changeContext.getChange().getChangeType();
      if (previousYamlType == null) {
        previousYamlType = yamlType;
      }

      if (previousChangeType == null) {
        previousChangeType = changeContext.getChange().getChangeType();
      }

      if (previousYamlType != yamlType || (++numOfParallelChanges == YAML_MAX_PARALLEL_COUNT)) {
        checkFuturesAndEvictCache(futures, previousYamlType, accountId, previousChangeType);
        previousYamlType = yamlType;
        previousChangeType = changeType;
        numOfParallelChanges = 0;
      }
      User user = UserThreadLocal.get();
      futures.add(executorService.submit(() -> {
        try (UserThreadLocal.Guard guard = userGuard(user)) {
          log.info("Processing file: [{}]", changeContext.getChange().getFilePath());
          processYamlChange(changeContext, changeContextList);
          if (gitSyncPath) {
            yamlGitService.discardGitSyncErrorForFilePath(changeContext.getChange().getAccountId(), yamlFilePath);
          }
          // mark file as successfully processed with status SUCCESS
          gitSyncService.onGitFileProcessingSuccess(changeContext.getChange(), accountId);
          log.info("Processing done for file [{}]", changeContext.getChange().getFilePath());
        } catch (Exception ex) {
          // mark file as failed with status FAILEDonGit
          log.warn("Exception while processing yaml file {}", yamlFilePath, ex);
          ChangeWithErrorMsg changeWithErrorMsg = ChangeWithErrorMsg.builder()
                                                      .change(changeContext.getChange())
                                                      .errorMsg(ExceptionUtils.getMessage(ex))
                                                      .build();
          // We continue processing the yaml files we understand, the failures are reported at the end
          failedYamlFileChangeMap.put(changeContext.getChange().getFilePath(), changeWithErrorMsg);
        }
      }));
    }

    checkFuturesAndEvictCache(futures, previousYamlType, accountId, previousChangeType);

    if (failedYamlFileChangeMap.size() > 0) {
      logAllErrorsWhileYamlInjestion(failedYamlFileChangeMap);
      log.error(
          GIT_YAML_LOG_PREFIX + "Error while processing some yaml files in the changeset", failedYamlFileChangeMap);
    }
    return failedYamlFileChangeMap;
  }

  private void logAllErrorsWhileYamlInjestion(Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap) {
    StringBuilder builder =
        new StringBuilder(128).append(GIT_YAML_LOG_PREFIX).append("Found Following Errors in Yaml Injestion");
    if (EmptyPredicate.isNotEmpty(failedYamlFileChangeMap)) {
      for (Map.Entry<String, ChangeWithErrorMsg> entry : failedYamlFileChangeMap.entrySet()) {
        builder.append("\nFileName: ")
            .append(entry.getKey())
            .append(", ErrorMsg: ")
            .append(entry.getValue().getErrorMsg());
      }
    }

    log.warn(builder.toString());
  }

  private void checkFuturesAndEvictCache(
      Queue<Future> futures, YamlType yamlType, String accountId, ChangeType changeType) {
    while (!futures.isEmpty()) {
      try {
        if (futures.peek().isDone()) {
          futures.poll().get();
        }
      } catch (InterruptedException | ExecutionException e) {
        log.error("Error while waiting for processing of entities of type {} for account {} ",
            yamlType != null ? yamlType.name() : "", accountId);
      }
      quietSleep(ofMillis(10));
    }
    invalidateUserRelatedCacheIfNeeded(accountId, yamlType, changeType);
  }

  private void invalidateUserRelatedCacheIfNeeded(String accountId, YamlType yamlType, ChangeType changeType) {
    if (ChangeType.ADD == changeType && rbacCreateYamlTypes.contains(yamlType)) {
      if (yamlType == YamlType.APPLICATION || yamlType == YamlType.ENVIRONMENT) {
        authService.evictUserPermissionAndRestrictionCacheForAccount(accountId, true, true);
      } else {
        authService.evictUserPermissionCacheForAccount(accountId, true);
      }
    } else if (ChangeType.MODIFY == changeType && rbacUpdateYamlTypes.contains(yamlType)) {
      if (yamlType == YamlType.ENVIRONMENT) {
        authService.evictUserPermissionAndRestrictionCacheForAccount(accountId, true, true);
      } else {
        authService.evictUserPermissionCacheForAccount(accountId, true);
      }
    } else if (ChangeType.DELETE == changeType && rbacDeleteYamlTypes.contains(yamlType)) {
      authService.evictUserPermissionAndRestrictionCacheForAccount(accountId, true, true);
    }
  }

  private void processYamlChange(ChangeContext changeContext, List<ChangeContext> changeContextList)
      throws HarnessException {
    notNullCheck("changeContext is null", changeContext, USER);
    Change change = changeContext.getChange();
    notNullCheck("FileChange is null", change, USER);
    notNullCheck("ChangeType is null for change:" + change.getFilePath(), change.getChangeType(), USER);

    if (!(change instanceof GitFileChange)) {
      throw new IllegalArgumentException(
          "Expected change to be instance of GitFileChange. Found: " + change.getClass().getCanonicalName());
    }

    String commitId = ((GitFileChange) changeContext.getChange()).getCommitId();
    String accountId = change.getAccountId();

    // do not process commits which exceed limits
    // if you process them, the processing throws validation errors which populates the Alerts page with GitSyncErrors
    if (isNotEmpty(commitId) && failedCommitStore.didExceedLimit(new FailedCommitStore.Commit(commitId, accountId))) {
      log.warn("CommitId: {} blocked by rate limit", commitId);
      return;
    }

    // If its not a yaml file, we don't have a handler for that file
    if (!change.getFilePath().endsWith(YAML_EXTENSION) && !doesEntityUsesActualFile(change.getFilePath())
        && !isPcfOverrideFile(change.getFilePath()) && !isAzureAppServiceOverrideFile(change.getFilePath())) {
      return;
    }

    BaseYamlHandler yamlSyncHandler = changeContext.getYamlSyncHandler();

    switch (change.getChangeType()) {
      case ADD:
      case MODIFY:
        upsertFromYaml(changeContext, changeContextList);
        break;
      case DELETE:
        yamlSyncHandler.delete(changeContext);
        break;
      case RENAME:
        // TODO
      default:
        // TODO
        break;
    }
  }

  private boolean isPcfOverrideFile(String filePath) {
    return Pattern.compile(YamlType.MANIFEST_FILE_PCF_OVERRIDE_ENV_SERVICE_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches()
        || Pattern.compile(YamlType.MANIFEST_FILE_PCF_OVERRIDE_ENV_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches();
  }

  private boolean isAzureAppServiceOverrideFile(String filePath) {
    return Pattern.compile(MANIFEST_FILE_APP_SETTINGS_ENV_OVERRIDE.getPathExpression()).matcher(filePath).matches()
        || Pattern.compile(MANIFEST_FILE_APP_SETTINGS_ENV_SERVICE_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches()
        || Pattern.compile(MANIFEST_FILE_CONN_STRINGS_ENV_OVERRIDE.getPathExpression()).matcher(filePath).matches()
        || Pattern.compile(MANIFEST_FILE_CONN_STRINGS_ENV_SERVICE_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches();
  }

  private boolean doesEntityUsesActualFile(String filePath) {
    return Pattern.compile(YamlType.MANIFEST_FILE.getPathExpression()).matcher(filePath).matches()
        || filePath.contains(OC_PARAMS_FILE) || filePath.contains(KUSTOMIZE_PATCHES_FILE)
        || filePath.contains(APP_SETTINGS_FILE) || filePath.contains(CONN_STRINGS_FILE);
  }

  @VisibleForTesting
  void upsertFromYaml(ChangeContext changeContext, List<ChangeContext> changeContextList) throws HarnessException {
    GitFileChange change = (GitFileChange) changeContext.getChange();
    String commitId = change.getCommitId();
    String accountId = change.getAccountId();
    BaseYamlHandler yamlSyncHandler = changeContext.getYamlSyncHandler();

    try {
      checkThatEntityIdInChangeAndInYamlIsSame(yamlSyncHandler, accountId, change.getFilePath(), changeContext);
      yamlSyncHandler.upsertFromYaml(changeContext, changeContextList);

      // Handling for tags
      harnessTagYamlHelper.upsertTagLinksIfRequired(changeContext);

    } catch (WingsException e) {
      if (e.getCode() == ErrorCode.USAGE_LIMITS_EXCEEDED) {
        log.info("Usage Limit Exceeded. Account: {}. Message: {}", change.getAccountId(), e.getMessage());
        if (isNotEmpty(commitId)) {
          failedCommitStore.exceededLimit(new FailedCommitStore.Commit(commitId, accountId));
        }
      }
      throw e;
    }
  }

  private void checkThatEntityIdInChangeAndInYamlIsSame(
      BaseYamlHandler yamlSyncHandler, String accountId, String filePath, ChangeContext changeContext) {
    GitFileChange change = (GitFileChange) changeContext.getChange();
    if (changeContext.getYamlType() == TAG || changeContext.getYamlType() == APPLICATION_DEFAULTS
        || changeContext.getYamlType() == ACCOUNT_DEFAULTS || isEmpty(change.getEntityId())) {
      return;
    }
    String entityIdFromYaml;
    try {
      UuidAware entity = (UuidAware) yamlSyncHandler.get(accountId, filePath, changeContext);
      entityIdFromYaml = entity == null ? null : entity.getUuid();
    } catch (WingsException ex) {
      // There are entities which we don't store and get is not supported for them
      // For those entities we don't have to validate the ids
      if (ex.getCode() == ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION) {
        return;
      }
      log.error("Exception while getting entity from the change context for filePath : [{}] in accountId : [{}]",
          filePath, accountId, ex);
      throw ex;
    }

    if (isNotEmpty(change.getEntityId())) {
      // Its an update operation
      if (!change.getEntityId().equals(entityIdFromYaml)) {
        throw new InvalidRequestException(
            "The entity Id provided in the request and the entity Id of the Yaml doesn't match");
      }
    }
  }

  private String getEntityIdFromYaml(
      BaseYamlHandler yamlHandler, String accountId, String filePath, ChangeContext changeContext) {
    return null;
  }

  @VisibleForTesting
  BaseYaml getYaml(String yamlString, Class<? extends BaseYaml> yamlClass, String accountId) throws IOException {
    //    todo @abhinav: we can cache this object
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    if (featureFlagService.isNotEnabled(FeatureName.SPG_ENABLE_GIT_SYNC_YAML_VALIDATE, accountId)) {
      mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
    return mapper.readValue(yamlString, yamlClass);
  }

  private final class FilePathComparator implements Comparator<Change> {
    @Override
    public int compare(Change lhs, Change rhs) {
      int lCoffecient = lhs.getChangeType() == ChangeType.DELETE ? -1 : 1;
      int rCoffecient = rhs.getChangeType() == ChangeType.DELETE ? -1 : 1;
      return (lCoffecient * findOrdinal(lhs.getFilePath(), lhs.getAccountId()))
          - (rCoffecient * findOrdinal(rhs.getFilePath(), rhs.getAccountId()));
    }
  }

  private final class ChangeContextComparator implements Comparator<ChangeContext> {
    @Override
    public int compare(ChangeContext lhs, ChangeContext rhs) {
      int lCoffecient = lhs.getChange().getChangeType() == ChangeType.DELETE ? -1 : 1;
      int rCoffecient = rhs.getChange().getChangeType() == ChangeType.DELETE ? -1 : 1;
      return (lCoffecient * findOrdinal(lhs.getChange().getFilePath(), lhs.getChange().getAccountId()))
          - (rCoffecient * findOrdinal(rhs.getChange().getFilePath(), rhs.getChange().getAccountId()));
    }
  }

  public int findOrdinal(String yamlFilePath, String accountId) {
    AtomicInteger count = new AtomicInteger();
    Optional<YamlType> first = getYamlProcessingOrder()
                                   .stream()
                                   .filter(yamlType -> {
                                     count.incrementAndGet();
                                     return Pattern.matches(yamlType.getPathExpression(), yamlFilePath);
                                   })
                                   .findFirst();

    if (first.isPresent()) {
      return count.get();
    } else {
      return Integer.MIN_VALUE;
    }
  }

  private List<YamlType> getYamlProcessingOrder() {
    // If anything is to be hidden behind feature flag it cab=n bre removed from the list.
    return yamlProcessingOrder;
  }

  @Override
  public YamlType findYamlType(String yamlFilePath) throws YamlException {
    Optional<YamlType> first = getYamlProcessingOrder()
                                   .stream()
                                   .filter(yamlType -> Pattern.matches(yamlType.getPathExpression(), yamlFilePath))
                                   .findFirst();

    if (first.isPresent()) {
      return first.get();
    } else {
      throw new YamlException("Unknown yaml type for path: " + yamlFilePath, null);
    }
  }

  /**
   * Check if the yaml is valid
   *
   * @param yamlString
   * @return
   */
  private void validateYaml(String yamlString) throws ScannerException {
    Yaml yamlObj = new Yaml(new SafeConstructor(new LoaderOptions()));

    // We just load the yaml to see if its well formed.
    LinkedHashMap<String, Object> load = (LinkedHashMap<String, Object>) yamlObj.load(yamlString);
    checkOnPhasesNamesWithDots(load);
    checkOnEmptyAmiFiltersNames(load);
    checkOnEmptyAmiTagNames(load);
    YamlWorkflowValidator.validateWorkflowPreDeploymentSteps(load);
  }

  /**
   * Get yaml representation for a given file path
   *
   * @param yamlFilePath
   * @param accountId
   * @param yamlSubType
   * @return
   */
  @Override
  public BaseYaml getYamlForFilePath(String accountId, String yamlFilePath, String yamlSubType, String applicationId) {
    if (EmptyPredicate.isEmpty(yamlFilePath)) {
      throw new InvalidArgumentsException(Pair.of("yaml file path", "cannot be empty"));
    }
    BaseYaml yamlForFilePath = null;
    try {
      YamlType yamlType = findYamlType(yamlFilePath);
      BaseYamlHandler yamlHandler = yamlHandlerFactory.getYamlHandler(yamlType, yamlSubType);
      yamlForFilePath = yamlHandler.toYaml(yamlHandler.get(accountId, yamlFilePath), applicationId);
    } catch (Exception e) {
      log.error(format("Error while fetching yaml content for file path %s", yamlFilePath));
    }
    return yamlForFilePath;
  }

  @Override
  public YamlOperationResponse upsertYAMLFilesAsZip(final String accountId, final InputStream fileInputStream) {
    try (AccountLogContext ignore1 = new AccountLogContext(accountId, OverrideBehavior.OVERRIDE_ERROR)) {
      final String auditHeaderIdFromGlobalContext = auditService.getAuditHeaderIdFromGlobalContext();
      if (!Strings.isNullOrEmpty(auditHeaderIdFromGlobalContext)) {
        final AuditHeader currentAuditHeader = wingsPersistence.get(AuditHeader.class, auditHeaderIdFromGlobalContext);
        if (currentAuditHeader != null) {
          Future<YamlOperationResponse> future =
              Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("upsert-yamls-as-zip").build())
                  .submit(() -> {
                    List unauthorizedFiles = null;
                    try {
                      auditHelper.setAuditContext(currentAuditHeader);
                      List changeList = getChangesForZipFile(accountId, fileInputStream, null);
                      List originalChanges = new ArrayList<>(changeList);

                      unauthorizedFiles = (List) io.harness.data.structure.CollectionUtils.emptyIfNull(changeList)
                                              .stream()
                                              .map(fileChange -> {
                                                String filePath = ((GitFileChange) fileChange).getFilePath();
                                                try {
                                                  yamlAuthHandler.authorizeDelete(filePath, accountId);
                                                } catch (Exception e) {
                                                  return fileChange;
                                                }
                                                return null;
                                              })
                                              .filter(Objects::nonNull)
                                              .collect(toList());

                      ListUtils.removeAll(changeList, unauthorizedFiles);

                      List<ChangeContext> processedChangeList = processChangeSet(changeList);
                      if (isEmpty(unauthorizedFiles)) {
                        return prepareSuccessfulYAMLOperationResponse(processedChangeList, originalChanges);
                      } else {
                        return prepareFailedYAMLOperationResponse("Unable to process few files", new HashMap<>(),
                            new ArrayList<>(), new ArrayList<>(), unauthorizedFiles);
                      }
                    } catch (YamlProcessingException ex) {
                      log.warn(format("Unable to process uploaded zip file for account %s, error: %s", accountId, ex));
                      return prepareFailedYAMLOperationResponse(ExceptionUtils.getMessage(ex),
                          ex.getFailedYamlFileChangeMap(), ex.getChangeContextList(), ex.getChangeList(),
                          unauthorizedFiles);
                    }
                  });
          return future.get(30, TimeUnit.SECONDS);
        }
      }
      return null;
    } catch (Exception ex) {
      log.warn(format("Unable to process uploaded zip file for account %s, error: %s", accountId, ex));
      throw new InvalidArgumentsException("Unable to open zip file or some error in content of zip file", USER, ex);
    }
  }

  /**
   * Delete YAML files by paths
   * @param accountId
   * @param filePaths
   * @return
   */
  @Override
  public YamlOperationResponse deleteYAMLByPaths(final String accountId, final List<String> filePaths) {
    List unauthorizedFiles = null;
    try (AccountLogContext ignore1 = new AccountLogContext(accountId, OverrideBehavior.OVERRIDE_ERROR)) {
      List changeList = filePaths.stream()
                            .map(filePath
                                -> GitFileChange.Builder.aGitFileChange()
                                       .withFileContent(DEFAULT_YAML)
                                       .withFilePath(filePath)
                                       .withAccountId(accountId)
                                       .withChangeType(ChangeType.DELETE)
                                       .build())
                            .collect(toList());
      List originalChanges = new ArrayList<>(changeList);

      unauthorizedFiles = (List) changeList.stream()
                              .map(fileChange -> {
                                String filePath = ((GitFileChange) fileChange).getFilePath();
                                try {
                                  yamlAuthHandler.authorizeDelete(filePath, accountId);
                                } catch (Exception e) {
                                  return fileChange;
                                }
                                return null;
                              })
                              .filter(Objects::nonNull)
                              .collect(toList());

      ListUtils.removeAll(changeList, unauthorizedFiles);

      List<ChangeContext> processedChangesWithContext = processChangeSet(changeList);
      return prepareSuccessfulYAMLOperationResponse(processedChangesWithContext, originalChanges);
    } catch (YamlProcessingException ex) {
      log.warn(format("Error while deleting yaml file paths(s) for account %s, error: %s", accountId, ex));
      return prepareFailedYAMLOperationResponse(ex.getMessage(), ex.getFailedYamlFileChangeMap(),
          ex.getChangeContextList(), ex.getChangeList(), unauthorizedFiles);
    }
  }

  @Override
  public YamlOperationResponse deleteYAMLByPathsV2(
      final String accountId, final List<EntityInformation> entityInformations) {
    List<Change> unauthorizedFiles = null;
    try (AccountLogContext ignore1 = new AccountLogContext(accountId, OverrideBehavior.OVERRIDE_ERROR)) {
      List<Change> changeList = io.harness.data.structure.CollectionUtils.emptyIfNull(entityInformations)
                                    .stream()
                                    .map(entityInformation -> {
                                      String fileContent;
                                      if (isNotEmpty(entityInformation.getFileContent())) {
                                        fileContent = entityInformation.getFileContent();
                                      } else {
                                        fileContent = DEFAULT_YAML;
                                      }
                                      return GitFileChange.Builder.aGitFileChange()
                                          .withFileContent(fileContent)
                                          .withFilePath(entityInformation.getFilePath())
                                          .withAccountId(accountId)
                                          .withChangeType(ChangeType.DELETE)
                                          .build();
                                    })
                                    .collect(toList());
      unauthorizedFiles = changeList.stream()
                              .map(change -> {
                                try {
                                  yamlAuthHandler.authorizeDelete(change.getFilePath(), accountId);
                                } catch (Exception e) {
                                  return change;
                                }
                                return null;
                              })
                              .filter(Objects::nonNull)
                              .collect(toList());
      List<Change> changes = ListUtils.removeAll(changeList, unauthorizedFiles);
      List<ChangeContext> processedChangesWithContext = processChangeSet(changeList);
      return prepareSuccessfulYAMLOperationResponse(processedChangesWithContext, changeList);
    } catch (YamlProcessingException ex) {
      log.warn(format("Error while deleting yaml file paths(s) for account %s, error: %s", accountId, ex));
      return prepareFailedYAMLOperationResponse(ExceptionUtils.getMessage(ex), ex.getFailedYamlFileChangeMap(),
          ex.getChangeContextList(), ex.getChangeList(), unauthorizedFiles);
    }
  }

  @Override
  public FileOperationStatus upsertYAMLFile(String accountId, String yamlFilePath, String yamlContent) {
    if (yamlContent.isEmpty()) {
      throw new InvalidArgumentsException(Pair.of("Input YAML", "cannot be empty"));
    }
    try (AccountLogContext ignore1 = new AccountLogContext(accountId, OverrideBehavior.OVERRIDE_ERROR);
         YamlProcessingLogContext ignore2 =
             YamlProcessingLogContext.builder().filePath(yamlFilePath).build(OverrideBehavior.OVERRIDE_ERROR)) {
      List changeList = Arrays.asList(GitFileChange.Builder.aGitFileChange()
                                          .withFilePath(yamlFilePath)
                                          .withFileContent(yamlContent)
                                          .withChangeType(ChangeType.ADD)
                                          .withAccountId(accountId)
                                          .build());
      yamlAuthHandler.authorizeUpsert(yamlFilePath, accountId);
      List<ChangeContext> processedChangeList = processChangeSet(changeList);
      if (!processedChangeList.isEmpty()) {
        final ChangeContext changeContext = processedChangeList.get(0);
        String entityId = getEntityId(yamlFilePath, changeContext);
        // added tracability due to many recent issues
        doTracing(accountId, yamlFilePath, changeContext);

        return FileOperationStatus.builder()
            .status(FileOperationStatus.Status.SUCCESS)
            .errorMssg("")
            .yamlFilePath(changeContext.getChange().getFilePath())
            .entityId(entityId)
            .build();
      }
    } catch (YamlProcessingException ex) {
      log.warn(format("Unable to process yaml file for account %s, error: %s", accountId, ex));
      if (ex != null && !isEmpty(ex.getFailedYamlFileChangeMap())) {
        final Map.Entry<String, ChangeWithErrorMsg> entry =
            ex.getFailedYamlFileChangeMap().entrySet().iterator().next();
        final ChangeWithErrorMsg changeWithErrorMsg = entry.getValue();
        return FileOperationStatus.builder()
            .status(FileOperationStatus.Status.FAILED)
            .errorMssg(changeWithErrorMsg.getErrorMsg())
            .yamlFilePath(changeWithErrorMsg.getChange().getFilePath())
            .build();
      }
    }
    return null;
  }

  private void doTracing(String accountId, String yamlFilePath, ChangeContext changeContext) {
    try {
      Object o = changeContext.getYamlSyncHandler().get(accountId, yamlFilePath);
      if (o == null) {
        log.error("Not able to create entity due to some issue for file path {}.", yamlFilePath);
      } else {
        log.info("Created entity {} for filepath {}", o, yamlFilePath);
      }
    } catch (Exception e) {
      log.error(String.format("Some issue while validating creation of entity with file path %s", yamlFilePath), e);
    }
  }

  private String getEntityId(String yamlFilePath, ChangeContext changeContext) {
    PersistentEntity entity = changeContext.getEntity();
    String entityId = null;
    if (entity instanceof Base) {
      entityId = ((Base) entity).getUuid();
    } else if (entity instanceof UuidAware) {
      entityId = ((UuidAware) entity).getUuid();
    } else {
      log.info("No entity ID found for the entity {} and filepath {}", entity, yamlFilePath);
    }
    return entityId;
  }

  private YamlOperationResponse prepareFailedYAMLOperationResponse(final String errorMessage,
      final Map<String, ChangeWithErrorMsg> processingFailures, final List<ChangeContext> processedChangesWithContext,
      final List<Change> originalChangeList, List<Change> unauthorizedFiles) {
    final YamlOperationResponseBuilder yamlOperationResponseBuilder =
        YamlOperationResponse.builder().errorMessage(errorMessage).responseStatus(YamlOperationResponse.Status.FAILED);
    if (processingFailures.isEmpty() || CollectionUtils.isEmpty(processedChangesWithContext)
        || CollectionUtils.isEmpty(originalChangeList)) {
      return yamlOperationResponseBuilder.build();
    }
    final List<FileOperationStatus> fileOperationStatusList =
        prepareFileOperationStatusList(processingFailures, unauthorizedFiles);
    final List<Change> failedChangeList =
        new LinkedList<>(processingFailures.values()).stream().map(ChangeWithErrorMsg::getChange).collect(toList());
    final List<Change> successfullyProcessedChanges =
        getFilesWhichAreSuccessfullyProcessed(processedChangesWithContext, failedChangeList);
    fileOperationStatusList.addAll(prepareFileOperationStatusListFromChangeList(
        failedChangeList, successfullyProcessedChanges, originalChangeList));
    return yamlOperationResponseBuilder.filesStatus(fileOperationStatusList).build();
  }

  @VisibleForTesting
  List<Change> getFilesWhichAreSuccessfullyProcessed(
      List<ChangeContext> processedChangesWithContext, List<Change> failedChangeList) {
    if (isEmpty(processedChangesWithContext)) {
      return Collections.emptyList();
    }
    if (isEmpty(failedChangeList)) {
      return processedChangesWithContext.stream().map(changeContext -> changeContext.getChange()).collect(toList());
    }

    Set<String> failedFilePathSet =
        failedChangeList.stream().map(change -> change.getFilePath()).collect(Collectors.toSet());
    return ListUtils.emptyIfNull(processedChangesWithContext)
        .stream()
        .filter(changeContext -> !failedFilePathSet.contains(changeContext.getChange().getFilePath()))
        .map(changeContext -> changeContext.getChange())
        .collect(toList());
  }

  private YamlOperationResponse prepareSuccessfulYAMLOperationResponse(
      final List<ChangeContext> processedChangesWithContext, final List<Change> originalChangeList) {
    final YamlOperationResponseBuilder yamlOperationResponseBuilder = YamlOperationResponse.builder();
    if (CollectionUtils.isEmpty(processedChangesWithContext) || CollectionUtils.isEmpty(originalChangeList)) {
      return yamlOperationResponseBuilder.responseStatus(YamlOperationResponse.Status.FAILED)
          .filesStatus(Collections.EMPTY_LIST)
          .errorMessage("No yaml files found in the uploaded zip file.")
          .build();
    }

    final List<Change> processedChangeList =
        processedChangesWithContext.stream().map(result -> result.getChange()).collect(toList());
    List<FileOperationStatus> fileOperationStatusList =
        prepareFileOperationStatusListFromChangeList(Collections.EMPTY_LIST, processedChangeList, originalChangeList);
    fileOperationStatusList = addEntityIdToFileOperationResponse(fileOperationStatusList, processedChangesWithContext);

    return yamlOperationResponseBuilder.responseStatus(YamlOperationResponse.Status.SUCCESS)
        .filesStatus(fileOperationStatusList)
        .errorMessage("")
        .build();
  }

  private List<FileOperationStatus> addEntityIdToFileOperationResponse(
      List<FileOperationStatus> fileOperationStatusList, List<ChangeContext> processedChangesWithContext) {
    if (CollectionUtils.isEmpty(processedChangesWithContext)) {
      return fileOperationStatusList;
    }
    Map<String, String> filePathToEntityIdMap = processedChangesWithContext.stream().collect(HashMap::new,
        (mapping, changeContext)
            -> mapping.put(changeContext.getChange().getFilePath(),
                changeContext.getEntity() != null ? ((Base) changeContext.getEntity()).getUuid() : null),
        (mapping, changeContext) -> {});
    return fileOperationStatusList.stream()
        .map(item
            -> FileOperationStatus.builder()
                   .yamlFilePath(item.getYamlFilePath())
                   .status(item.getStatus())
                   .errorMssg(item.getErrorMssg())
                   .entityId(filePathToEntityIdMap.getOrDefault(item.getYamlFilePath(), ""))
                   .build())
        .collect(toList());
  }

  private List<Change> getSkippedChangeList(final List<Change> originalChangeList,
      final List<Change> processedChangeList, final List<Change> failedChangeList) {
    List<Change> skippedFiles = new LinkedList<>(originalChangeList);
    Set<Change> skippedFileSet = new HashSet<>(skippedFiles);
    Set<Change> processedChangeSet = new HashSet<>(processedChangeList);
    Set<Change> failedChangeSet = new HashSet<>(failedChangeList);
    skippedFileSet.removeAll(processedChangeSet);
    skippedFileSet.removeAll(failedChangeSet);
    skippedFiles = new LinkedList<>(skippedFileSet);
    return skippedFiles;
  }

  private List<FileOperationStatus> prepareFileOperationStatusListFromChangeList(final List<Change> failedChangeList,
      final List<Change> processedChangeList, final List<Change> originalChangeList) {
    if (CollectionUtils.isEmpty(processedChangeList) || CollectionUtils.isEmpty(originalChangeList)) {
      return Collections.EMPTY_LIST;
    }
    List<FileOperationStatus> fileOperationStatusList = new LinkedList<>();
    if (originalChangeList.size() >= processedChangeList.size()) {
      List<Change> skippedFiles = getSkippedChangeList(originalChangeList, processedChangeList, failedChangeList);
      if (skippedFiles.size() > 0) {
        fileOperationStatusList.addAll(
            prepareFileOperationStatusList(skippedFiles, FileOperationStatus.Status.SKIPPED));
      }
      List<Change> successfullyProcessedFiles = new LinkedList<>(processedChangeList);
      successfullyProcessedFiles.retainAll(originalChangeList);
      if (successfullyProcessedFiles.size() > 0) {
        fileOperationStatusList.addAll(
            prepareFileOperationStatusList(successfullyProcessedFiles, FileOperationStatus.Status.SUCCESS));
      }
    }
    return fileOperationStatusList;
  }

  private List<FileOperationStatus> prepareFileOperationStatusList(
      final List<Change> processedChangeList, final FileOperationStatus.Status status) {
    if (CollectionUtils.isEmpty(processedChangeList)) {
      return Collections.EMPTY_LIST;
    }
    return processedChangeList.stream()
        .map(result
            -> FileOperationStatus.builder().status(status).errorMssg("").yamlFilePath(result.getFilePath()).build())
        .collect(toList());
  }

  private List<FileOperationStatus> prepareFileOperationStatusList(
      final Map<String, ChangeWithErrorMsg> processingFailures, List<Change> unauthorizedFiles) {
    if (processingFailures.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    List<FileOperationStatus> fileOperationStatusList = new LinkedList<>();
    for (Map.Entry<String, ChangeWithErrorMsg> entry : processingFailures.entrySet()) {
      final ChangeWithErrorMsg changeWithErrorMsg = entry.getValue();
      fileOperationStatusList.add(FileOperationStatus.builder()
                                      .status(FileOperationStatus.Status.FAILED)
                                      .errorMssg(changeWithErrorMsg.getErrorMsg())
                                      .yamlFilePath(changeWithErrorMsg.getChange().getFilePath())
                                      .build());
    }
    unauthorizedFiles.forEach(file -> {
      fileOperationStatusList.add(
          FileOperationStatus.builder()
              .status(FileOperationStatus.Status.FAILED)
              .errorMssg(String.format("User unauthorized for changing entity for file path: [%s]", file.getFilePath()))
              .yamlFilePath(file.getFilePath())
              .build());
    });
    return fileOperationStatusList;
  }

  private void checkOnPhasesNamesWithDots(LinkedHashMap<String, Object> load) {
    if (load.containsKey(PHASES)) {
      List<String> phaseNames =
          ((List<LinkedHashMap<String, String>>) load.get(PHASES)).stream().map(map -> map.get(NAME)).collect(toList());

      phaseNames.forEach(name -> {
        if (name.contains(".")) {
          throw new InvalidYamlNameException("Invalid phase name [" + name + "]. Dots are not permitted");
        }
      });
    }
  }

  private void checkOnEmptyAmiFiltersNames(LinkedHashMap<String, Object> load) {
    if (load.containsKey(AMI_FILTERS)) {
      List<String> phaseNames = ((List<LinkedHashMap<String, String>>) load.get(AMI_FILTERS))
                                    .stream()
                                    .map(map -> map.get(NAME))
                                    .collect(toList());

      phaseNames.forEach(name -> {
        if (name.trim().isEmpty()) {
          throw new InvalidYamlNameException("Invalid amiFilter name. Empty names are not permitted");
        }
      });
    }
  }

  private void checkOnEmptyAmiTagNames(LinkedHashMap<String, Object> load) {
    if (load.containsKey(AMI_TAGS)) {
      List<String> phaseNames = ((List<LinkedHashMap<String, String>>) load.get(AMI_TAGS))
                                    .stream()
                                    .map(map -> map.get(NAME))
                                    .collect(toList());

      phaseNames.forEach(name -> {
        if (name.trim().isEmpty()) {
          throw new InvalidYamlNameException("Invalid amiTag name. Empty names are not permitted");
        }
      });
    }
  }
}
