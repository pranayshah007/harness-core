/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.joor.Reflect.on;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;
import io.harness.serializer.JsonUtils;

import software.wings.api.DeploymentType;
import software.wings.stencils.OverridingStencil;
import software.wings.stencils.StencilCategory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

/**
 * The enum Infra mapping type.
 */
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public enum InfrastructureMappingType implements InfrastructureMappingDescriptor {
  /**
   * Physical data center ssh infra mapping type.
   */
  PHYSICAL_DATA_CENTER_SSH(PhysicalInfrastructureMapping.class, "PHYSICAL_DATA_CENTER_SSH", StencilCategory.OTHERS, 1,
      Lists.newArrayList(DeploymentType.SSH)),
  /**
   * Aws ssh infra mapping type.
   */
  AWS_SSH(AwsInfrastructureMapping.class, "AWS_SSH", StencilCategory.OTHERS, 2, Lists.newArrayList(DeploymentType.SSH)),

  /**
   * Aws ami infrastructure mapping type.
   */
  AWS_AMI(
      AwsAmiInfrastructureMapping.class, "AWS_AMI", StencilCategory.OTHERS, 2, Lists.newArrayList(DeploymentType.AMI)),
  /**
   * Aws CodeDeploy infra mapping type.
   */
  AWS_AWS_CODEDEPLOY(CodeDeployInfrastructureMapping.class, "AWS_AWS_CODEDEPLOY", StencilCategory.OTHERS, 3,
      Lists.newArrayList(DeploymentType.AWS_CODEDEPLOY)),
  /**
   * Aws ecs infra mapping type.
   */
  AWS_ECS(EcsInfrastructureMapping.class, "AWS_ECS", StencilCategory.OTHERS, 4, Lists.newArrayList(DeploymentType.ECS)),

  /**
   * Direct connection to kubernetes infra mapping type.
   */
  DIRECT_KUBERNETES(DirectKubernetesInfrastructureMapping.class, "DIRECT_KUBERNETES", StencilCategory.OTHERS, 5,
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM)),
  /**
   * Gcp kubernetes infra mapping type.
   */
  GCP_KUBERNETES(GcpKubernetesInfrastructureMapping.class, "GCP_KUBERNETES", StencilCategory.OTHERS, 6,
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM)),
  /**
   * Aws aws lambda infrastructure mapping type.
   */
  AWS_AWS_LAMBDA(AwsLambdaInfraStructureMapping.class, "AWS_AWS_LAMBDA", StencilCategory.OTHERS, 7,
      Lists.newArrayList(DeploymentType.AWS_LAMBDA)),
  /**
   * Azure kubernetes [AKS] infra mapping type.
   */
  AZURE_KUBERNETES(AzureKubernetesInfrastructureMapping.class, "AZURE_KUBERNETES", StencilCategory.OTHERS, 8,
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM)),

  PHYSICAL_DATA_CENTER_WINRM(PhysicalInfrastructureMappingWinRm.class, "PHYSICAL_DATA_CENTER_WINRM",
      StencilCategory.OTHERS, 9, Lists.newArrayList(DeploymentType.WINRM)),

  PCF_PCF(
      PcfInfrastructureMapping.class, "PCF_PCF", StencilCategory.OTHERS, 10, Lists.newArrayList(DeploymentType.PCF)),

  AZURE_INFRA(AzureInfrastructureMapping.class, "AZURE", StencilCategory.OTHERS, 11,
      Lists.newArrayList(DeploymentType.SSH, DeploymentType.WINRM)),

  AZURE_VMSS(AzureVMSSInfrastructureMapping.class, "AZURE_VMSS", StencilCategory.OTHERS, 12,
      Lists.newArrayList(DeploymentType.AZURE_VMSS)),

  CUSTOM(CustomInfrastructureMapping.class, "CUSTOM", StencilCategory.OTHERS, 13,
      Lists.newArrayList(DeploymentType.CUSTOM)),

  AZURE_WEBAPP(AzureWebAppInfrastructureMapping.class, "AZURE_WEBAPP", StencilCategory.OTHERS, 14,
      Lists.newArrayList(DeploymentType.AZURE_WEBAPP)),

  RANCHER_KUBERNETES(RancherKubernetesInfrastructureMapping.class, "RANCHER_KUBERNETES", StencilCategory.OTHERS, 15,
      Lists.newArrayList(DeploymentType.KUBERNETES));

  private static final String stencilsPath = "/templates/inframapping/";
  private static final String uiSchemaSuffix = "-InfraMappingUISchema.json";

  private Object uiSchema;
  private JsonNode jsonSchema;

  @JsonIgnore private Class<? extends InfrastructureMapping> infrastructureMappingClass;
  @JsonIgnore private String name;

  private StencilCategory stencilCategory;
  private Integer displayOrder;
  private List<DeploymentType> deploymentTypes;

  InfrastructureMappingType(Class<? extends InfrastructureMapping> infrastructureMappingClass, String name,
      StencilCategory stencilCategory, Integer displayOrder, List<DeploymentType> deploymentTypes) {
    this.infrastructureMappingClass = infrastructureMappingClass;
    this.name = name;
    this.stencilCategory = stencilCategory;
    this.displayOrder = displayOrder;
    this.deploymentTypes = deploymentTypes;
    try {
      uiSchema = readResource(stencilsPath + name() + uiSchemaSuffix);
    } catch (Exception e) {
      uiSchema = new HashMap<String, String>();
    }
    jsonSchema = JsonUtils.jsonSchema(infrastructureMappingClass);
  }

  @Override
  public String getType() {
    return getName();
  }

  @Override
  public Class<? extends InfrastructureMapping> getTypeClass() {
    return infrastructureMappingClass;
  }

  @Override
  public StencilCategory getStencilCategory() {
    return stencilCategory;
  }

  @Override
  public JsonNode getJsonSchema() {
    return jsonSchema;
  }

  @Override
  public Object getUiSchema() {
    return uiSchema;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public OverridingStencil getOverridingStencil() {
    return new OverridingInfrastructureMappingType(this);
  }

  @Override
  public Integer getDisplayOrder() {
    return displayOrder;
  }

  public List<DeploymentType> getDeploymentTypes() {
    return deploymentTypes;
  }

  @Override
  public InfrastructureMapping newInstance(String id) {
    return on(infrastructureMappingClass).create().get();
  }

  @Override
  public boolean matches(Object context) {
    return true;
  }

  private Object readResource(String file) {
    try {
      URL url = this.getClass().getResource(file);
      String json = Resources.toString(url, Charsets.UTF_8);
      return JsonUtils.asObject(json, HashMap.class);
    } catch (Exception exception) {
      throw new WingsException("Error in initializing InfrastructureMapping-" + file, exception);
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("uiSchema", uiSchema)
        .add("jsonSchema", jsonSchema)
        .add("infrastructureMappingClass", infrastructureMappingClass)
        .add("name", name)
        .add("stencilCategory", stencilCategory)
        .add("displayOrder", displayOrder)
        .add("type", getType())
        .add("typeClass", getTypeClass())
        .add("overridingStencil", getOverridingStencil())
        .toString();
  }
}
