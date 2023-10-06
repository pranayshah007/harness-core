// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package gcputils

import (
	"context"
	"time"

	"cloud.google.com/go/storage"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"go.uber.org/zap"
	"google.golang.org/api/option"
)

const defaultTimeoutSecs int64 = 300 // 5 minutes
var storageNewClient = storage.NewClient

//go:generate mockgen -source gcs.go -package=gcputils -destination mocks/gcs_mock.go GCS

// GCS represents an interface to view or updates objects in GCS bucket.
type GCS interface {
	UploadObject(ctx context.Context, bucketName, objectName, srcFilePath string) error
	DownloadObject(ctx context.Context, bucketName string, objectName string, dstFilePath string) error
	GetObjectMetadata(ctx context.Context, bucketName, objectName string) (map[string]string, error)
	DeleteObject(ctx context.Context, bucketName string, objectName string) error
	UpdateObjectMetadata(ctx context.Context, bucketName, objectName string, metadata map[string]string) error
	SignURL(bucketName, objectName, customHost string) (string, error)
	Close() error
}

type gcs struct {
	client             StorageClient
	fs                 filesystem.FileSystem
	log                *zap.SugaredLogger
	timeoutSecs        int64
	credentialFilePath string
}

// GCSClientOption is a type for providing arguments to NewGCSClient in variadic format
type GCSClientOption func(*gcs)

// WithGCSClientTimeout sets the timeout of any operation performed using the GCS client. Timeout provide should be a positive value.
func WithGCSClientTimeout(timeoutSecs int64) GCSClientOption {
	return func(g *gcs) {
		g.timeoutSecs = timeoutSecs
	}
}

// WithGCSCredentialsFile sets the file path of credentials to create GCS client.
func WithGCSCredentialsFile(credentialFilePath string) GCSClientOption {
	return func(g *gcs) {
		g.credentialFilePath = credentialFilePath
	}
}

// NewGCSClient creates a new GCS client and returns it.
// Credential file to create GCS client can be provided using GCSClientOption. By default, it assumes that credential file path is set in
// GOOGLE_APPLICATION_CREDENTIALS environment variable.
// Timeout of client operation can be provided using the GCSClientOption. By default, timeout of any client operation is 5 minutes.
func NewGCSClient(ctx context.Context, fs filesystem.FileSystem, log *zap.SugaredLogger, opts ...GCSClientOption) (GCS, error) {
	gcs := &gcs{
		log:                log,
		fs:                 fs,
		timeoutSecs:        defaultTimeoutSecs,
		credentialFilePath: "",
	}
	for _, opt := range opts {
		opt(gcs)
	}

	var client *storage.Client
	var err error
	if gcs.credentialFilePath == "" {
		client, err = storageNewClient(ctx)
	} else {
		client, err = storageNewClient(ctx, option.WithCredentialsFile(gcs.credentialFilePath))
	}
	if err != nil {
		return nil, err
	}

	gcs.client = AdaptGCSClient(client)
	return gcs, nil
}

// UploadObject uploads a file to a GCS bucket.
// os.PathError is returned if no file is present at given srcFilePath.
func (gcs *gcs) UploadObject(ctx context.Context, bucketName, objectName, srcFilePath string) error {
	start := time.Now()
	fh, err := gcs.fs.Open(srcFilePath)
	if err != nil {
		logGCSUploadWarning(gcs.log, "error encountered while opening file during GCS upload", bucketName, objectName, srcFilePath, start, err)
		return err
	}
	defer fh.Close()

	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(gcs.timeoutSecs))
	defer cancel()

	wc := gcs.client.Bucket(bucketName).Object(objectName).NewWriter(ctx)
	//	wc.Metadata = metadata
	if _, err = gcs.fs.Copy(wc, fh); err != nil {
		logGCSUploadWarning(gcs.log, "error encountered while copying file during GCS upload", bucketName, objectName, srcFilePath, start, err)
		return err
	}
	if err := wc.Close(); err != nil {
		logGCSUploadWarning(gcs.log, "error encountered while closing remote file during GCS upload", bucketName, objectName, srcFilePath, start, err)
		return err
	}

	gcs.log.Infow(
		"Uploaded to GCS",
		"bucket", bucketName,
		"key", objectName,
		"source file path", srcFilePath,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return nil
}

