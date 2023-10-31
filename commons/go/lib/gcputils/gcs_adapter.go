// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package gcputils

import (
	"context"
	"io"

	"cloud.google.com/go/storage"
)

//go:generate mockgen -source gcs_adapter.go  -package=gcputils -destination gcs_adapter_mock.go StorageClient BucketHandle ObjectHandle StorageReader StorageWriter
// Mocked files are generated in the same directory rather than mocks folder since generated mocked files are unable to find the dependencies.
// This issue comes up when an interface having methods that returns an interface is mocked.

type (
	storageClient struct{ *storage.Client }
	bucketHandle  struct{ *storage.BucketHandle }
	objectHandle  struct{ *storage.ObjectHandle }
	storageReader struct{ *storage.Reader }
	storageWriter struct{ *storage.Writer }
)

// StorageClient denotes the required methods on GCS storage client.
type StorageClient interface {
	Bucket(name string) BucketHandle
	Close() error
}

// BucketHandle denotes the required methods on GCS storage buckets.
type BucketHandle interface {
	Object(name string) ObjectHandle
	SignedURL(bucket string, opts *storage.SignedURLOptions) (string, error)
}

// ObjectHandle denotes the required methods on GCS storage bucket objects.
type ObjectHandle interface {
	NewReader(context.Context) (StorageReader, error)
	NewWriter(ctx context.Context) StorageWriter
	Attrs(ctx context.Context) (*storage.ObjectAttrs, error)
	Delete(ctx context.Context) error
	Update(ctx context.Context, uattrs storage.ObjectAttrsToUpdate) (*storage.ObjectAttrs, error)
}

// StorageReader denotes the required methods on GCS storage object reader.
type StorageReader interface {
	io.ReadCloser
}

// StorageWriter denotes the required methods on GCS storage object reader.
type StorageWriter interface {
	io.WriteCloser
}

// AdaptGCSClient adapts a storage.Client so that it satisfies StorageClient.
func AdaptGCSClient(c *storage.Client) StorageClient {
	return storageClient{c}
}

func (c storageClient) Bucket(name string) BucketHandle {
	return bucketHandle{c.Client.Bucket(name)}
}

func (b bucketHandle) Object(name string) ObjectHandle {
	return objectHandle{b.BucketHandle.Object(name)}
}

// NewReader creates a storage reader to read the object from GCS.
func (o objectHandle) NewReader(ctx context.Context) (StorageReader, error) {
	r, err := o.ObjectHandle.NewReader(ctx)
	if err != nil {
		return nil, err
	}
	return storageReader{r}, nil
}

func (o objectHandle) NewWriter(ctx context.Context) StorageWriter {
	return storageWriter{o.ObjectHandle.NewWriter(ctx)}
}

func (o objectHandle) Attrs(ctx context.Context) (*storage.ObjectAttrs, error) {
	return o.ObjectHandle.Attrs(ctx)
}

func (o objectHandle) Delete(ctx context.Context) error {
	return o.ObjectHandle.Delete(ctx)
}
