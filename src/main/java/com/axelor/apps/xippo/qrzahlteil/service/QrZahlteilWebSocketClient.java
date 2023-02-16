package com.axelor.apps.xippo.qrzahlteil.service;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QrZahlteilWebSocketClient extends WebSocketClient {
	
	protected Logger log = LoggerFactory.getLogger(getClass());

	public QrZahlteilWebSocketClient(URI serverUri) {
		super(serverUri);
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		log.debug("Open WebSocket Connection");
		
	}

	@Override
	public void onMessage(String message) {
		log.debug("Receive WebSocket Message");
		
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		log.debug("Close WebSocket Connection");
		
	}

	@Override
	public void onError(Exception ex) {
		log.error("WebSocket Error");
		log.error(ex.toString());
	}

}