// DownloadObject downloads a file from GCS bucket.
// storage.ErrObjectNotExist is returned if object is not present in the bucket.
func (gcs *gcs) DownloadObject(ctx context.Context, bucketName string, objectName string, dstFilePath string) error {
	start := time.Now()
	fh, err := gcs.fs.Create(dstFilePath)
	if err != nil {
		logGCSDownloadWarning(gcs.log, "error encountered while creating file during GCS download", bucketName, objectName, dstFilePath, start, err)
		return err
	}
	defer fh.Close()

	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(gcs.timeoutSecs))
	defer cancel()

	rc, err := gcs.client.Bucket(bucketName).Object(objectName).NewReader(ctx)
	if err != nil {
		logGCSDownloadWarning(gcs.log, "error encountered while fetching object reader during GCS download", bucketName, objectName, dstFilePath, start, err)
		return err
	}
	defer rc.Close()

	if _, err = gcs.fs.Copy(fh, rc); err != nil {
		logGCSDownloadWarning(gcs.log, "error encountered while copying file during GCS download", bucketName, objectName, dstFilePath, start, err)
		return err
	}

	gcs.log.Infow(
		"Downloaded from GCS",
		"bucket", bucketName,
		"key", objectName,
		"destination file path", dstFilePath,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return nil
}

// GetObjectMetadata returns the metadata for an object present in GCS bucket.
// storage.ErrObjectNotExist is returned if object is not present in the bucket.
func (gcs *gcs) GetObjectMetadata(ctx context.Context, bucketName, objectName string) (map[string]string, error) {
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(gcs.timeoutSecs))
	defer cancel()

	objectHandle := gcs.client.Bucket(bucketName).Object(objectName)
	attrs, err := objectHandle.Attrs(ctx)
	if err != nil {
		gcs.log.Warnw(
			"error encountered while viewing object from gcs",
			"bucket", bucketName,
			"key", objectName,
			"elapsed_time_ms", utils.TimeSince(start),
			zap.Error(err),
		)
		return nil, err
	}

	gcs.log.Infow(
		"Fetched object metadata from GCS",
		"bucket", bucketName,
		"key", objectName,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return attrs.Metadata, err
}

// UpdateObjectMetadata updates the metadata for an object present in GCS bucket.
// storage.ErrObjectNotExist is returned if object is not present in the bucket.
func (gcs *gcs) UpdateObjectMetadata(ctx context.Context, bucketName, objectName string, metadata map[string]string) error {
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(gcs.timeoutSecs))
	defer cancel()

	uattrs := storage.ObjectAttrsToUpdate{
		Metadata: metadata,
	}
	_, err := gcs.client.Bucket(bucketName).Object(objectName).Update(ctx, uattrs)
	if err != nil {
		gcs.log.Warnw(
			"error encountered while updating object to gcs",
			"bucket", bucketName,
			"key", objectName,
			"elapsed_time_ms", utils.TimeSince(start),
			zap.Error(err),
		)
		return err
	}

	gcs.log.Infow(
		"Updated object metadata to GCS",
		"bucket", bucketName,
		"key", objectName,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return nil
}

// DeleteObject deletes an object from GCS bucket.
// storage.ErrObjectNotExist is returned if object is not present in the bucket.
func (gcs *gcs) DeleteObject(ctx context.Context, bucketName string, objectName string) error {
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(gcs.timeoutSecs))
	defer cancel()
	o := gcs.client.Bucket(bucketName).Object(objectName)
	if err := o.Delete(ctx); err != nil {
		gcs.log.Warnw(
			"error encountered while deleting from gcs",
			"bucket", bucketName,
			"key", objectName,
			"elapsed_time_ms", utils.TimeSince(start),
			zap.Error(err),
		)
		return err
	}

	gcs.log.Infow(
		"Deleted from GCS",
		"bucket", bucketName,
		"key", objectName,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return nil
}

// SignURL signs an object from GCS bucket with custom host.
// storage.ErrObjectNotExist is returned if object is not present in the bucket.
func (gcs *gcs) SignURL(bucketName, objectName, customHost string) (string, error) {
	expiration := time.Now().Add(1 * time.Hour) // Adjust expiration as needed
	url, err := gcs.client.Bucket(bucketName).SignedURL(objectName, &storage.SignedURLOptions{
		Method:   "GET",
		Expires:  expiration,
		Hostname: customHost,
	})

	if err != nil {
		return "", err
	}

	return url, nil
}

func (gcs *gcs) Close() error {
	return gcs.client.Close()
}

func logGCSDownloadWarning(log *zap.SugaredLogger, warnMsg, bucketName, objectName, dstFilePath string, startTime time.Time, err error) {
	log.Warnw(
		warnMsg,
		"bucket", bucketName,
		"key", objectName,
		"destination file path", dstFilePath,
		"elapsed_time_ms", utils.TimeSince(startTime),
		zap.Error(err),
	)
}

func logGCSUploadWarning(log *zap.SugaredLogger, warnMsg, bucketName, objectName, srcFilePath string, startTime time.Time, err error) {
	log.Warnw(
		warnMsg,
		"bucket", bucketName,
		"key", objectName,
		"source file path", srcFilePath,
		"elapsed_time_ms", utils.TimeSince(startTime),
		zap.Error(err),
	)
}
