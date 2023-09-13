/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
@OwnedBy(PL)
public class PublicAccessUtils {
  public static final String ALL_USERS = "ALL_USERS";
  public static final String ALL_AUTHENTICATED_USERS = "ALL_AUTHENTICATED_USERS";
  public static final String PUBLIC_RESOURCE_GROUP_IDENTIFIER = "_public_resources";
  public static final String PUBLIC_RESOURCE_GROUP_NAME = "Public Resource Group";

  private static final String PUBLIC_ACCESS_CONFIG_FILE_PATH =
      "io/harness/accesscontrol/resources/public-access-assignment-mapping.yml";
  public static boolean isPrincipalPublic(String identifier) {
    return ALL_USERS.equals(identifier) || ALL_AUTHENTICATED_USERS.equals(identifier);
  }

  public static boolean isResourceGroupPublic(String identifier) {
    return PUBLIC_RESOURCE_GROUP_IDENTIFIER.equals(identifier);
  }

  public List<PublicAccessRoleAssignmentMapping> getPublicAccessRoleAssignmentMapping() {
    PublicAccessRoleAssignmentConfig config;

    ObjectMapper om = new ObjectMapper(new YAMLFactory());
    try {
      URL url = getClass().getClassLoader().getResource(PUBLIC_ACCESS_CONFIG_FILE_PATH);
      byte[] bytes = Resources.toByteArray(url);
      config = om.readValue(bytes, PublicAccessRoleAssignmentConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("File path or format is invalid");
    }
    return config.getPublicAccessRoleAssignmentMapping();
  }
}
