/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static org.joor.Reflect.on;

import io.harness.exception.WingsException;
import io.harness.serializer.JsonUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.OverridingContainerTaskTypeDescriptor;
import software.wings.stencils.OverridingStencil;
import software.wings.stencils.StencilCategory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.net.URL;
import java.util.HashMap;

/**
 * Created by anubhaw on 2/6/17.
 */
public enum ContainerTaskType implements ContainerTaskTypeDescriptor {
  ECS(EcsContainerTask.class, DeploymentType.ECS.name()),
  KUBERNETES(KubernetesContainerTask.class, DeploymentType.KUBERNETES.name());

  private static final String stencilsPath = "/templates/containertasks/";
  private static final String uiSchemaSuffix = "-ContainerTaskUISchema.json";

  private Object uiSchema;
  private JsonNode jsonSchema;

  @JsonIgnore private Class<? extends ContainerTask> containerTaskClass;
  @JsonIgnore private String name;

  ContainerTaskType(Class<? extends ContainerTask> containerTaskClass, String name) {
    this.containerTaskClass = containerTaskClass;
    this.name = name;
    try {
      uiSchema = readResource(stencilsPath + name() + uiSchemaSuffix);
    } catch (Exception e) {
      uiSchema = new HashMap<String, String>();
    }
    jsonSchema = JsonUtils.jsonSchema(containerTaskClass);
  }

  private Object readResource(String file) {
    try {
      URL url = this.getClass().getResource(file);
      String json = Resources.toString(url, Charsets.UTF_8);
      return JsonUtils.asObject(json, HashMap.class);
    } catch (Exception exception) {
      throw new WingsException("Error in initializing CommandUnitType-" + file, exception);
    }
  }

  @Override
  public String getType() {
    return name();
  }

  @Override
  public Class<? extends ContainerTask> getTypeClass() {
    return containerTaskClass;
  }

  @Override
  public StencilCategory getStencilCategory() {
    return StencilCategory.CONFIGURATIONS;
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
    return new OverridingContainerTaskTypeDescriptor(this);
  }

  @Override
  public Integer getDisplayOrder() {
    return DEFAULT_DISPLAY_ORDER;
  }

  @Override
  public ContainerTask newInstance(String id) {
    return on(containerTaskClass).create().get();
  }

  @Override
  public boolean matches(Object context) {
    return true;
  }
}
