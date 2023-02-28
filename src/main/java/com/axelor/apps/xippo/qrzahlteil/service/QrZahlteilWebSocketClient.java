package com.axelor.apps.xippo.qrzahlteil.service;

import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.spec.AlgorithmParameterSpec;
import java.time.LocalDate;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.apache.commons.codec.binary.Hex;
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
  private String secretKey;
  private String initializationVector;
  private String qrCodeFilePath;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getQrCodeFilePath() {
    return qrCodeFilePath;
  }

  public void setQrCodeFilePath(String qrCodeFilePath) {
    this.qrCodeFilePath = qrCodeFilePath;
  }

  public interface QrZahlteilWebSocketClientListener {
    public void onConnected(String sessionId, String qrPath);
  }

  private QrZahlteilWebSocketClientListener listener;

  public void setListener(QrZahlteilWebSocketClientListener listener) {
    this.listener = listener;
  }

  public QrZahlteilWebSocketClient(URI serverUri) {
    super(serverUri);
    this.listener = null;
    this.mapper = new ObjectMapper();
  }

  public void startConnection(
      String apiKey,
      String sessionId,
      String identificationKey,
      int pictureFormat,
      boolean acceptKodierzeile,
      boolean encrypted,
      String licenseKey,
      String secretKey,
      String initializationVector)
      throws AxelorException {

    if (this.getReadyState() == ReadyState.OPEN) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE, "WebSocket Connection allready open!");
    }

    try {
      int retry = 0;

      this.apiKey = apiKey;
      this.setSessionId(sessionId);
      this.identificationKey = identificationKey;
      this.pictureFormat = pictureFormat;
      this.acceptKodierzeile = acceptKodierzeile;
      this.encrypted = encrypted;
      this.licenseKey = licenseKey;
      this.secretKey = secretKey;
      this.initializationVector = initializationVector;

      while (!this.isOpen() && retry < 5) {
        this.connect();
        retry += 1;
        Thread.sleep(1000);
      }

      if (this.getReadyState() != ReadyState.OPEN) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE, "Can't connect to server!");
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
      objectNode.putPOJO("sessionId", this.getSessionId());
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
      if (this.encrypted) {
        objectNode.putPOJO("data", this.encrypt(dataNode.toString()));
      } else {
        objectNode.putPOJO("data", dataNode);
      }

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

      if (!response.has("event")) {
        return;
      }

      String eventName = response.get("event").asText();
      int respsonseVersion = response.get("version").asInt();

      if (respsonseVersion != this.version) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE,
            "The Version of request and response does't match!");
      }

      if (eventName.equalsIgnoreCase("connected")) {
        this.setSessionId(response.get("sessionId").asText());
        this.licenseValidTo = LocalDate.parse(response.get("licensedUntil").asText());
        JsonNode customerParameters = response.get("custom");

        if (this.encrypted) {
          ObjectNode qrCodeContent =
              (ObjectNode) this.mapper.readTree(response.get("pairingQrCodeData").asText());
          qrCodeContent.putPOJO("secret", Hex.encodeHexString(this.secretKey.getBytes()));
          qrCodeContent.putPOJO("iv", Hex.encodeHexString(this.initializationVector.getBytes()));

          // this.setQrCodeFilePath(this.generateBarcode(qrCodeContent.toPrettyString()));
        } else {
          File qrBarcodeFile = File.createTempFile("qrcode", ".png");
          BufferedImage image =
              ImageIO.read(
                  new ByteArrayInputStream(
                      Base64.getDecoder().decode(response.get("paringQrCodeImg").asText())));
          ImageIO.write(image, "png", qrBarcodeFile);

          this.setQrCodeFilePath(qrBarcodeFile.getPath());
        }

        if (this.listener != null) {
          listener.onConnected(this.getSessionId(), this.getQrCodeFilePath());
        }
      }
    } catch (IOException e) {
      log.error(e.getMessage());
    } catch (AxelorException e) {
      log.error(e.getMessage());
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

    if (result.length() == 0) {
      if (node.has("text")) {
        result = node.get("text").asText();
      } else {
        result = "no error description found!";
      }
    }

    return result;
  }

  public String encrypt(String input) {
    try {
      byte[] key = this.secretKey.getBytes("UTF-8");
      byte[] ivs = this.initializationVector.getBytes("UTF-8");
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
      SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
      AlgorithmParameterSpec paramSpec = new IvParameterSpec(ivs);
      cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, paramSpec);
      return Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes("UTF-8")));

    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public String decrypt(String input) {
    try {
      byte[] key = this.secretKey.getBytes("UTF-8");
      byte[] ivs = this.initializationVector.getBytes("UTF-8");
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
      SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
      AlgorithmParameterSpec paramSpec = new IvParameterSpec(ivs);
      cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, paramSpec);
      byte[] decoded = Base64.getDecoder().decode(cipher.doFinal(input.getBytes("UTF-8")));
      return decoded.toString();

    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  //  private String generateBarcode(String content) {
  //    String result = "";
  //    try {
  //      QRCodeWriter qrWriter = new QRCodeWriter();
  //      BitMatrix qrImage = qrWriter.encode(content, BarcodeFormat.QR_CODE, 125, 125);
  //
  //      File qrBarcodeFile = File.createTempFile("qrcode", ".png");
  //      int matrixWidth = qrImage.getWidth();
  //      BufferedImage image = new BufferedImage(matrixWidth, matrixWidth,
  // BufferedImage.TYPE_INT_RGB);
  //      image.createGraphics();
  //
  //      Graphics2D graphics = (Graphics2D) image.getGraphics();
  //      graphics.setColor(Color.WHITE);
  //      graphics.fillRect(0, 0, matrixWidth, matrixWidth);
  //      // Paint and save the image using the ByteMatrix
  //      graphics.setColor(Color.BLACK);
  //
  //      for (int i = 0; i < matrixWidth; i++) {
  //        for (int j = 0; j < matrixWidth; j++) {
  //          if (qrImage.get(i, j)) {
  //            graphics.fillRect(i, j, 1, 1);
  //          }
  //        }
  //      }
  //      ImageIO.write(image, "png", qrBarcodeFile);
  //      result = qrBarcodeFile.getPath();
  //    } catch (WriterException e) {
  //      log.error(e.getMessage());
  //    } catch (IOException e) {
  //      log.error(e.getMessage());
  //    }
  //    return result;
  //  }
}
