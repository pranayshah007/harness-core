## Custom delegate image

The recommended method for installing third party tools on your delegate is to create your own delegate image, push it to a container registry, and then to modify your delegate deployments to use your new custom image.

For more information, go to [Build custom delegate images with third-party tools](https://developer.harness.io/docs/platform/delegates/install-delegates/build-custom-delegate-images-with-third-party-tools/).

```dockerfile
ARG DELEGATE_TAG=23.03.78705
ARG DELEGATE_IMAGE=harness/delegate
FROM $DELEGATE_IMAGE:$DELEGATE_TAG

USER 0

RUN useradd -u 1001 -g 0 harness
