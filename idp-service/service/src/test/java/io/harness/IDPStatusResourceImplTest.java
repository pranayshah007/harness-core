/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.status.beans.StatusEnum;
import io.harness.idp.status.resources.IDPStatusResourceImpl;
import io.harness.idp.status.response.IDPStatusDTO;
import io.harness.ng.core.dto.ResponseDTO;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IDPStatusResourceImplTest {
  @InjectMocks private IDPStatusResourceImpl idpStatusResource;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Category(UnitTests.class)
  public void testGetIDPStatus() {
    ResponseDTO<IDPStatusDTO> responseDTO = idpStatusResource.getIDPStatus("123");
    Assert.assertEquals(responseDTO.getData().getStatus(), StatusEnum.SUCCESS.toString());
  }
}
