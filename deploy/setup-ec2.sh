#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 24.04 EC2 instance: Docker, swap, and the deploy files.
# Run on the instance:  curl -fsSL https://raw.githubusercontent.com/paulcartagena/icar-api/main/deploy/setup-ec2.sh | bash
set -euo pipefail

REPO_RAW=https://raw.githubusercontent.com/paulcartagena/icar-api/main/deploy
APP_DIR="$HOME/icar"

echo "==> Installing Docker"
sudo apt-get update -y
sudo apt-get install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

# t4g.micro has 1 GB of RAM; swap avoids the JVM being killed on memory spikes
if ! swapon --show | grep -q /swapfile; then
  echo "==> Adding 1 GB swap"
  sudo fallocate -l 1G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
fi

echo "==> Downloading deploy files to $APP_DIR"
mkdir -p "$APP_DIR"
curl -fsSL "$REPO_RAW/docker-compose.yml" -o "$APP_DIR/docker-compose.yml"
curl -fsSL "$REPO_RAW/Caddyfile" -o "$APP_DIR/Caddyfile"
if [ ! -f "$APP_DIR/.env" ]; then
  curl -fsSL "$REPO_RAW/.env.example" -o "$APP_DIR/.env"
  chmod 600 "$APP_DIR/.env"
fi

echo
echo "Done. Next: log out and back in (so the docker group applies), fill in $APP_DIR/.env,"
echo "then run:  cd $APP_DIR && docker compose up -d"
