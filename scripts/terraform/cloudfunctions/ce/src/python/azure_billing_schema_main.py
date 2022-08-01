# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

"""
CF3 for Azure data pipeline
Helps in generating schema dynamically
"""
import json
import base64
import os
import io
from google.cloud import bigquery
from google.cloud import storage
import util
from bigquery_schema_generator.generate_schema import SchemaGenerator
from util import print_


"""
Event format:
{
	"accountId": "vj6LeqxiSWyVbxKrHnGm3g",
	"path": "vj6LeqxiSWyVbxKrHnGm3g/BILLINGACCOUNT/38f326c0-8eee-4e70-a6a4-a6fcb51a95cd/BILLINGACCOUNT/20220601-20220630",
	"bucket": "azurecustomerbillingdata-dev",
	"cloudProvider": "AZURE",
	"tenant_id": "38f326c0-8eee-4e70-a6a4-a6fcb51a95cd",
	"is_partitioned_csv": false,
	"reportYear": "2022",
	"reportMonth": "06",
	"connectorId": "BILLINGACCOUNT",
	"datasetName": "BillingReport_vj6leqxiswyvbxkrhngm3g",
	"tableSuffix": "2022_06_BILLINGACCOUNT",
	"tableName": "azureBilling_2022_06_BILLINGACCOUNT",
	"tableId": "ccm-play.BillingReport_vj6leqxiswyvbxkrhngm3g.azureBilling_2022_06_BILLINGACCOUNT",
	"uri": "gs://azurecustomerbillingdata-dev/vj6LeqxiSWyVbxKrHnGm3g/BILLINGACCOUNT/38f326c0-8eee-4e70-a6a4-a6fcb51a95cd/BILLINGACCOUNT/20220601-20220630/BILLINGACCOUNT_162df3cb-e4c5-432a-8d65-427426eca653.csv",
	"csvtoingest": "vj6LeqxiSWyVbxKrHnGm3g/BILLINGACCOUNT/38f326c0-8eee-4e70-a6a4-a6fcb51a95cd/BILLINGACCOUNT/20220601-20220630/BILLINGACCOUNT_162df3cb-e4c5-432a-8d65-427426eca653.csv"
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
client = bigquery.Client(PROJECTID)
storage_client = storage.Client(PROJECTID)

def main(event, context):
    """Triggered from a message on a Cloud Pub/Sub topic.
    Args:
         event (dict): Event payload.
         context (google.cloud.functions.Context): Metadata for the event.
    """
    print(event)
    data = base64.b64decode(event['data']).decode('utf-8')
    jsonData = json.loads(data)
    print(jsonData)

    # Set the accountId for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountId")
    generate_schema_dynamically(jsonData)


def generate_schema_dynamically(jsonData):
    try:
        blobs = storage_client.list_blobs(
            jsonData["bucket"], prefix=jsonData["csvtoingest"])
        for blob in blobs:
            # To avoid picking up sub directory json files
            print_(blob.name)
            localname = '/tmp/%s' % blob.name.split("/")[-1]
            print_(localname)
            blob.download_to_filename(localname)
            print_("saved in %s" % localname)
            o = io.StringIO()
            with open(localname, 'r') as f:
                print_("Generating schema...")
                generator = SchemaGenerator(
                    input_format='csv'
                )
                generator.run(input_file=f, output_file=o)
                o.seek(0)
                j = o.read()
                j = json.loads(j)
                o.close()
                if len(j) == 0:
                    print_("No schema generated. Please try manually")
                    return
                print_("Generated schema")
                # Sanitize the file
                # It is noticed that first schema entry is corrupt.
                j[0]["name"] = j[0]["name"].replace("\ufeff","")
                # Handle for other schema entries
                for column in j:
                    if column.get("name") in ["\ufeffinvoiceId", "\ufeffInvoiceSectionName"] :
                        column["name"] = column["name"].replace("\ufeff","")
                        print_("Sanitized the schema")
                        break

                print_(j)
                schema = []
                for column in j:
                    schema.append(bigquery.SchemaField(column["name"], column["type"], column["mode"]))
                # Create table
                try:
                    if len(schema) != 0:
                        client.delete_table(jsonData["tableId"], not_found_ok=True)
                        table = client.create_table(bigquery.Table(jsonData["tableId"], schema=schema))
                        print_("Created table {}.{}.{}".format(table.project, table.dataset_id,
                                                              table.table_id))
                    else:
                        print_("No table to create")
                except Exception as e:
                    print_("Error while creating table\n {}".format(e), "ERROR")
                    raise
            break
    except Exception as e:
        print_("Error while generating schema\n {}".format(e), "ERROR")
        raise