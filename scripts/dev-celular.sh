#!/usr/bin/env bash
# Teste da Sala Psiconet pelo celular (mesmo Wi-Fi do computador), sem conta em nenhum serviço.
#
# O navegador só libera câmera/microfone em HTTPS, então:
#   - túnel HTTPS temporário (cloudflared, sem conta) para o front (:3000) e para o LiveKit local (:7880);
#   - o LiveKit passa a ouvir na rede local e anuncia o IP do computador: o áudio/vídeo vai direto entre o
#     celular e o computador pelo Wi-Fi (portas 7881/tcp e 50000-50100/udp, liberadas no perfil padrão do
#     firewall do Fedora Workstation).
#
# Uso:  scripts/dev-celular.sh [IP-do-computador]   # sobe tudo e ajusta VIDEO_URL no .env
#       scripts/dev-celular.sh stop                 # desfaz (volta ao LiveKit só em localhost)
# Depois de subir ou parar: reinicie o backend (VIDEO_URL é lida na inicialização).
# Requer: cloudflared no PATH, docker, `npm run dev` rodando no front.
set -euo pipefail
cd "$(dirname "$0")/.."

STATE_DIR="${XDG_RUNTIME_DIR:-/tmp}/psiconet-dev-celular"
mkdir -p "$STATE_DIR"

stop_tunnels() {
  for name in front livekit; do
    if [[ -f "$STATE_DIR/$name.pid" ]]; then
      kill "$(cat "$STATE_DIR/$name.pid")" 2>/dev/null || true
      rm -f "$STATE_DIR/$name.pid"
    fi
  done
}

# Troca (ou acrescenta) uma variável no .env sem mexer no resto do arquivo.
set_env() {
  local key=$1 value=$2
  if grep -q "^$key=" .env; then
    sed -i "s#^$key=.*#$key=$value#" .env
  else
    printf '%s=%s\n' "$key" "$value" >> .env
  fi
}

if [[ "${1:-}" == "stop" ]]; then
  stop_tunnels
  set_env VIDEO_URL "ws://localhost:7880"
  sed -i '/^VIDEO_API_URL=/d' .env
  docker compose --profile video up -d --force-recreate livekit >/dev/null
  echo "Pronto: túneis encerrados e LiveKit de volta a localhost. Reinicie o backend."
  exit 0
fi

command -v cloudflared >/dev/null || { echo "cloudflared não encontrado no PATH." >&2; exit 1; }
IP="${1:-$(ip -4 route get 1.1.1.1 | awk '{for (i = 1; i <= NF; i++) if ($i == "src") print $(i + 1)}')}"
[[ -n "$IP" ]] || { echo "Não foi possível descobrir o IP do computador; informe como argumento." >&2; exit 1; }

stop_tunnels
LIVEKIT_BIND=0.0.0.0 LIVEKIT_NODE_IP="$IP" docker compose --profile video up -d --force-recreate livekit >/dev/null

start_tunnel() {
  local name=$1 target=$2
  : > "$STATE_DIR/$name.log"
  setsid nohup cloudflared tunnel --no-autoupdate --url "$target" > "$STATE_DIR/$name.log" 2>&1 &
  echo $! > "$STATE_DIR/$name.pid"
}

tunnel_url() {
  local name=$1 url=""
  for _ in $(seq 1 30); do
    url=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$STATE_DIR/$name.log" | head -1 || true)
    [[ -n "$url" ]] && { echo "$url"; return 0; }
    sleep 1
  done
  echo "Túnel '$name' não subiu; veja $STATE_DIR/$name.log" >&2
  return 1
}

start_tunnel front http://localhost:3000
start_tunnel livekit http://localhost:7880
FRONT_URL=$(tunnel_url front)
LIVEKIT_URL=$(tunnel_url livekit)

# O navegador conecta pelo túnel (wss://); o backend continua falando com o LiveKit direto.
set_env VIDEO_URL "wss://${LIVEKIT_URL#https://}"
set_env VIDEO_API_URL "http://localhost:7880"

cat <<EOF

Sala Psiconet pelo celular — pronto.
  1. Reinicie o backend (para ler o novo VIDEO_URL).
  2. No celular (mesmo Wi-Fi), abra:  $FRONT_URL
  3. Para encerrar:  scripts/dev-celular.sh stop

LiveKit anunciando o IP $IP. Os endereços dos túneis mudam a cada execução.
EOF
