# Terraform installation
set +x  
apt-get update  
apt-get install wget  
apt-get -y install git  
wget https://releases.hashicorp.com/terraform/0.13.3/terraform_0.13.3_linux_amd64.zip apt-get install unzip  
unzip terraform_0.13.3_linux_amd64.zip  
cp terraform /usr/bin/  
terraform --version  
