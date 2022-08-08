/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import software.wings.stencils.OverridingStencil;
import software.wings.stencils.StencilCategory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

/**
 * Created by peeyushaggarwal on 6/28/16.
 */
public class OverridingCommandUnitDescriptor implements OverridingStencil<CommandUnit>, CommandUnitStencil {
  private CommandUnitStencil commandUnitDescriptor;
  private Optional<String> overridingName = Optional.empty();
  private Optional<JsonNode> overridingJsonSchema = Optional.empty();

  /**
   * Instantiates a new Overriding command unit descriptor.
   *
   * @param commandUnitDescriptor the command unit descriptor
   */
  public OverridingCommandUnitDescriptor(CommandUnitStencil commandUnitDescriptor) {
    this.commandUnitDescriptor = commandUnitDescriptor;
  }

  @Override
  public String getType() {
    return commandUnitDescriptor.getType();
  }

  @Override
  @JsonIgnore
  public Class<? extends CommandUnit> getTypeClass() {
    return commandUnitDescriptor.getTypeClass();
  }

  @Override
  public JsonNode getJsonSchema() {
    return overridingJsonSchema.isPresent() ? overridingJsonSchema.get().deepCopy()
                                            : commandUnitDescriptor.getJsonSchema();
  }

  @Override
  public Object getUiSchema() {
    return commandUnitDescriptor.getUiSchema();
  }

  @Override
  public String getName() {
    return overridingName.orElse(commandUnitDescriptor.getName());
  }

  @Override
  public OverridingStencil getOverridingStencil() {
    return commandUnitDescriptor.getOverridingStencil();
  }

  @Override
  public CommandUnit newInstance(String id) {
    return commandUnitDescriptor.newInstance(id);
  }

  @Override
  public JsonNode getOverridingJsonSchema() {
    return overridingJsonSchema.orElse(null);
  }

  @Override
  public void setOverridingJsonSchema(JsonNode overridingJsonSchema) {
    this.overridingJsonSchema = Optional.ofNullable(overridingJsonSchema);
  }

  @Override
  public String getOverridingName() {
    return overridingName.orElse(null);
  }

  @Override
  public void setOverridingName(String overridingName) {
    this.overridingName = Optional.ofNullable(overridingName);
  }

  @Override
  public StencilCategory getStencilCategory() {
    return commandUnitDescriptor == null ? null : commandUnitDescriptor.getStencilCategory();
  }

  @Override
  public Integer getDisplayOrder() {
    return commandUnitDescriptor == null ? Integer.valueOf(DEFAULT_DISPLAY_ORDER)
                                         : commandUnitDescriptor.getDisplayOrder();
  }

  @Override
  public boolean matches(Object context) {
    return commandUnitDescriptor.matches(context);
  }
}
