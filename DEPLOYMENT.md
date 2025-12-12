# Deployment Guide - zeit.remer.cc

This guide covers deploying the Time Tracking application to production at **zeit.remer.cc**.

## Table of Contents
- [Architecture Overview](#architecture-overview)
- [Prerequisites](#prerequisites)
- [Initial Server Setup](#initial-server-setup)
- [GitHub Container Registry Setup](#github-container-registry-setup)
- [Application Deployment](#application-deployment)
- [Caddy Configuration](#caddy-configuration)
- [Post-Deployment Tasks](#post-deployment-tasks)
- [Updating the Application](#updating-the-application)
- [Backup and Restore](#backup-and-restore)
- [Troubleshooting](#troubleshooting)
- [Security Considerations](#security-considerations)

## Architecture Overview

```
Internet → Caddy (zeit.remer.cc) → Docker Compose
                                    ├── PostgreSQL (persistent volume)
                                    └── Timetrack App (single container)
                                        ├── Spring Boot backend (port 8811)
                                        └── Frontend static files (served by Spring Boot)
```

**Key Design Decisions:**
- **Single Docker image**: Frontend is built and bundled into the backend image
- **Spring Boot serves everything**: Both API endpoints and static frontend files
- **Caddy handles HTTPS**: Automatic SSL/TLS certificates via Let's Encrypt
- **Manual deployment**: Pull images and restart containers manually (no automated CD)
- **Persistent database**: PostgreSQL data stored in Docker volume

## Prerequisites

The server must have the following installed:

1. **Docker** (version 24.0 or higher)
   ```bash
   docker --version
   ```

2. **Docker Compose** (version 2.20 or higher)
   ```bash
   docker compose version
   ```

3. **Caddy** (version 2.7 or higher)
   ```bash
   caddy version
   ```

4. **Git** (for cloning the repository if needed)
   ```bash
   git --version
   ```

### Installing Prerequisites on Ubuntu/Debian

```bash
# Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Docker Compose (included with Docker Desktop, or install separately)
# Usually comes with Docker nowadays

# Caddy
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install caddy
```

## Initial Server Setup

### 1. Create Application Directory

```bash
sudo mkdir -p /opt/timetrack
cd /opt/timetrack
```

### 2. Copy Production Files

Copy the following files from the repository to `/opt/timetrack`:
- `docker-compose.prod.yml`
- `.env.example.prod` (rename to `.env.prod`)

```bash
# Option 1: Clone the repository
git clone https://github.com/stefan-remer/timetrack.git /tmp/timetrack
cp /tmp/timetrack/docker-compose.prod.yml /opt/timetrack/
cp /tmp/timetrack/.env.example.prod /opt/timetrack/.env.prod

# Option 2: Manual copy via scp
scp docker-compose.prod.yml user@server:/opt/timetrack/
scp .env.example.prod user@server:/opt/timetrack/.env.prod
```

### 3. Configure Environment Variables

Edit `/opt/timetrack/.env.prod` with secure values:

```bash
sudo nano /opt/timetrack/.env.prod
```

**Critical values to change:**

```bash
# Database Configuration
DB_PASSWORD=<STRONG_RANDOM_PASSWORD>

# JWT Configuration (minimum 256 bits / 32 characters)
JWT_SECRET=<STRONG_RANDOM_SECRET_AT_LEAST_32_CHARACTERS>

# CORS Configuration
ALLOWED_ORIGINS=https://zeit.remer.cc
```

**Generate secure secrets:**
```bash
# Generate database password
openssl rand -base64 32

# Generate JWT secret
openssl rand -base64 32
```

### 4. Set Proper Permissions

```bash
sudo chown -R $USER:$USER /opt/timetrack
chmod 600 /opt/timetrack/.env.prod  # Protect sensitive environment file
```

## GitHub Container Registry Setup

The application Docker images are hosted on GitHub Container Registry (ghcr.io).

### Login to GitHub Container Registry

To pull images from ghcr.io, you need to authenticate:

1. **Create a GitHub Personal Access Token (PAT)**:
   - Go to: https://github.com/settings/tokens
   - Click "Generate new token" → "Generate new token (classic)"
   - Give it a name: e.g., "timetrack-deployment"
   - Select scope: `read:packages`
   - Click "Generate token"
   - **Copy the token** (you won't see it again!)

2. **Login to ghcr.io on the server:**
   ```bash
   echo YOUR_GITHUB_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
   ```

   Replace:
   - `YOUR_GITHUB_TOKEN` with the token from step 1
   - `YOUR_GITHUB_USERNAME` with your GitHub username

   Example:
   ```bash
   echo ghp_xxxxxxxxxxxx | docker login ghcr.io -u stefan-remer --password-stdin
   ```

3. **Verify login:**
   ```bash
   docker pull ghcr.io/stefan-remer/timetrack:latest
   ```

**Note**: Docker stores credentials in `~/.docker/config.json`. For better security, consider using a credential helper.

## Application Deployment

### 1. Pull the Latest Images

```bash
cd /opt/timetrack
docker compose -f docker-compose.prod.yml pull
```

### 2. Start the Services

```bash
docker compose -f docker-compose.prod.yml up -d
```

This will:
- Start PostgreSQL container
- Wait for PostgreSQL to be healthy
- Start the application container
- Run Flyway database migrations automatically

### 3. Verify Services are Running

```bash
# Check container status
docker compose -f docker-compose.prod.yml ps

# Check application logs
docker compose -f docker-compose.prod.yml logs -f app

# Check database logs
docker compose -f docker-compose.prod.yml logs -f postgres
```

### 4. Test Application Locally

```bash
# Health check
curl http://localhost:8811/api/actuator/health

# Should return: {"status":"UP"}

# Test frontend (should return HTML)
curl http://localhost:8811/

# Should return index.html content
```

## Caddy Configuration

### 1. Configure Caddy

Copy the Caddyfile configuration:

```bash
sudo cp /tmp/timetrack/Caddyfile.example /etc/caddy/Caddyfile
```

Or manually add to your existing Caddyfile:

```caddy
zeit.remer.cc {
    reverse_proxy localhost:8811

    encode gzip

    header {
        X-Content-Type-Options "nosniff"
        X-Frame-Options "DENY"
        Referrer-Policy "strict-origin-when-cross-origin"
        Strict-Transport-Security "max-age=31536000; includeSubDomains; preload"
        Permissions-Policy "geolocation=(), microphone=(), camera=()"
    }
}
```

### 2. Validate Configuration

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
```

### 3. Reload Caddy

```bash
# If using systemd
sudo systemctl reload caddy

# Or if running Caddy manually
sudo caddy reload
```

### 4. Verify Caddy is Running

```bash
sudo systemctl status caddy
```

### 5. Check Caddy Logs

```bash
sudo journalctl -u caddy -f
```

## Post-Deployment Tasks

### 1. Test Public Access

Visit https://zeit.remer.cc in your browser. You should see the login page.

### 2. Change Default Admin Password

1. Login with default credentials:
   - Email: `admin@timetrack.local`
   - Password: `admin`

2. Navigate to your profile (top right menu)
3. Change the password to a strong, unique password
4. Log out and log back in with the new password

### 3. Create Users

As admin, navigate to User Management and create accounts for your team.

### 4. Configure Automatic Restarts

Ensure Docker containers restart on system reboot:

```bash
# The docker-compose.prod.yml already includes:
# restart: unless-stopped

# Enable Docker to start on boot
sudo systemctl enable docker
```

### 5. Set Up Monitoring (Optional)

Consider setting up monitoring for:
- Application uptime (e.g., UptimeRobot, Pingdom)
- Server resources (CPU, memory, disk)
- Docker container health
- Log aggregation

## Updating the Application

When new versions are released, follow these steps:

### 1. Pull Latest Images

```bash
cd /opt/timetrack
docker compose -f docker-compose.prod.yml pull
```

### 2. Backup Database (Recommended)

```bash
# Create backup directory
mkdir -p /opt/timetrack/backups

# Backup database
docker compose -f docker-compose.prod.yml exec postgres pg_dump -U timetrack -d timetrack -F c -f /tmp/backup.dump

# Copy backup out of container
docker cp timetrack-db-prod:/tmp/backup.dump /opt/timetrack/backups/backup-$(date +%Y%m%d-%H%M%S).dump
```

### 3. Stop and Restart Containers

```bash
docker compose -f docker-compose.prod.yml down
docker compose -f docker-compose.prod.yml up -d
```

### 4. Verify Update

```bash
# Check logs
docker compose -f docker-compose.prod.yml logs -f app

# Test application
curl http://localhost:8811/api/actuator/health
```

### 5. Test in Browser

Visit https://zeit.remer.cc and verify everything works.

## Backup and Restore

### Database Backup

#### Automated Backup Script

Create `/opt/timetrack/backup.sh`:

```bash
#!/bin/bash
set -e

BACKUP_DIR="/opt/timetrack/backups"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="$BACKUP_DIR/timetrack-$TIMESTAMP.dump"

mkdir -p "$BACKUP_DIR"

# Create backup
docker compose -f /opt/timetrack/docker-compose.prod.yml exec -T postgres \
    pg_dump -U timetrack -d timetrack -F c > "$BACKUP_FILE"

echo "Backup created: $BACKUP_FILE"

# Keep only last 7 days of backups
find "$BACKUP_DIR" -name "timetrack-*.dump" -mtime +7 -delete

echo "Old backups cleaned up"
```

Make it executable:
```bash
chmod +x /opt/timetrack/backup.sh
```

#### Schedule Daily Backups (Cron)

```bash
crontab -e
```

Add:
```
# Daily backup at 2 AM
0 2 * * * /opt/timetrack/backup.sh >> /var/log/timetrack-backup.log 2>&1
```

### Database Restore

```bash
cd /opt/timetrack

# Stop the application
docker compose -f docker-compose.prod.yml stop app

# Restore from backup
docker compose -f docker-compose.prod.yml exec -T postgres \
    pg_restore -U timetrack -d timetrack -c -F c < backups/timetrack-TIMESTAMP.dump

# Start the application
docker compose -f docker-compose.prod.yml start app
```

### Volume Backup

For complete disaster recovery, backup the entire Docker volume:

```bash
# Stop containers
docker compose -f docker-compose.prod.yml down

# Backup volume
sudo tar czf /opt/timetrack/backups/postgres-volume-$(date +%Y%m%d).tar.gz \
    -C /var/lib/docker/volumes/timetrack_postgres_data .

# Restart containers
docker compose -f docker-compose.prod.yml up -d
```

## Troubleshooting

### Application Won't Start

1. **Check logs:**
   ```bash
   docker compose -f docker-compose.prod.yml logs -f app
   ```

2. **Common issues:**
   - Database not ready: Wait for PostgreSQL health check to pass
   - Missing environment variables: Check `.env.prod`
   - Port conflicts: Ensure port 8811 is not in use

### Database Connection Issues

1. **Verify PostgreSQL is running:**
   ```bash
   docker compose -f docker-compose.prod.yml ps postgres
   ```

2. **Test database connection:**
   ```bash
   docker compose -f docker-compose.prod.yml exec postgres \
       psql -U timetrack -d timetrack -c "SELECT 1;"
   ```

3. **Check database logs:**
   ```bash
   docker compose -f docker-compose.prod.yml logs postgres
   ```

### Caddy/HTTPS Issues

1. **Check Caddy logs:**
   ```bash
   sudo journalctl -u caddy -f
   ```

2. **Verify DNS points to server:**
   ```bash
   dig zeit.remer.cc
   nslookup zeit.remer.cc
   ```

3. **Test local connection:**
   ```bash
   curl http://localhost:8811/api/actuator/health
   ```

4. **Certificate issues:**
   - Caddy automatically obtains certificates from Let's Encrypt
   - Ensure ports 80 and 443 are open
   - Check firewall rules: `sudo ufw status`

### Can't Pull Images from GitHub Registry

1. **Re-authenticate:**
   ```bash
   echo YOUR_GITHUB_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
   ```

2. **Verify token has `read:packages` scope**

3. **Check image exists:**
   ```bash
   curl -H "Authorization: token YOUR_GITHUB_TOKEN" \
       https://ghcr.io/v2/stefan-remer/timetrack/manifests/latest
   ```

### Frontend Not Loading

1. **Verify static files in container:**
   ```bash
   docker compose -f docker-compose.prod.yml exec app ls -la /app/static
   ```

2. **Check Spring Boot is serving static content:**
   ```bash
   curl -I http://localhost:8811/
   # Should return 200 OK with Content-Type: text/html
   ```

3. **Check browser console for errors**

### Performance Issues

1. **Check resource usage:**
   ```bash
   docker stats
   ```

2. **Check database connections:**
   ```bash
   docker compose -f docker-compose.prod.yml exec postgres \
       psql -U timetrack -d timetrack -c \
       "SELECT count(*) FROM pg_stat_activity;"
   ```

3. **Review application logs for slow queries**

## Security Considerations

### 1. Secrets Management

- Never commit `.env.prod` to version control
- Use strong random values for `JWT_SECRET` (minimum 256 bits)
- Use strong database passwords
- Rotate secrets regularly (at least annually)
- Consider using a secrets manager for production

### 2. CORS Configuration

The application is configured with:
```
ALLOWED_ORIGINS=https://zeit.remer.cc
```

This ensures the API only accepts requests from your domain.

### 3. HTTPS Only

Caddy automatically:
- Redirects HTTP to HTTPS
- Obtains and renews SSL certificates
- Applies security headers

**Never** expose the application on port 8811 publicly. Only Caddy should be accessible from the internet.

### 4. Database Security

- PostgreSQL is not exposed publicly (only accessible within Docker network)
- Use strong passwords
- Regular backups
- Keep PostgreSQL updated

### 5. Container Security

- Images are regularly updated in CI/CD
- Application runs as non-root user
- Keep Docker and Docker Compose updated

### 6. Firewall Configuration

Recommended firewall rules (using UFW):

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 80/tcp   # HTTP (redirects to HTTPS)
sudo ufw allow 443/tcp  # HTTPS
sudo ufw enable
```

### 7. System Updates

Keep the server updated:
```bash
sudo apt update && sudo apt upgrade -y
```

### 8. Log Monitoring

Regularly review logs for:
- Failed login attempts
- Unusual access patterns
- Application errors
- Security warnings

## Maintenance Tasks

### Daily
- Monitor application availability
- Check for error logs

### Weekly
- Review backup logs
- Check disk space usage
- Review access logs

### Monthly
- Test database restore procedure
- Review and rotate logs
- Check for security updates
- Review user access permissions

### Quarterly
- Review and update secrets (passwords, tokens)
- Perform security audit
- Review and update documentation

## Support and Documentation

- **Application Documentation**: See `README.md` in the repository
- **API Documentation**: Available at `/api/swagger-ui.html` (if enabled)
- **Development Guide**: See `CLAUDE.md` for development workflows
- **GitHub Issues**: Report issues at https://github.com/stefan-remer/timetrack/issues

## Rollback Procedure

If an update causes issues:

1. **Identify previous working version:**
   ```bash
   docker images ghcr.io/stefan-remer/timetrack
   ```

2. **Edit docker-compose.prod.yml:**
   Change:
   ```yaml
   image: ghcr.io/stefan-remer/timetrack:latest
   ```
   To:
   ```yaml
   image: ghcr.io/stefan-remer/timetrack:main-abc1234
   ```

3. **Restart containers:**
   ```bash
   docker compose -f docker-compose.prod.yml down
   docker compose -f docker-compose.prod.yml up -d
   ```

4. **If database migration is the issue:**
   Restore from backup (see Backup and Restore section)

## Useful Commands Cheat Sheet

```bash
# View all containers
docker compose -f docker-compose.prod.yml ps

# View logs (follow mode)
docker compose -f docker-compose.prod.yml logs -f

# View only app logs
docker compose -f docker-compose.prod.yml logs -f app

# Restart application (keeps data)
docker compose -f docker-compose.prod.yml restart app

# Stop all services
docker compose -f docker-compose.prod.yml down

# Start all services
docker compose -f docker-compose.prod.yml up -d

# Pull latest images
docker compose -f docker-compose.prod.yml pull

# Execute command in container
docker compose -f docker-compose.prod.yml exec app sh

# View container resource usage
docker stats

# Clean up unused Docker resources
docker system prune -a
```
