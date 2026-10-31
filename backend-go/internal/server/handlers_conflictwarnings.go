package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

func (s *Server) GetConflictWarnings(
	ctx context.Context,
	r api.GetConflictWarningsRequestObject,
) (api.GetConflictWarningsResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) AcknowledgeWarning(
	ctx context.Context,
	r api.AcknowledgeWarningRequestObject,
) (api.AcknowledgeWarningResponseObject, error) {
	return nil, httpx.NotImplemented()
}
