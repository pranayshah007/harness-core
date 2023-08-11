/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception;

public interface ErrorMetadataConstants {
  String SAMPLE_ERROR = "Sample";
  String TEMPLATE_INPUTS_ERROR = "TemplateInputsErrorMetadata";
  String YAML_SCHEMA_ERROR = "YamlSchemaErrorWrapperDTO";
  String SCM_ERROR = "ScmErrorMetadataDTO";
  String INPUT_SET_ERROR = "InputSetErrorWrapperDTOPMS";
  String OVERLAY_INPUT_SET_ERROR = "OverlayInputSetErrorWrapperDTOPMS";
  String TEMPLATE_INPUTS_VALIDATION_ERROR = "TemplateInputsErrorMetadataV2";
  String INVALID_FIELDS_ERROR = "InvalidFieldsDTO";
  String FILTER_CREATOR_ERROR = "FilterCreatorResponseError";
  String GIT_ERROR = "GitErrorMetadataDTO";
  String CONNECTOR_VALIDATION_ERROR = "ConnectorValidationErrorMetaData";
}
