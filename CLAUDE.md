# Claude Code Bootstrap - Time Tracking Application

## 📋 Orientation Checklist (Read on Every Session)

1. **Latest Changes** - Review recent work
   - Read last 10 git commits: `git log --oneline -10`
   - Check `CHANGELOG.md` for recent features and development history

2. **Project State**
   - Review `openapi.yaml` - Single source of truth for API design
   - Check `README.md` - Project overview and setup

3. **Current Phase** - Check CHANGELOG.md Development History section
   - Phase 7 ✅ Backend Complete (99 tests passing)
   - Phase 8 ✅ Frontend Complete (4 new views)
   - Phase 9 ⏳ Statistics & Reports (Next)

## 🏗️ Architecture Overview

**Monorepo Structure**:
- `backend/` - Spring Boot 4, Java 25, PostgreSQL 17
- `frontend/` - Vue 3, TypeScript, Vite, PrimeVue 4
- `openapi.yaml` - API-first design (root level, shared by both)

**Key Patterns**:
- Use-case driven architecture (backend)
- OpenAPI code generation (both backend and frontend)
- Integration tests with Testcontainers (see backend/TESTING.md)
- JWT authentication with refresh tokens
- German + English i18n support

## 🚀 Quick Start Commands

### Backend (Spring Boot)
```bash
cd backend
./mvnw clean test              # Run all tests
./mvnw spring-boot:run         # Start server (http://localhost:8080)
./mvnw clean compile           # Compile only
```

### Frontend (Vue)
```bash
cd frontend
npm run dev                    # Dev server (http://localhost:5173)
npm run build                  # Production build
npm run generate-api           # Regenerate API client from openapi.yaml
```

### Both via Docker
```bash
docker-compose up -d           # Start both + PostgreSQL
docker-compose logs -f app     # View backend logs
docker-compose down            # Stop all services
```

**Default Credentials**: admin@timetrack.local / admin1

## 🌐 Browser Testing with Chrome MCP

Claude Code can interact with the running application via the Chrome DevTools MCP server, enabling automated browser testing and UI inspection.

### Prerequisites
- Chrome MCP server must be configured in Claude Code settings
- Frontend dev server running on `http://localhost:5173`
- Backend server running on `http://localhost:8080`

### Common Browser Testing Tasks

**Navigate and Login**:
```
Claude can navigate to http://localhost:5173/dashboard
Fill login form with credentials
Take snapshots of the page structure
Take screenshots of the UI
```

**Inspect Page Elements**:
- `take_snapshot` - Get accessibility tree with element UIDs
- `take_screenshot` - Capture visual state
- `list_console_messages` - Check for JavaScript errors
- `list_network_requests` - Inspect API calls

**Interact with UI**:
- `fill` or `fill_form` - Enter data into form fields
- `click` - Click buttons and links
- `press_key` - Submit forms or keyboard shortcuts
- `wait_for` - Wait for specific text to appear

**Debug Issues**:
- Check console for errors: `list_console_messages`
- Inspect failed requests: `list_network_requests` with filters
- Get request details: `get_network_request` by reqid

### Example Workflow
1. Ask Claude to access the dashboard: "Access http://localhost:5173/dashboard"
2. Claude will navigate, detect login redirect, and fill credentials
3. Once logged in, Claude can take screenshots or inspect elements
4. Useful for verifying UI behavior, testing flows, or debugging issues

### Tips
- Snapshots show element UIDs - use these for interactions
- Network requests help debug API communication issues
- Console messages reveal JavaScript errors
- Screenshots capture visual state for review

## 🧪 Testing Philosophy

**When to Add Tests**:
- ✅ Every new use case requires integration test
- ✅ Every repository method needs test coverage
- ✅ Test both success and failure scenarios
- ✅ Verify authorization rules (admin vs user access)

**See**: `backend/TESTING.md` for detailed testing guide with Testcontainers setup

**Current Status**: 99 integration tests passing

## 📝 Development Workflow

### Before Starting Work
1. Run orientation checklist above
2. Read task requirements carefully
3. Check for existing patterns in codebase
4. Use TodoWrite tool to plan multi-step tasks

### During Development
1. **Follow existing patterns**:
   - Backend: Use-case → Controller → Repository
   - Frontend: Service → Composable → View
   - Always update OpenAPI spec first for new endpoints

2. **Check for redundancy**:
   - Search for similar code before writing new
   - Use shared test fixtures (RepositoryTestBase)
   - Centralize common logic (e.g., mappers, validators)
   - Don't duplicate validation logic

