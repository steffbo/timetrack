package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/conflictwarnings"
)

func (s *Server) conflictWarnings() conflictwarnings.Service {
	return conflictwarnings.Service{Pool: s.Pool, Clock: s.Clock}
}

func (s *Server) GetConflictWarnings(
	ctx context.Context,
	r api.GetConflictWarningsRequestObject,
) (api.GetConflictWarningsResponseObject, error) {
	principal, _ := auth.Current(ctx)
	unacknowledged := r.Params.UnacknowledgedOnly != nil && *r.Params.UnacknowledgedOnly
	out, err := s.conflictWarnings().List(ctx, principal.ID, unacknowledged)
	if err != nil {
		return nil, err
	}
	return api.GetConflictWarnings200JSONResponse(out), nil
}

func (s *Server) AcknowledgeWarning(
	ctx context.Context,
	r api.AcknowledgeWarningRequestObject,
) (api.AcknowledgeWarningResponseObject, error) {
	principal, _ := auth.Current(ctx)
	out, err := s.conflictWarnings().Acknowledge(ctx, principal.ID, r.Id)
	if err != nil {
		return nil, err
	}
	return api.AcknowledgeWarning200JSONResponse(out), nil
}
