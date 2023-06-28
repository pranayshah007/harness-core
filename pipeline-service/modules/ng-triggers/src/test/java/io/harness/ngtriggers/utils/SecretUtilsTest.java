/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.rule.OwnerRule.VINICIUS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.secrets.remote.SecretNGManagerClient;

import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class SecretUtilsTest extends CategoryTest {
  @Mock Call<ResponseDTO<DecryptableEntity>> request;
  @Mock private SecretNGManagerClient secretNGManagerClient;
  @InjectMocks private SecretUtils secretUtils;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testDecrypt() throws Exception {
    GithubTokenSpecDTO githubTokenSpec =
        GithubTokenSpecDTO.builder().tokenRef(SecretRefData.builder().identifier("token").build()).build();
    when(secretNGManagerClient.decryptEncryptedDetails(any(), any())).thenReturn(request);
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(githubTokenSpec)));
    secretUtils.decrypt(githubTokenSpec, Collections.emptyList(), "account", "connector");
    verify(secretNGManagerClient, times(1)).decryptEncryptedDetails(any(), eq("account"));
  }
}
