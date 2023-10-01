// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package mock

//go:generate mockgen -source=../store/store.go -package=mock -destination=mock_store.go Store
//go:generate mockgen -source=../cache/cache.go -package=mock -destination=mock_cache.go Cache
//go:generate mockgen -source=../queue/queue.go -package=mock -destination=mock_queue.go Queue
//go:generate mockgen -source=../download/download.go -package=mock -destination=mock_download.go Download
//go:generate mockgen -source=../stream/stream.go -package=mock -destination=mock_stream.go Stream
//go:generate mockgen -source=../client/client.go -package=mock -destination=mock_client.go Client
