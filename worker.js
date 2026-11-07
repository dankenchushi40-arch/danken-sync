
import { DurableObject } from "cloudflare:workers";

export class MusicRoom extends DurableObject {
  async fetch(request) {
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("WebSocket required", { status: 426 });
    }

    const url = new URL(request.url);
    const role = url.searchParams.get("role");

    if (!["main", "guest"].includes(role)) {
      return new Response("Invalid device role", { status: 400 });
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ role });

    this.broadcast({
      type: "devices",
      count: this.ctx.getWebSockets().length
    });

    server.send(JSON.stringify({
      type: "connected",
      role
    }));

    return new Response(null, {
      status: 101,
      webSocket: client
    });
  }

  broadcast(data) {
    const message = JSON.stringify(data);
    for (const socket of this.ctx.getWebSockets()) {
      try { socket.send(message); } catch {}
    }
  }

  webSocketMessage(socket, message) {
    const info = socket.deserializeAttachment();

    if (info?.role !== "main") {
      socket.send(JSON.stringify({
        type: "error",
        message: "Only the main device can control playback."
      }));
      return;
    }

    let data;
    try {
      data = JSON.parse(message);
    } catch {
      return;
    }

    if (!["play", "pause", "seek"].includes(data.action)) return;

    this.broadcast({
      type: "command",
      action: data.action,
      position: Number(data.position) || 0
    });
  }

  webSocketClose() {
    this.broadcast({
      type: "devices",
      count: this.ctx.getWebSockets().length
    });
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/ws") {
      const room = (url.searchParams.get("room") || "").toUpperCase();
      const role = url.searchParams.get("role");

      if (!/^[A-Z0-9]{6}$/.test(room)) {
        return new Response("Invalid room code", { status: 400 });
      }

      const id = env.ROOMS.idFromName(room);
      return env.ROOMS.get(id).fetch(request);
    }

    return env.ASSETS.fetch(request);
  }
};
