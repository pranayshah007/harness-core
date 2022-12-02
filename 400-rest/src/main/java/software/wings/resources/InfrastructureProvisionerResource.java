/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.security.PermissionAttribute.Action.CREATE;
import static software.wings.security.PermissionAttribute.Action.DELETE;
import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.Action.UPDATE;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.PROVISIONER;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;

import software.wings.api.DeploymentType;
import software.wings.beans.InfrastructureMappingBlueprint;
import software.wings.beans.InfrastructureMappingBlueprint.CloudProviderType;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.InfrastructureProvisionerDetails;
import software.wings.beans.NameValuePair;
import software.wings.beans.TerraformSourceType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.ListAPI;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.intfc.InfrastructureProvisionerService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.StreamingOutput;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;

@Api("infrastructure-provisioners")
@Path("infrastructure-provisioners")
@Produces("application/json")
@Consumes("application/json")
@Scope(ResourceType.APPLICATION)
public class InfrastructureProvisionerResource {
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;

  @GET
  @Path("blueprint-properties")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Map<Pair<DeploymentType, CloudProviderType>, Map<String, String>>> blueprintProperties() {
    return new RestResponse<>(InfrastructureMappingBlueprint.infrastructureMappingPropertiesMap);
  }

  @GET
  @Timed
  @ExceptionMetered
  @ListAPI(ResourceType.PROVISIONER)
  @AuthRule(permissionType = PROVISIONER, action = READ)
  public RestResponse<PageResponse<InfrastructureProvisioner>> list(
      @QueryParam("appId") List<String> appIds, @BeanParam PageRequest<InfrastructureProvisioner> pageRequest) {
    if (isNotEmpty(appIds)) {
      pageRequest.addFilter("appId", IN, appIds.toArray());
    }
    return new RestResponse<>(infrastructureProvisionerService.list(pageRequest));
  }

  @GET
  @Path("for-task")
  @Timed
  @ExceptionMetered
  @ListAPI(ResourceType.PROVISIONER)
  @AuthRule(permissionType = PROVISIONER, action = READ)
  public RestResponse<PageResponse<InfrastructureProvisioner>> listForTask(@QueryParam("appId") String appId,
      @QueryParam("type") String infrastructureProvisionerType, @QueryParam("serviceId") String serviceId,
      @QueryParam("deploymentType") DeploymentType deploymentType,
      @QueryParam("cloudProviderType") CloudProviderType cloudProviderType) {
    return new RestResponse<>(infrastructureProvisionerService.listByBlueprintDetails(
        appId, infrastructureProvisionerType, serviceId, deploymentType, cloudProviderType));
  }

  @GET
  @Path("details")
  @Timed
  @ExceptionMetered
  @ListAPI(ResourceType.PROVISIONER)
  @AuthRule(permissionType = PROVISIONER, action = READ)
  public RestResponse<PageResponse<InfrastructureProvisionerDetails>> listDetails(@QueryParam("appId") String appId,
      @QueryParam("tagFilter") String tagFilter, @QueryParam("withTags") @DefaultValue("false") boolean withTags,
      @BeanParam PageRequest<InfrastructureProvisioner> pageRequest) {
    if (appId != null) {
      pageRequest.addFilter("appId", EQ, appId);
    }
    return new RestResponse<>(infrastructureProvisionerService.listDetails(pageRequest, withTags, tagFilter, appId));
  }

  @POST
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PROVISIONER, action = CREATE)
  public RestResponse<InfrastructureProvisioner> save(
      @QueryParam("appId") String appId, InfrastructureProvisioner infrastructureProvisioner) {
    infrastructureProvisioner.setAppId(appId);
    return new RestResponse<>(infrastructureProvisionerService.save(infrastructureProvisioner));
  }

  @PUT
  @Path("{infraProvisionerId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PROVISIONER, action = UPDATE)
  public RestResponse<InfrastructureProvisioner> update(@QueryParam("appId") String appId,
      @PathParam("infraProvisionerId") String infraProvisionerId, InfrastructureProvisioner infrastructureProvisioner) {
    infrastructureProvisioner.setUuid(infraProvisionerId);
    infrastructureProvisioner.setAppId(appId);
    return new RestResponse<>(infrastructureProvisionerService.update(infrastructureProvisioner));
  }

  @GET
  @Path("{infraProvisionerId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PROVISIONER, action = READ)
  public RestResponse<InfrastructureProvisioner> get(
      @QueryParam("appId") @NotEmpty String appId, @PathParam("infraProvisionerId") String infraProvisionerId) {
    return new RestResponse<>(infrastructureProvisionerService.get(appId, infraProvisionerId));
  }

  @POST
  @Path("get-params")
  @Timed
  @ExceptionMetered
  public RestResponse<List<AwsCFTemplateParamsData>> getParamsKeys(@QueryParam("type") @NotEmpty String type,
      @QueryParam("region") @NotEmpty String region, @QueryParam("awsConfigId") @NotEmpty String awsConfigId,
      @QueryParam("appId") @NotEmpty String appId, String data,
      @QueryParam("sourceRepoSettingId") String sourceRepoSettingId, @QueryParam("path") String templatePath,
      @QueryParam("commitId") String commitId, @QueryParam("branch") String sourceRepoBranch,
      @QueryParam("useBranch") Boolean useBranch, @QueryParam("repoName") String repoName) {
    return new RestResponse<>(infrastructureProvisionerService.getCFTemplateParamKeys(type, region, awsConfigId, data,
        appId, sourceRepoSettingId, sourceRepoBranch, templatePath, commitId, useBranch, repoName));
  }

  @DELETE
  @Path("{infraProvisionerId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PROVISIONER, action = DELETE)
  public RestResponse delete(
      @QueryParam("appId") String appId, @PathParam("infraProvisionerId") String infraProvisionerId) {
    infrastructureProvisionerService.delete(appId, infraProvisionerId);
    return new RestResponse();
  }

  @GET
  @Path("terraform-variables")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PROVISIONER, action = READ)
  public RestResponse<List<NameValuePair>> getTerraformVariables(@QueryParam("appId") String appId,
      @QueryParam("sourceRepoSettingId") String scmSettingId, @QueryParam("path") String terraformDirectory,
      @QueryParam("accountId") String accountId, @QueryParam("branch") String sourceRepoBranch,
      @QueryParam("commitId") String commitId, @QueryParam("repoName") String repoName,
      @QueryParam("terraformSourceType") TerraformSourceType terraformSourceType, @QueryParam("s3URI") String s3URI,
      @QueryParam("awsConfigId") String awsConfigId) {
    return new RestResponse<>(infrastructureProvisionerService.getTerraformVariables(appId, scmSettingId,
        terraformDirectory, accountId, sourceRepoBranch, commitId, repoName, terraformSourceType, s3URI, awsConfigId));
  }

  @GET
  @Path("terraform-targets")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PROVISIONER, action = READ)
  public RestResponse<List<String>> getTerraformTargets(@QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @NotNull @QueryParam("provisionerId") String provisionerId) {
    return new RestResponse<>(infrastructureProvisionerService.getTerraformTargets(appId, accountId, provisionerId));
  }

  @GET
  @Path("tf-download-state")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PROVISIONER, action = READ)
  public StreamingOutput downloadTerraformState(@NotEmpty @QueryParam("provisionerId") String provisionerId,
      @NotEmpty @QueryParam("envId") String envId, @QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId) {
    return infrastructureProvisionerService.downloadTerraformState(provisionerId, envId);
  }
}
