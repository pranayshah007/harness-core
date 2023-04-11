 Install TF
RUN curl -LO  https://releases.hashicorp.com/terraform/1.3.1/terraform_1.3.1_linux_amd64.zip \
  && unzip -q terraform_1.3.1_linux_amd64.zip \
  && mv ./terraform /usr/bin/ \
  && terraform --version

USER 1001
```
