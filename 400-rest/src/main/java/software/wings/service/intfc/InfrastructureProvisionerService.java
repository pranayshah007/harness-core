/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.api.DeploymentType;
import software.wings.beans.BlueprintProperty;
import software.wings.beans.InfrastructureMappingBlueprint.CloudProviderType;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.InfrastructureProvisionerDetails;
import software.wings.beans.NameValuePair;
import software.wings.beans.TerraGroupProvisioners;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.intfc.ownership.OwnedByApplication;
import software.wings.sm.ExecutionContext;
import software.wings.sm.states.ManagerExecutionLogCallback;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.Valid;
import javax.ws.rs.core.StreamingOutput;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface InfrastructureProvisionerService extends OwnedByApplication {
  PageResponse<InfrastructureProvisioner> list(PageRequest<InfrastructureProvisioner> pageRequest);

  PageResponse<InfrastructureProvisioner> listByBlueprintDetails(@NotEmpty String appId,
      String infrastructureProvisionerType, String serviceId, DeploymentType deploymentType,
      CloudProviderType cloudProviderType);

  PageResponse<InfrastructureProvisionerDetails> listDetails(
      PageRequest<InfrastructureProvisioner> pageRequest, boolean withTags, String tagFilter, @NotEmpty String appId);

  @ValidationGroups(Create.class)
  InfrastructureProvisioner save(@Valid InfrastructureProvisioner infrastructureProvisioner);

  @ValidationGroups(Update.class)
  InfrastructureProvisioner update(@Valid InfrastructureProvisioner infrastructureProvisioner);

  InfrastructureProvisioner get(String appId, String infrastructureProvisionerId);
  InfrastructureProvisioner getByName(String appId, String provisionerName);

  void delete(String appId, String infrastructureProvisionerId);

  void pruneDescendingEntities(String appId, String infrastructureProvisionerId);

  Map<String, Object> resolveProperties(Map<String, Object> contextMap, List<BlueprintProperty> properties,
      Optional<ManagerExecutionLogCallback> executionLogCallbackOptional, Optional<String> region,
      String infraProvisionerTypeKey);

  void regenerateInfrastructureMappings(String provisionerId, ExecutionContext context, Map<String, Object> outputs);

  void regenerateInfrastructureMappings(String provisionerId, ExecutionContext context, Map<String, Object> outputs,
      Optional<ManagerExecutionLogCallback> executionLogCallback, Optional<String> region);

  List<AwsCFTemplateParamsData> getCFTemplateParamKeys(String type, String region, String awsConfigId, String data,
      String appId, String scmSettingId, String sourceRepoBranch, String templatePath, String commitId,
      Boolean useBranch, String repoName);

  void delete(String appId, String infrastructureProvisionerId, boolean syncFromGit);

  List<NameValuePair> getTerraformVariables(String appId, String scmSettingId, String terraformDirectory,
      String accountId, String sourceRepoBranch, String commitId, String repoName);

  List<String> getTerraformTargets(String appId, String accountId, String provisionerId);

  boolean isTemplatizedProvisioner(TerraGroupProvisioners infrastructureProvisioner);

  StreamingOutput downloadTerraformState(String provisionerId, String envId);

  ShellScriptInfrastructureProvisioner getShellScriptProvisioner(String appId, String provisionerId);

  Map<String, String> extractTextVariables(List<NameValuePair> variables, ExecutionContext context);

  Map<String, String> extractUnresolvedTextVariables(List<NameValuePair> variables);

  Map<String, EncryptedDataDetail> extractEncryptedTextVariables(
      List<NameValuePair> variables, String appId, String workflowExecutionId);

  String getEntityId(String provisionerId, String envId);

  ManagerExecutionLogCallback getManagerExecutionCallback(String appId, String activityId, String commandUnitName);

  Map<String, Object> resolveExpressions(InfrastructureDefinition infrastructureDefinition,
      Map<String, Object> contextMap, InfrastructureProvisioner infrastructureProvisioner);

  boolean areExpressionsValid(
      InfrastructureProvisioner infrastructureProvisioner, Map<String, Object> resolvedExpressions);
}
