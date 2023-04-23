/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.aws.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.aws.service.AwsResourceServiceImpl;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.AwsBaseInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.AwsListInstancesFilterDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.utils.IdentifierRefHelper;

import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.impl.aws.model.AwsEC2Instance;
import software.wings.service.impl.aws.model.AwsVPC;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Api("aws")
@Path("/aws/aws-helper")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AwsHelperResource {
  private final AwsResourceServiceImpl awsHelperService;
  private final InfrastructureEntityService infrastructureEntityService;
  private final ArtifactResourceUtils artifactResourceUtils;

  @GET
  @Path("regions")
  @ApiOperation(value = "Get all the AWS regions defined in the application", nickname = "RegionsForAws")
  public ResponseDTO<Map<String, String>> listRegions() {
    return ResponseDTO.newResponse(awsHelperService.getRegions());
  }

  @POST
  @Path("cf-parameters")
  @ApiOperation(value = "Get Cloudformation parameters from a template", nickname = "CFParametersForAws")
  public ResponseDTO<List<AwsCFTemplateParamsData>> listCFParameterKeys(@QueryParam("type") @NotNull String type,
      @QueryParam("region") @NotNull String region, @QueryParam("isBranch") boolean isBranch,
      @QueryParam("branch") String branch, @QueryParam("filePath") String templatePath,
      @QueryParam("commitId") String commitId, @QueryParam("awsConnectorRef") @NotNull String awsConnectorRef,
      @QueryParam("gitConnectorRef") String gitConnectorRefParam, @QueryParam("repoName") String repoName,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @NotNull String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @NotNull String projectIdentifier, String data) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);

    List<AwsCFTemplateParamsData> keys =
        awsHelperService.getCFparametersKeys(type, region, isBranch, branch, repoName, templatePath, commitId,
            connectorRef, data, gitConnectorRefParam, accountIdentifier, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(keys);
  }

  @GET
  @Path("cf-capabilities")
  @ApiOperation(value = "Get the Cloudformation capabilities", nickname = "CFCapabilitiesForAws")
  public ResponseDTO<List<String>> listCFCapabilities() {
    return ResponseDTO.newResponse(awsHelperService.getCapabilities());
  }

  @GET
  @Path("cf-states")
  @ApiOperation(value = "Get all the Cloudformation states for a stack", nickname = "CFStatesForAws")
  public ResponseDTO<Set<String>> listCFStates() {
    return ResponseDTO.newResponse(awsHelperService.getCFStates());
  }

  @GET
  @Path("iam-roles")
  @ApiOperation(value = "Get all the IAM roles", nickname = "getIamRolesForAws")
  public ResponseDTO<Map<String, String>> listIamRoles(@NotNull @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("region") String region) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        awsHelperService.getRolesARNs(connectorRef, orgIdentifier, projectIdentifier, region));
  }

  @GET
  @Path("hosts")
  @ApiOperation(value = "Get all the IAM hosts", nickname = "filterHosts")
  public ResponseDTO<List<String>> filterHosts(@NotNull @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(required = true, description = "Filter body") @Valid AwsListInstancesFilterDTO filterDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    List<AwsEC2Instance> instances = awsHelperService.filterHosts(connectorRef, filterDTO.isWinRm(),
        filterDTO.getRegion(), filterDTO.getVpcIds(), filterDTO.getTags(), filterDTO.getAutoScalingGroupName());
    List<String> result = CollectionUtils.emptyIfNull(instances)
                              .stream()
                              .map(AwsEC2Instance::getPublicDnsName)
                              .collect(Collectors.toList());
    return ResponseDTO.newResponse(result);
  }

  @GET
  @Path("vpcs")
  @ApiOperation(value = "Get all the vpcs", nickname = "vpcs")
  public ResponseDTO<List<AwsVPC>> getVpcs(@NotNull @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("region") String region) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(awsHelperService.getVPCs(connectorRef, orgIdentifier, projectIdentifier, region));
  }

  @GET
  @Path("tags")
  @ApiOperation(value = "Get all the tags", nickname = "tags")
  public ResponseDTO<Set<String>> getTags(@NotNull @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("region") String region) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    Map<String, String> tags = awsHelperService.getTags(connectorRef, orgIdentifier, projectIdentifier, region);
    return ResponseDTO.newResponse(tags.keySet());
  }

  @GET
  @Path("v2/tags")
  @ApiOperation(value = "Get all the tags V2", nickname = "tagsV2")
  public ResponseDTO<Set<String>> getTagsV2(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("region") String region,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((SshWinRmAwsInfrastructure) spec).getRegion().getValue();
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    Map<String, String> tags = awsHelperService.getTags(connectorRef, orgIdentifier, projectIdentifier, region);
    return ResponseDTO.newResponse(tags.keySet());
  }

  @GET
  @Path("load-balancers")
  @ApiOperation(value = "Get load balancers", nickname = "loadBalancers")
  public ResponseDTO<List<String>> getLoadBalancers(@NotNull @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("region") String region) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        awsHelperService.getLoadBalancers(connectorRef, orgIdentifier, projectIdentifier, region));
  }

  @GET
  @Path("auto-scaling-groups")
  @ApiOperation(value = "Get auto scaling groups", nickname = "autoScalingGroups")
  public ResponseDTO<List<String>> getASGNames(@NotNull @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("region") String region) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        awsHelperService.getASGNames(connectorRef, orgIdentifier, projectIdentifier, region));
  }

  @GET
  @Path("clusters")
  @ApiOperation(value = "Get clusters", nickname = "clusters")
  public ResponseDTO<List<String>> getClusterNames(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((EcsInfrastructure) spec).getRegion().getValue();
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        awsHelperService.getClusterNames(connectorRef, orgIdentifier, projectIdentifier, region));
  }

  @GET
  @Path("elastic-load-balancers")
  @ApiOperation(value = "Get elastic load balancers", nickname = "elastic load balancers")
  public ResponseDTO<List<String>> getElasticLoadBalancers(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("region") String region,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((AwsBaseInfrastructure) spec).getRegion().getValue();
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        awsHelperService.getElasticLoadBalancerNames(connectorRef, orgIdentifier, projectIdentifier, region));
  }

  @GET
  @Path("listeners")
  @ApiOperation(value = "Get elastic load balancer listeners ", nickname = "listeners")
  public ResponseDTO<Map<String, String>> getElasticLoadBalancerListenersArn(
      @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("region") String region, @NotNull @QueryParam("elasticLoadBalancer") String elasticLoadBalancer,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((AwsBaseInfrastructure) spec).getRegion().getValue();
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(awsHelperService.getElasticLoadBalancerListenersArn(
        connectorRef, orgIdentifier, projectIdentifier, region, elasticLoadBalancer));
  }

  @GET
  @Path("listener-rules-arns")
  @ApiOperation(value = "Get elastic load balancer listener rules", nickname = "listener rules")
  public ResponseDTO<List<String>> getElasticLoadBalancerListenerRules(
      @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("region") String region, @NotNull @QueryParam("elasticLoadBalancer") String elasticLoadBalancer,
      @NotNull @QueryParam("listenerArn") String listenerArn,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((AwsBaseInfrastructure) spec).getRegion().getValue();
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(awsHelperService.getElasticLoadBalancerListenerRules(
        connectorRef, orgIdentifier, projectIdentifier, region, elasticLoadBalancer, listenerArn));
  }

  private InfrastructureDefinitionConfig getInfrastructureDefinitionConfig(
      String accountId, String orgIdentifier, String projectIdentifier, String envId, String infraDefinitionId) {
    if (isEmpty(envId)) {
      throw new InvalidRequestException(
          String.valueOf(format("%s must be provided", NGCommonEntityConstants.ENVIRONMENT_KEY)));
    }

    if (isEmpty(infraDefinitionId)) {
      throw new InvalidRequestException(
          String.valueOf(format("%s must be provided", NGCommonEntityConstants.INFRA_DEFINITION_KEY)));
    }

    InfrastructureEntity infrastructureEntity =
        infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId)
            .orElseThrow(() -> {
              throw new NotFoundException(String.format(
                  "Infrastructure with identifier [%s] in project [%s], org [%s], environment [%s] not found",
                  infraDefinitionId, projectIdentifier, orgIdentifier, envId));
            });

    return InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureEntity)
        .getInfrastructureDefinitionConfig();
  }

  @GET
  @Path("eks/clusters")
  @ApiOperation(value = "Get EKS clusters list", nickname = "getEKSClusterNames")
  public ResponseDTO<List<String>> getEKSClusterNames(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;

    if (isEmpty(awsConnectorRef)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }
    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(awsHelperService.getEKSClusterNames(connectorRef, orgIdentifier, projectIdentifier));
  }
}
