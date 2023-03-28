/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.Base;
import software.wings.beans.EnvSummary;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.container.KubernetesPayload;
import software.wings.beans.stats.CloneMetadata;
import software.wings.service.intfc.ownership.OwnedByApplication;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by anubhaw on 4/1/16.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface EnvironmentService extends OwnedByApplication {
  /**
   * The constant PROD_ENV.
   */
  String PROD_ENV = "Production";
  /**
   * The constant QA_ENV.
   */
  String QA_ENV = "Quality Assurance";
  /**
   * The constant DEV_ENV.
   */
  String DEV_ENV = "Development";

  /**
   * List.
   *
   * @param request      the request
   * @param hitSecondary
   * @return the page response
   */
  PageResponse<Environment> list(
      PageRequest<Environment> request, boolean withTags, String tagFilter, boolean hitSecondary);

  PageResponse<Environment> listWithSummary(
      PageRequest<Environment> request, boolean withTags, String tagFilter, List<String> appIds);

  Environment get(@NotEmpty String appId, @NotEmpty String envId, boolean withSummary);

  /**
   * Gets the environment and does not throw exception if not found
   *
   * @param appId
   * @param envId
   * @return
   */
  Environment get(@NotEmpty String appId, @NotEmpty String envId);

  Environment getWithTags(@NotEmpty String appId, @NotEmpty String envId);

  Environment getEnvironmentByName(String appId, String environmentName);

  Environment getEnvironmentByName(String appId, String environmentName, boolean withServiceTemplates);

  /**
   * Exist boolean.
   *
   * @param appId the app id
   * @param envId the env id
   * @return the boolean
   */
  boolean exist(@NotEmpty String appId, @NotEmpty String envId);

  /**
   * Save.
   *
   * @param environment the environment
   * @return the environment
   */
  @ValidationGroups(Create.class) Environment save(@Valid Environment environment);

  void setEnvironmentTypeTag(Environment environment);

  /**
   * Update.
   *
   * @param environment the environment
   * @return the environment
   */
  @ValidationGroups(Update.class) Environment update(@Valid Environment environment);

  /**
   * Delete.
   *
   * @param appId the app id
   * @param envId the env id
   */
  void delete(@NotEmpty String appId, @NotEmpty String envId);

  /**
   * Prune descending objects.
   *
   * @param appId the app id
   * @param envId the env id
   */
  void pruneDescendingEntities(@NotEmpty String appId, @NotEmpty String envId);

  /**
   * Create default environments.
   *
   * @param appId the app id
   */
  void createDefaultEnvironments(@NotEmpty String appId);

  /**
   * Gets env by app.
   *
   * @param appId the app id
   * @return the env by app
   */
  List<Environment> getEnvByApp(@NotEmpty String appId);

  List<Environment> getEnvByAccountId(String accountId);

  /**
   * Gets env ids by app.
   *
   * @param appId
   * @return
   */
  List<String> getEnvIdsByApp(@NotEmpty String appId);

  Map<String, List<Base>> getAppIdEnvMap(Set<String> appIds, String accountId);

  /**
   * @param appIds          the applicationIds for which we need to fetch the environments
   * @param environmentType The type of environment needed
   * @return A map of the ids of environments in the given application and of the given type grouped
   * by applications
   */
  Map<String, Set<String>> getAppIdEnvIdMap(Set<String> appIds);

  /**
   * Get EnvIds map by AppIds.
   *
   * @param appIds
   * @return
   */
  Map<String, Set<String>> getAppIdEnvIdMapByType(Set<String> appIds, EnvironmentType environmentType);

  /**
   * Clones Environment along with Service Infrastructure
   *
   * @param appId
   * @param envId
   * @param cloneMetadata
   * @return
   */
  Environment cloneEnvironment(@NotEmpty String appId, @NotEmpty String envId, CloneMetadata cloneMetadata);

  /**
   * @param appId
   * @param envId
   * @return
   */
  List<Service> getServicesWithOverrides(@NotEmpty String appId, @NotEmpty String envId);

  /**
   * @param accountId the accountId
   * @param envIds    the list of environmentIds
   * @return list of names of environments
   */
  List<String> getNames(@NotEmpty String accountId, @Nonnull List<String> envIds);

  List<Environment> getEnvironmentsFromIds(@NotEmpty String accountId, @Nonnull List<String> envIds);

  Environment setConfigMapYaml(String appId, String envId, KubernetesPayload kubernetesPayload);

  Environment setConfigMapYamlForService(
      String appId, String envId, String serviceTemplateId, KubernetesPayload kubernetesPayload);

  void deleteConfigMapYamlByServiceTemplateId(String appId, String serviceTemplateId);

  Environment update(Environment environment, boolean fromYaml);

  void delete(String appId, String envId, boolean syncFromGit);

  List<EnvSummary> obtainEnvironmentSummaries(String appId, List<String> envIds);

  ManifestFile createValues(
      String appId, String envId, String serviceId, ManifestFile manifestFile, AppManifestKind kind);

  ManifestFile updateValues(
      String appId, String envId, String serviceId, ManifestFile manifestFile, AppManifestKind kind);

  Environment setHelmValueYaml(
      String appId, String envId, String serviceTemplateId, KubernetesPayload kubernetesPayload);

  Environment deleteHelmValueYaml(String appId, String envId, String serviceTemplateId);

  List<String> getEnvIdsByAppsAndType(List<String> appIds, String environmentType);
}
