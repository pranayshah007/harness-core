/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.persistence.AppContainer;

/**
 * PlatformService.
 *
 * @author Rishi
 */
public interface PlatformService {
  /**
   * List.
   *
   * @param req the req
   * @return the page response
   */
  PageResponse<AppContainer> list(PageRequest<AppContainer> req);

  /**
   * Creates the.
   *
   * @param platform the platform
   * @return the app container
   */
  AppContainer create(AppContainer platform);

  /**
   * Update.
   *
   * @param platform the platform
   * @return the app container
   */
  AppContainer update(AppContainer platform);
}
