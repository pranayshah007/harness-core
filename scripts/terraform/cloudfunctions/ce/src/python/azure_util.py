# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

from google.cloud import secretmanager
import os

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')

def get_secret_key(secret_name):
    client = secretmanager.SecretManagerServiceClient()
    request = {"name": f"projects/{PROJECTID}/secrets/{secret_name}/versions/latest"}
    response = client.access_secret_version(request)
    secret_string = response.payload.data.decode("UTF-8")
    return secret_string
