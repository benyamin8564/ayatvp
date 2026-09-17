package com.ayat64.airvpn

import com.wireguard.android.backend.GoBackend

/**
 * Real WireGuard service supplied by the official WireGuard Android tunnel library.
 * GoBackend creates the Android TUN interface and protects the WireGuard sockets
 * from being routed back into the VPN.
 */
class AiVpnService : GoBackend.VpnService()
