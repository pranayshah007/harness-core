/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

variable "global_access_key" {type="string"}
variable "global_secret_key" {type="string"}


provider "aws" {
 region = "ap-southeast-2"
 access_key = "${var.global_access_key}"
 secret_key = "${var.global_secret_key}"
}

resource "aws_instance" "example" {
 ami = "ami-0c9d48b5db60893646e"
 instance_type = "t2.micro"
 }

 output "global_access_key" {
  value = "${var.global_access_key}"
 }

 output "secret_key" {
  value = "${var.global_secret_key}"
 }
