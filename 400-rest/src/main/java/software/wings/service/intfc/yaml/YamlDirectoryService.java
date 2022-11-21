/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.LambdaSpecification;
import software.wings.beans.NotificationGroup;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.container.PcfServiceSpecification;
import software.wings.beans.container.UserDataSpecification;
import software.wings.beans.template.Template;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.yaml.FullSyncError;
import software.wings.beans.yaml.GitFileChange;
import software.wings.infra.InfrastructureDefinition;
import software.wings.security.AppPermissionSummary;
import software.wings.security.UserPermissionInfo;
import software.wings.settings.SettingVariableTypes;
import software.wings.verification.CVConfiguration;
import software.wings.yaml.YamlVersion;
import software.wings.yaml.directory.DirectoryNode;
import software.wings.yaml.directory.DirectoryPath;
import software.wings.yaml.directory.FolderNode;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Yaml Directory Service.
 *
 * @author bsollish
 */
@OwnedBy(HarnessTeam.PL)
public interface YamlDirectoryService {
  /**
   * Find by account id.
   *
   * @param accountId the account id
   * @return the directory node (the full account setup/"config-as-code" directory)
   */
  DirectoryNode getDirectory(@NotEmpty String accountId, String appId);

  /**
   * Get Directory (tree/sub-tree structure) by entityId, optionally filtered by nodes ("branches") that have custom git
   * sync
   *
   * @param accountId the account id
   * @param entityId the entity id
   * @param applyPermissions
   * @param userPermissionInfo user permission info
   * @return the directory node (top of the requested "tree")
   */
  FolderNode getDirectory(
      @NotEmpty String accountId, String entityId, boolean applyPermissions, UserPermissionInfo userPermissionInfo);

  YamlGitConfig weNeedToPushChanges(String accountId, String entityId);

  List<GitFileChange> traverseDirectory(List<GitFileChange> gitFileChanges, String accountId, FolderNode fn,
      String path, boolean includeFiles, boolean failFast, List<FullSyncError> listOfYamlErrors);

  FolderNode doTemplateLibrary(String accountId, DirectoryPath directoryPath, String appId,
      String templateLibraryFolderName, YamlVersion.Type type, boolean applyPermissions, Set<String> allowedTemplates);

  String getRootPath();

  String getRootPathByApp(Application app);

  String getRootPathByService(Service service);

  String getRootPathByService(Service service, String applicationPath);

  String getRootPathByServiceCommand(Service service, ServiceCommand serviceCommand);

  String getRootPathByContainerTask(Service service, ContainerTask containerTask);

  String getRootPathByHelmChartSpecification(Service service, HelmChartSpecification helmChartSpecification);

  String getRootPathByLambdaSpec(Service service, LambdaSpecification lambdaSpecification);

  String getRootPathByUserDataSpec(Service service, UserDataSpecification userDataSpecification);

  <H, T> String getRootPathByConfigFile(H helperEntity, T entity);

  String getRootPathByConfigFileOverride(Environment environment);

  String getRootPathByEnvironment(Environment environment);

  String getRootPathByEnvironment(Environment environment, String appName);

  String getRootPathByInfraMapping(InfrastructureMapping infraMapping);

  String getRootPathByInfraDefinition(InfrastructureDefinition infrastructureDefinition);

  String getRootPathByCVConfiguration(CVConfiguration cvConfiguration);

  String getRootPathByPipeline(Pipeline pipeline);

  String getRootPathByWorkflow(Workflow workflow);

  String getRootPathByTrigger(Trigger trigger);

  String getRootPathByArtifactStream(ArtifactStream artifactStream);

  String getRootPathBySettingAttribute(SettingAttribute settingAttribute, SettingVariableTypes settingVariableType);

  String getRootPathByNotificationGroup(NotificationGroup notificationGroup);

  String getRootPathBySettingAttribute(SettingAttribute settingAttribute);

  String getRootPathByPcfServiceSpecification(Service service, PcfServiceSpecification pcfServiceSpecification);

  String getRootPathByInfraProvisioner(InfrastructureProvisioner provisioner);

  String getRootPathByTemplate(Template template);

  String getRootPathByEventConfig(CgEventConfig cgEventConfig);

  void getGitFileChange(DirectoryNode dn, String path, String accountId, boolean includeFiles,
      List<GitFileChange> gitFileChanges, boolean failFast, List<FullSyncError> listOfYamlErrors, boolean gitSyncPath);

  <R, T> String obtainEntityRootPath(R helperEntity, T entity);

  DirectoryNode getApplicationYamlFolderNode(@NotEmpty String accountId, @NotEmpty String applicationId);

  FolderNode doApplication(String applicationId, boolean applyPermissions,
      Map<String, AppPermissionSummary> appPermissionSummaryMap, FolderNode applicationsFolder,
      DirectoryPath directoryPath);

  String getRootPathByApplicationManifest(ApplicationManifest applicationManifest);

  String getRootPathByManifestFile(ManifestFile manifestFile, ApplicationManifest applicationManifest);

  DirectoryNode getApplicationManifestYamlFolderNode(
      @NotEmpty String accountId, @NotEmpty String appId, @NotEmpty String serviceId);

  FolderNode doTemplateLibraryForApp(
      Application app, DirectoryPath directoryPath, boolean applyPermissions, Set<String> allowedTemplates);

  FolderNode generateManifestFileFolderNode(
      String accountId, Service service, List<ManifestFile> manifestFiles, DirectoryPath manifestFilePath);
}
