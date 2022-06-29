/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ContainerTaskCommons {
  static final String DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX = "\\$\\{DOCKER_IMAGE_NAME}";
  static final String DOCKER_IMAGE_NAME_REGEX = "(\\s*\"?image\"?\\s*:\\s*\"?)";
  static final String CONTAINER_NAME_PLACEHOLDER_REGEX = "\\$\\{CONTAINER_NAME}";

  static final String DUMMY_DOCKER_IMAGE_NAME = "hv--docker-image-name--hv";
  static final String DUMMY_CONTAINER_NAME = "hv--container-name--hv";

  public Pattern compileRegexPattern(String domainName) {
    return Pattern.compile(
        DOCKER_IMAGE_NAME_REGEX + "(" + Pattern.quote(domainName) + "\\/)" + DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX);
  }
}
