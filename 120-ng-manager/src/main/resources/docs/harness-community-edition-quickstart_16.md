# Clean Up

To clean up your environment, do the following.

To delete the Delegate, run the following:

```
kubectl delete statefulset -n harness-delegate-ng quickstart
```

To remove Harness CD Community Edition, run the following:

```
docker-compose down -v
```

To remove the Harness images, use `docker rmi` to removes images by their ID.

To remove the image, you first need to list all the images to get the Image IDs, Image name and other details. Run `docker images -a` or `docker images`.

Note the images you want to remove, and then run:

```
docker rmi <image-id> <image-id> ...
```

To remove all images at once, run:

```
docker rmi $(docker images -q)
```
