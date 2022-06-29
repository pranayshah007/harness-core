# bucket for cloudfunctions zip storage
resource "google_storage_bucket" "bucket1" {
  name = "ce-functions-deploy-${var.deployment}"
  location = "us"
  project = "${var.projectId}"
}

# PubSub topic for AWS data pipeline. scheduler pushes into this
resource "google_pubsub_topic" "ce-awsdata-topic" {
  name = "ce-awsdata-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for AWS NG data pipeline. AWS GCS data transfer job ingests into this
resource "google_pubsub_topic" "ce-aws-billing-gcs-topic" {
  name = "ce-aws-billing-gcs"
  project = "${var.projectId}"
}

# PubSub topic for AWS NG data pipeline. ce-aws-billing-gcs CF pushes into this
resource "google_pubsub_topic" "ce-aws-billing-cf-topic" {
  name = "ce-aws-billing-cf"
  project = "${var.projectId}"
}

# PubSub topic for AWS EC2 Inventory data pipeline. scheduler pushes into this
resource "google_pubsub_topic" "ce-awsdata-ec2-inventory-topic" {
  name = "ce-awsdata-ec2-inventory-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for AWS EC2 Inventory data pipeline. scheduler pushes into this
resource "google_pubsub_topic" "ce-awsdata-ec2-inventory-load-topic" {
  name = "ce-awsdata-ec2-inventory-load-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for AWS EC2 Inventory metric data pipeline. scheduler pushes into this
resource "google_pubsub_topic" "ce-awsdata-ec2-metric-topic" {
  name = "ce-awsdata-ec2-metric-inventory-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for AWS EBS Inventory data pipeline. scheduler pushes into this
resource "google_pubsub_topic" "ce-awsdata-ebs-inventory-topic" {
  name = "ce-awsdata-ebs-inventory-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for loading AWS EBS Inventory data into main bq table. scheduler pushes into this
resource "google_pubsub_topic" "ce-awsdata-ebs-inventory-load-topic" {
  name = "ce-awsdata-ebs-inventory-load-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for AWS EBS Inventory Metrics data pipeline. scheduler pushes into this
resource "google_pubsub_topic" "ce-awsdata-ebs-metrics-topic" {
  name = "ce-awsdata-ebs-metrics-inventory-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for AWS RDS Inventory data pipeline. scheduler pushes into this
resource "google_pubsub_topic" "ce-awsdata-rds-inventory-topic" {
  name = "ce-awsdata-rds-inventory-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for AWS RDS Inventory data pipeline. scheduler pushes into this
resource "google_pubsub_topic" "ce-awsdata-rds-inventory-load-topic" {
  name = "ce-awsdata-rds-inventory-load-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for AWS Connector CRUD events init. ce-nextgen pushes into this
resource "google_pubsub_topic" "ce-aws-connector-crud-topic" {
  name = "ce-aws-connector-crud"
  project = "${var.projectId}"
}

# PubSub topic for GCP Connector CRUD events init. ce-nextgen pushes into this
resource "google_pubsub_topic" "ce-gcp-connector-crud-topic" {
  name = "ce-gcp-connector-crud"
  project = "${var.projectId}"
}

# PubSub topic for AZURE Connector CRUD events init. ce-nextgen pushes into this
resource "google_pubsub_topic" "ce-azure-connector-crud-topic" {
  name = "ce-azure-connector-crud"
  project = "${var.projectId}"
}

# PubSub topic for AZURE data pipeline. CF1 pushes into this
resource "google_pubsub_topic" "ce-azure-billing-cf-topic" {
  name = "ce-azure-billing-cf"
  project = "${var.projectId}"
}

# PubSub topic for AZURE data pipeline. Azure GCS data transfer job ingests into this
resource "google_pubsub_topic" "ce-azure-billing-gcs-topic" {
  name = "ce-azure-billing-gcs"
  project = "${var.projectId}"
}

# PubSub topic for GCP NG data pipeline. Batch and CF pushes into this
resource "google_pubsub_topic" "ce-gcp-billing-cf-topic" {
  name = "ce-gcp-billing-cf"
  project = "${var.projectId}"
}

# PubSub topic for GCP Instance Inventory data pipeline. scheduler pushes into this
resource "google_pubsub_topic" "ce-gcp-instance-inventory-data-topic" {
  name = "ce-gcp-instance-inventory-data-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for loading GCP Instance Inventory data into main bq table. scheduler pushes into this
resource "google_pubsub_topic" "ce-gcp-instance-inventory-data-load-topic" {
  name = "ce-gcp-instance-inventory-data-load-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for GCP Disk Inventory data pipeline. scheduler pushes into this
resource "google_pubsub_topic" "ce-gcp-disk-inventory-data-topic" {
  name = "ce-gcp-disk-inventory-data-scheduler"
  project = "${var.projectId}"
}

# PubSub topic for loading GCP Disk Inventory data into main bq table. scheduler pushes into this
resource "google_pubsub_topic" "ce-gcp-disk-inventory-data-load-topic" {
  name = "ce-gcp-disk-inventory-data-load-scheduler"
  project = "${var.projectId}"
}

# Archive files keep the CF files and dependencies
data "archive_file" "ce-clusterdata" {
  type        = "zip"
  output_path = "${path.module}/files/ce-clusterdata.zip"
  source {
    content  = "${file("${path.module}/src/python/clusterdata_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-gcpdata" {
  type        = "zip"
  output_path = "${path.module}/files/ce-gcpdata.zip"
  source {
    content  = "${file("${path.module}/src/python/gcpdata_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-gcp-billing-bq" {
  type        = "zip"
  output_path = "${path.module}/files/ce-gcp-billing-bq.zip"
  source {
    content  = "${file("${path.module}/src/python/gcp_billing_bq_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-aws-billing-bq" {
  type        = "zip"
  output_path = "${path.module}/files/ce-aws-billing-bq.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_billing_bq_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-aws-billing-gcs" {
  type        = "zip"
  output_path = "${path.module}/files/ce-aws-billing-gcs.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_billing_gcs_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-aws-inventory-init" {
  type        = "zip"
  output_path = "${path.module}/files/ce-aws-inventory-init.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_inventory_init_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-gcp-inventory-init" {
  type        = "zip"
  output_path = "${path.module}/files/ce-gcp-inventory-init.zip"
  source {
    content  = "${file("${path.module}/src/python/gcp_inventory_init_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-azure-inventory-init" {
  type        = "zip"
  output_path = "${path.module}/files/ce-azure-inventory-init.zip"
  source {
    content  = "${file("${path.module}/src/python/azure_inventory_init_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-azure-billing-gcs" {
  type        = "zip"
  output_path = "${path.module}/files/ce-azure-billing-gcs.zip"
  source {
    content  = "${file("${path.module}/src/python/azure_billing_gcs_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-azure-billing-bq" {
  type        = "zip"
  output_path = "${path.module}/files/ce-azure-billing-bq.zip"
  source {
    content  = "${file("${path.module}/src/python/azure_billing_bq_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-awsdata-ec2" {
  type        = "zip"
  output_path = "${path.module}/files/ce-awsdata-ec2.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_ec2_data_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/aws_util.py")}"
    filename = "aws_util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-awsdata-ec2-load" {
  type        = "zip"
  output_path = "${path.module}/files/ce-awsdata-ec2-load.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_ec2_data_load.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-awsdata-ec2-metric" {
  type        = "zip"
  output_path = "${path.module}/files/ce-awsdata-ec2-metric.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_ec2_metric_data_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/aws_util.py")}"
    filename = "aws_util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-awsdata-ebs" {
  type        = "zip"
  output_path = "${path.module}/files/ce-awsdata-ebs.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_ebs_data_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/aws_util.py")}"
    filename = "aws_util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-awsdata-ebs-load" {
  type        = "zip"
  output_path = "${path.module}/files/ce-awsdata-ebs-load.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_ebs_data_load.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-awsdata-ebs-metrics" {
  type        = "zip"
  output_path = "${path.module}/files/ce-awsdata-ebs-metrics.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_ebs_metrics_data_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/aws_util.py")}"
    filename = "aws_util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-awsdata-rds" {
  type        = "zip"
  output_path = "${path.module}/files/ce-awsdata-rds.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_rds_data_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/aws_util.py")}"
    filename = "aws_util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-awsdata-rds-load" {
  type        = "zip"
  output_path = "${path.module}/files/ce-awsdata-rds-load.zip"
  source {
    content  = "${file("${path.module}/src/python/aws_rds_data_load.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-gcp-instance-inventory-data" {
  type        = "zip"
  output_path = "${path.module}/files/ce-gcp-instance-inventory-data.zip"
  source {
    content  = "${file("${path.module}/src/python/gcp_instance_inventory_data_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/gcp_util.py")}"
    filename = "gcp_util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-gcp-instance-inventory-data-load" {
  type        = "zip"
  output_path = "${path.module}/files/ce-gcp-instance-inventory-data-load.zip"
  source {
    content  = "${file("${path.module}/src/python/gcp_instance_inventory_data_load.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-gcp-disk-inventory-data" {
  type        = "zip"
  output_path = "${path.module}/files/ce-gcp-disk-inventory-data.zip"
  source {
    content  = "${file("${path.module}/src/python/gcp_disk_inventory_data_main.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/gcp_util.py")}"
    filename = "gcp_util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

data "archive_file" "ce-gcp-disk-inventory-data-load" {
  type        = "zip"
  output_path = "${path.module}/files/ce-gcp-disk-inventory-data-load.zip"
  source {
    content  = "${file("${path.module}/src/python/gcp_disk_inventory_data_load.py")}"
    filename = "main.py"
  }
  source {
    content  = "${file("${path.module}/src/python/util.py")}"
    filename = "util.py"
  }
  source {
    content  = "${file("${path.module}/src/python/bq_schema.py")}"
    filename = "bq_schema.py"
  }
  source {
    content  = "${file("${path.module}/src/python/requirements.txt")}"
    filename = "requirements.txt"
  }
}

resource "google_storage_bucket_object" "ce-clusterdata-archive" {
  name   = "ce-clusterdata.${data.archive_file.ce-clusterdata.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-clusterdata.zip"
  depends_on = ["data.archive_file.ce-clusterdata"]
}

resource "google_storage_bucket_object" "ce-gcp-billing-bq-archive" {
  name = "ce-aws-billing-bq.${data.archive_file.ce-gcp-billing-bq.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-gcp-billing-bq.zip"
  depends_on = ["data.archive_file.ce-gcp-billing-bq"]
}

resource "google_storage_bucket_object" "ce-aws-billing-bq-archive" {
  name = "ce-aws-billing-bq.${data.archive_file.ce-aws-billing-bq.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-aws-billing-bq.zip"
  depends_on = ["data.archive_file.ce-aws-billing-bq"]
}

resource "google_storage_bucket_object" "ce-aws-billing-gcs-archive" {
  name = "ce-aws-billing-gcs.${data.archive_file.ce-aws-billing-gcs.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-aws-billing-gcs.zip"
  depends_on = ["data.archive_file.ce-aws-billing-gcs"]
}

resource "google_storage_bucket_object" "ce-azure-billing-gcs-archive" {
  name = "ce-azure-billing.${data.archive_file.ce-azure-billing-gcs.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-azure-billing-gcs.zip"
  depends_on = ["data.archive_file.ce-azure-billing-gcs"]
}

resource "google_storage_bucket_object" "ce-azure-billing-bq-archive" {
  name = "ce-azure-billing.${data.archive_file.ce-azure-billing-bq.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-azure-billing-bq.zip"
  depends_on = ["data.archive_file.ce-azure-billing-bq"]
}

resource "google_storage_bucket_object" "ce-awsdata-ec2-archive" {
  name = "ce-awsdata.${data.archive_file.ce-awsdata-ec2.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-awsdata-ec2.zip"
  depends_on = ["data.archive_file.ce-awsdata-ec2"]
}

resource "google_storage_bucket_object" "ce-awsdata-ec2-load-archive" {
  name = "ce-awsdata.${data.archive_file.ce-awsdata-ec2-load.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-awsdata-ec2-load.zip"
  depends_on = ["data.archive_file.ce-awsdata-ec2-load"]
}

resource "google_storage_bucket_object" "ce-awsdata-ec2-metric-archive" {
  name = "ce-awsdata.${data.archive_file.ce-awsdata-ec2-metric.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-awsdata-ec2-metric.zip"
  depends_on = ["data.archive_file.ce-awsdata-ec2-metric"]
}

resource "google_storage_bucket_object" "ce-awsdata-ebs-archive" {
  name = "ce-awsdata.${data.archive_file.ce-awsdata-ebs.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-awsdata-ebs.zip"
  depends_on = ["data.archive_file.ce-awsdata-ebs"]
}

resource "google_storage_bucket_object" "ce-awsdata-ebs-load-archive" {
  name = "ce-awsdata.${data.archive_file.ce-awsdata-ebs-load.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-awsdata-ebs-load.zip"
  depends_on = ["data.archive_file.ce-awsdata-ebs-load"]
}

resource "google_storage_bucket_object" "ce-awsdata-ebs-metrics-archive" {
  name = "ce-awsdata.${data.archive_file.ce-awsdata-ebs-metrics.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-awsdata-ebs-metrics.zip"
  depends_on = ["data.archive_file.ce-awsdata-ebs-metrics"]
}

resource "google_storage_bucket_object" "ce-awsdata-rds-archive" {
  name = "ce-awsdata.${data.archive_file.ce-awsdata-rds.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-awsdata-rds.zip"
  depends_on = ["data.archive_file.ce-awsdata-rds"]
}

resource "google_storage_bucket_object" "ce-awsdata-rds-load-archive" {
  name = "ce-awsdata.${data.archive_file.ce-awsdata-rds-load.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-awsdata-rds-load.zip"
  depends_on = ["data.archive_file.ce-awsdata-rds-load"]
}

resource "google_storage_bucket_object" "ce-aws-inventory-init-archive" {
  name = "ce-awsdata.${data.archive_file.ce-aws-inventory-init.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-aws-inventory-init.zip"
  depends_on = ["data.archive_file.ce-aws-inventory-init"]
}

resource "google_storage_bucket_object" "ce-gcp-inventory-init-archive" {
  name = "ce-gcp.${data.archive_file.ce-gcp-inventory-init.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-gcp-inventory-init.zip"
  depends_on = ["data.archive_file.ce-gcp-inventory-init"]
}

resource "google_storage_bucket_object" "ce-azure-inventory-init-archive" {
  name = "ce-azuredata.${data.archive_file.ce-azure-inventory-init.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-azure-inventory-init.zip"
  depends_on = ["data.archive_file.ce-azure-inventory-init"]
}

resource "google_storage_bucket_object" "ce-gcp-instance-inventory-data-archive" {
  name = "ce-gcpdata.${data.archive_file.ce-gcp-instance-inventory-data.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-gcp-instance-inventory-data.zip"
  depends_on = ["data.archive_file.ce-gcp-instance-inventory-data"]
}

resource "google_storage_bucket_object" "ce-gcp-instance-inventory-data-load-archive" {
  name = "ce-gcpdata.${data.archive_file.ce-gcp-instance-inventory-data-load.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-gcp-instance-inventory-data-load.zip"
  depends_on = ["data.archive_file.ce-gcp-instance-inventory-data-load"]
}

resource "google_storage_bucket_object" "ce-gcp-disk-inventory-data-archive" {
  name = "ce-gcpdata.${data.archive_file.ce-gcp-disk-inventory-data.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-gcp-disk-inventory-data.zip"
  depends_on = ["data.archive_file.ce-gcp-disk-inventory-data"]
}

resource "google_storage_bucket_object" "ce-gcp-disk-inventory-data-load-archive" {
  name = "ce-gcpdata.${data.archive_file.ce-gcp-disk-inventory-data-load.output_md5}.zip"
  bucket = "${google_storage_bucket.bucket1.name}"
  source = "${path.module}/files/ce-gcp-disk-inventory-data-load.zip"
  depends_on = ["data.archive_file.ce-gcp-disk-inventory-data-load"]
}

resource "google_cloudfunctions_function" "ce-clusterdata-function" {
    name                      = "ce-clusterdata-terraform"
    entry_point               = "main"
    available_memory_mb       = 256
    timeout                   = 540
    runtime                   = "python38"
    project                   = "${var.projectId}"
    region                    = "${var.region}"
    source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
    source_archive_object     = "${google_storage_bucket_object.ce-clusterdata-archive.name}"
    environment_variables = {
      disabled = "false"
      disable_for_accounts = ""
      GCP_PROJECT = "${var.projectId}"
    }
    event_trigger {
      event_type = "google.storage.object.finalize"
      resource   = "clusterdata-${var.deployment}"
      failure_policy {
        retry = false
      }
    }
}


resource "google_cloudfunctions_function" "ce-gcp-billing-bq-function" {
  name                      = "ce-gcp-billing-bq-terraform"
  description               = ""
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-gcp-billing-bq-archive.name}"
  #labels = {
  #  deployment_name           = "test"
  #}
  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
    GCPCFTOPIC = "${google_pubsub_topic.ce-gcp-billing-cf-topic.name}"
  }
  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-gcp-billing-cf-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-aws-billing-bq-function" {
  name                      = "ce-aws-billing-bq-terraform"
  description               = ""
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-aws-billing-bq-archive.name}"
  #labels = {
  #  deployment_name           = "test"
  #}
  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }
  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-aws-billing-cf-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-aws-billing-gcs-function" {
  name                      = "ce-aws-billing-gcs-terraform"
  description               = ""
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-aws-billing-gcs-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
    AWSCFTOPIC = "${google_pubsub_topic.ce-aws-billing-cf-topic.name}"
  }
  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-aws-billing-gcs-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-azure-billing-bq-function" {
  name                      = "ce-azure-billing-bq-terraform"
  description               = "This cloudfunction gets triggered when cloud scheduler sends an event in pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-azure-billing-bq-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }
  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-azure-billing-cf-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-azure-billing-gcs-function" {
  name                      = "ce-azure-billing-gcs-terraform"
  description               = "This cloudfunction gets triggered when azure gcs data transfer job completes"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-azure-billing-gcs-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
    AZURECFTOPIC = "${google_pubsub_topic.ce-azure-billing-cf-topic.name}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-azure-billing-gcs-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-awsdata-ec2-function" {
  name                      = "ce-awsdata-ec2-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-awsdata-ec2-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-awsdata-ec2-inventory-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-awsdata-ec2-load-function" {
  name                      = "ce-awsdata-ec2-load-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-awsdata-ec2-load-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-awsdata-ec2-inventory-load-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-awsdata-ec2-metric-function" {
  name                      = "ce-awsdata-ec2-metric-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-awsdata-ec2-metric-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-awsdata-ec2-metric-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-awsdata-ebs-function" {
  name                      = "ce-awsdata-ebs-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-awsdata-ebs-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-awsdata-ebs-inventory-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-awsdata-ebs-load-function" {
  name                      = "ce-awsdata-ebs-load-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-awsdata-ebs-load-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-awsdata-ebs-inventory-load-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-awsdata-ebs-metrics-function" {
  name                      = "ce-awsdata-ebs-metrics-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-awsdata-ebs-metrics-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-awsdata-ebs-metrics-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-awsdata-rds-function" {
  name                      = "ce-awsdata-rds-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-awsdata-rds-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-awsdata-rds-inventory-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-awsdata-rds-load-function" {
  name                      = "ce-awsdata-rds-load-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-awsdata-rds-load-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-awsdata-rds-inventory-load-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-aws-inventory-init-function" {
  name                      = "ce-aws-inventory-init-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-aws-inventory-init-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-aws-connector-crud-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-gcp-inventory-init-function" {
  name                      = "ce-gcp-inventory-init-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-gcp-inventory-init-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-gcp-connector-crud-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-azure-inventory-init-function" {
  name                      = "ce-azure-inventory-init-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-azure-inventory-init-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-azure-connector-crud-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-gcp-instance-inventory-data-function" {
  name                      = "ce-gcp-instance-inventory-data-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-gcp-instance-inventory-data-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-gcp-instance-inventory-data-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-gcp-instance-inventory-data-load-function" {
  name                      = "ce-gcp-instance-inventory-data-load-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-gcp-instance-inventory-data-load-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-gcp-instance-inventory-data-load-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-gcp-disk-inventory-data-function" {
  name                      = "ce-gcp-disk-inventory-data-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-gcp-disk-inventory-data-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-gcp-disk-inventory-data-topic.name}"
    failure_policy {
      retry = false
    }
  }
}

resource "google_cloudfunctions_function" "ce-gcp-disk-inventory-data-load-function" {
  name                      = "ce-gcp-disk-inventory-data-load-terraform"
  description               = "This cloudfunction gets triggered upon event in a pubsub topic"
  entry_point               = "main"
  available_memory_mb       = 256
  timeout                   = 540
  runtime                   = "python38"
  project                   = "${var.projectId}"
  region                    = "${var.region}"
  source_archive_bucket     = "${google_storage_bucket.bucket1.name}"
  source_archive_object     = "${google_storage_bucket_object.ce-gcp-disk-inventory-data-load-archive.name}"

  environment_variables = {
    disabled = "false"
    enable_for_accounts = ""
    GCP_PROJECT = "${var.projectId}"
  }

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.ce-gcp-disk-inventory-data-load-topic.name}"
    failure_policy {
      retry = false
    }
  }
}