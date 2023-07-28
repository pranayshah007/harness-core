/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.ModuleType;
import io.harness.SchemaCacheKey;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.BranchFilterDelegateTaskParams;
import io.harness.beans.InputSetValidatorType;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.RepoFilterDelegateTaskParams;
import io.harness.beans.WebhookEncryptedSecretDTO;
import io.harness.cd.CDLicenseType;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.cvng.beans.appd.AppDynamicsApplication;
import io.harness.cvng.beans.appd.AppDynamicsTier;
import io.harness.encryption.SecretRefData;
import io.harness.exception.FilterCreatorException;
import io.harness.exception.JsonSchemaValidationException;
import io.harness.exception.PlanCreatorException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.http.HttpHeaderConfig;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.Status;
import io.harness.ng.core.common.beans.KeyValuePair;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.request.HttpRequestInfo;
import io.harness.request.RequestContext;
import io.harness.request.RequestContextData;
import io.harness.request.RequestMetadata;
import io.harness.serializer.KryoRegistrar;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.yaml.core.timeout.Timeout;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(PL)
public class NGCommonsKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    // keeping ids same
    kryo.register(ParameterField.class, 35001);
    kryo.register(InputSetValidator.class, 35002);
    kryo.register(InputSetValidatorType.class, 35008);

    kryo.register(NGTag.class, 22001);
    kryo.register(BaseNGAccess.class, 54324);
    kryo.register(SecretRefData.class, 3003);
    kryo.register(ErrorDetail.class, 54325);
    kryo.register(ConnectorValidationResult.class, 19059);
    kryo.register(ConnectivityStatus.class, 19458);
    kryo.register(ResponseDTO.class, 19459);
    kryo.register(Status.class, 19460);
    kryo.register(ErrorDTO.class, 19461);

    kryo.register(KeyValuePair.class, 22004);
    kryo.register(ResourceScope.class, 22005);
    kryo.register(AccountScope.class, 22006);
    kryo.register(OrgScope.class, 22007);
    kryo.register(ProjectScope.class, 22008);
    kryo.register(Resource.class, 22009);
    kryo.register(HttpRequestInfo.class, 22010);
    kryo.register(RequestMetadata.class, 22011);
    kryo.register(ModuleType.class, 22012);

    kryo.register(HttpHeaderConfig.class, 19462);

    kryo.register(RequestContextData.class, 970001);
    kryo.register(RequestContext.class, 970002);
    kryo.register(GitSyncBranchContext.class, 970003);
    kryo.register(GitEntityInfo.class, 970004);

    kryo.register(JsonSchemaValidationException.class, 970005);
    kryo.register(FilterCreatorException.class, 970006);
    kryo.register(PlanCreatorException.class, 970007);
    kryo.register(ServiceAccountDTO.class, 970008);
    kryo.register(SchemaCacheKey.class, 970009);
    kryo.register(StoreType.class, 970010);

    kryo.register(AppDynamicsApplication.class, 9020);
    kryo.register(AppDynamicsTier.class, 9021);

    kryo.register(CDLicenseType.class, 930010);
    kryo.register(PageRequestDTO.class, 5216);
    kryo.register(Timeout.class, 9300122);
    kryo.register(WebhookEncryptedSecretDTO.class, 9300123);
    kryo.register(RepoFilterDelegateTaskParams.class, 9300130);
    kryo.register(BranchFilterDelegateTaskParams.class, 9300131);
  }
}