3. **Write tests**:
   - Integration test for backend use cases
   - Manual testing for frontend (document in CHANGELOG.md)

### After Completing Task
1. **Update documentation**:
   - Update `CHANGELOG.md` with user-facing changes and development history
   - Update README.md if setup/commands changed

2. **Create git commit**:
   ```bash
   # Conventional commit format:
   git commit -m "type(scope): description"

   # Types:
   feat:     New feature
   fix:      Bug fix
   docs:     Documentation only
   test:     Adding/updating tests
   refactor: Code change without behavior change
   chore:    Build/tooling changes

   # Examples:
   feat(backend): Add vacation balance API endpoints
   fix(frontend): Correct time-off date filtering
   docs: Update CLAUDE.md with testing workflow
   test(backend): Add integration tests for recurring off-days
   ```

3. **Include co-author**:
   ```
   feat: Complete Phase 8 frontend implementation

   🤖 Generated with [Claude Code](https://claude.com/claude-code)

   Co-Authored-By: Claude <noreply@anthropic.com>
   ```

## 🔍 Key Files Reference

### API Design
- `openapi.yaml` - **START HERE** for any API changes
  - Backend generates interfaces from this
  - Frontend generates TypeScript client from this
  - Single source of truth

### Backend Entry Points
- `backend/src/main/resources/application.yml` - Configuration
- `backend/src/main/java/cc/remer/timetrack/usecase/` - Business logic
- `backend/src/main/resources/db/migration/` - Database schema (Flyway)
- `backend/TESTING.md` - Testing infrastructure guide

### Frontend Entry Points
- `frontend/src/main.ts` - App entry point
- `frontend/src/router/index.ts` - Routes
- `frontend/src/views/` - Page components
- `frontend/src/api/generated/` - Auto-generated from OpenAPI (don't edit)
- `frontend/src/i18n/locales/` - German (de.json) and English (en.json)

### Documentation
- `CHANGELOG.md` - User-facing changes + development history
- `README.md` - Project setup and overview
- `backend/TESTING.md` - Testing infrastructure guide

## 🎯 Current Context (Last Updated: 2025-12-12)

**Completed**:
- ✅ Phase 1-6: Foundation → Working Hours → Basic Frontend
- ✅ Phase 7: Advanced time tracking backend (99 tests)
- ✅ Phase 8: Advanced time tracking frontend (4 views)

**Features Available**:
1. Authentication (JWT with refresh tokens)
2. User Management (admin CRUD)
3. Working Hours (per-weekday config with time ranges)
4. Time Off (vacation, sick, personal, holidays)
5. Recurring Off-Days (weekly/monthly patterns)
6. Vacation Balance (days tracking)
7. Public Holidays (German: Berlin & Brandenburg)
8. Profile Management

**Next Phase**: Phase 9 - Statistics & Reports
- Dashboard statistics
- Time tracking charts
- Vacation usage reports
- Export functionality

## 🚨 Important Reminders

1. **Always read openapi.yaml** before adding/modifying API endpoints
2. **Run tests after backend changes**: `./mvnw clean test`
3. **Regenerate API client after OpenAPI changes**: `npm run generate-api`
4. **Check for code duplication** before implementing
5. **Update CHANGELOG.md** after completing major tasks
6. **Commit after task completion** with conventional format
7. **Both servers must run** for full app testing

## 💡 Common Tasks

### Add New Backend API Endpoint
1. Update `openapi.yaml` with new endpoint
2. Run Maven compile to generate interfaces
3. Create use case in `backend/src/main/java/cc/remer/timetrack/usecase/`
4. Implement controller method
5. Write integration test
6. Regenerate frontend API client: `cd frontend && npm run generate-api`
7. Document in CHANGELOG.md if user-facing

### Add New Frontend View
1. Create view component in `frontend/src/views/`
2. Add route in `frontend/src/router/index.ts`
3. Add navigation item in `frontend/src/components/layout/AppNavbar.vue`
4. Add i18n translations (de.json and en.json)
5. Import/use generated API service from `src/api/generated/`

### Add Database Migration
1. Create new file: `backend/src/main/resources/db/migration/V{N}__description.sql`
2. Follow naming: V{number}__{snake_case_description}.sql
3. Run tests to verify migration works
4. Document schema changes in CHANGELOG.md

---

**Last Updated**: 2025-12-12 (Phase 8 Complete)
**For Questions**: Check README.md or openapi.yaml
