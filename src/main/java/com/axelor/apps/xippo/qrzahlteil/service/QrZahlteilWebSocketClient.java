package com.axelor.apps.xippo.qrzahlteil.service;

import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QrZahlteilWebSocketClient extends WebSocketClient {

	protected Logger log = LoggerFactory.getLogger(getClass());

	private ObjectMapper mapper;

	private int version = 2;
	private String apiKey = "";
	private String sessionId = "";
	private String identificationKey = "";
	private int pictureFormat = 0;
	private boolean acceptKodierzeile = true;
	private boolean encrypted = false;
	private String licenseKey = "";
	private LocalDate licenseValidTo;

	public QrZahlteilWebSocketClient(URI serverUri) {
		super(serverUri);
		this.mapper = new ObjectMapper();
	}

	public void startConnection(String apiKey, String sessionId, String identificationKey, int pictureFormat,
			boolean acceptKodierzeile, boolean encrypted, String licenseKey) throws AxelorException {
		if (this.getReadyState() == ReadyState.OPEN) {
			throw new AxelorException(TraceBackRepository.CATEGORY_NO_VALUE, "WebSocket Connection allready open!");
		}

		try {
			int retry = 0;
			
			this.apiKey = apiKey;
			this.sessionId = sessionId;
			this.identificationKey = identificationKey;
			this.pictureFormat = pictureFormat;
			this.acceptKodierzeile = acceptKodierzeile;
			this.encrypted = encrypted;
			this.licenseKey = licenseKey;

			while (!this.isOpen() && retry < 5) {
				this.connect();
				retry += 1;
				Thread.sleep(1000);
			}

			if(this.getReadyState() != ReadyState.OPEN) {
				throw new AxelorException(TraceBackRepository.CATEGORY_NO_VALUE, "Can't connect to server!");
			}
			this.register("axelor ERP");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void register(String deviceName) {
		try {
			ObjectNode objectNode = this.mapper.createObjectNode();
			ObjectNode dataNode = this.mapper.createObjectNode();
			
			dataNode.putPOJO("deviceName", "UXJaYWhsdGVpbC5jaCBEZW1v");
			
			objectNode.putPOJO("method", "connect");
			objectNode.putPOJO("version", this.version);
			objectNode.putPOJO("sessionId", this.sessionId);
			objectNode.putPOJO("apiKey", this.apiKey);
			objectNode.putPOJO("identificationKey", this.identificationKey);
			switch (this.pictureFormat) {
			case 1:
				objectNode.putPOJO("pictureFormat", "separatePictures");
				break;
			case 2:
				objectNode.putPOJO("pictureFormat", "pdf");
				break;
			default:
				objectNode.putPOJO("pictureFormat", null);
				break;
			}
			objectNode.putPOJO("acceptKodierzeile", this.acceptKodierzeile);
			objectNode.putPOJO("encrypted", this.encrypted);
			objectNode.putPOJO("licenseKey", this.licenseKey);
			objectNode.putPOJO("productVersion", "6.4.5");
			objectNode.putPOJO("data", dataNode);

			String json = this.mapper.writeValueAsString(objectNode);

			this.send(json);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		log.debug("Open WebSocket Connection");
	}

	@Override
	public void onMessage(String message) {
		try {
			log.debug("Receive WebSocket Message");
			JsonNode jsonNode = this.mapper.readTree(message);

			if (jsonNode.has("error")) {
				String errorText = this.getErrorMessage(jsonNode.get("error"));
				log.debug("Receive WebSocket Error Message: " + errorText);
			}

			if (!jsonNode.has("response")) {
				return;
			}
			
			JsonNode response = jsonNode.get("response");
			
			if(!response.has("event")) {
				return;
			}
			
			String eventName = response.get("event").asText();
			int respsonseVersion = response.get("version").asInt();
			
			if(respsonseVersion != this.version) {
				throw new AxelorException(TraceBackRepository.CATEGORY_NO_VALUE, "The Version of request and response does't match!");
			}
			
			if(eventName == "connected") {
				this.sessionId = response.get("sessionId").asText();
				this.licenseValidTo =  LocalDate.parse(response.get("licensedUntil").asText());
				JsonNode customerParameters = response.get("custom");
			}
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AxelorException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
	
	private String getErrorMessage(JsonNode node) {
		String result = "";
		
		String key = node.get("key").textValue();
		
		switch (key) {
		case "scanner_encryption_mismatch":
			result = "Scannerencryption mismatch!";
			break;
		case "licensekey_in_use":
			result = "license key is in use!";
			break;
		default:
			break;
		}
		
		if(result.length() == 0) {
			if(node.has("text")) {
				result = node.get("text").asText();
			} else {
				result = "no error description found!";
			}
		}
		
		return result;
	}

	public static String encrypt(String algorithm, String input, SecretKey key,
		    IvParameterSpec iv) throws NoSuchPaddingException, NoSuchAlgorithmException,
		    InvalidAlgorithmParameterException, InvalidKeyException,
		    BadPaddingException, IllegalBlockSizeException {
		    
		    Cipher cipher = Cipher.getInstance(algorithm);
		    cipher.init(Cipher.ENCRYPT_MODE, key, iv);
		    byte[] cipherText = cipher.doFinal(input.getBytes());
		    return Base64.getEncoder()
		        .encodeToString(cipherText);
		}
}
