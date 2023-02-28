package com.axelor.apps.xippo.qrzahlteil.service;

import com.axelor.apps.base.db.repo.AppQrZahlteilRepository;
import com.axelor.apps.xippo.qrzahlteil.service.QrZahlteilWebSocketClient.QrZahlteilWebSocketClientListener;
import com.axelor.apps.xippo.qrzahlteil.service.app.AppQrZahlteilService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QrZahlteilApiServiceImpl
    implements QrZahlteilApiService, QrZahlteilWebSocketClientListener {
  protected Logger log = LoggerFactory.getLogger(getClass());

  @Inject private AppQrZahlteilService appQrZahlteilService;
  @Inject private AppQrZahlteilRepository appQrZahlteilRepo;
  @Inject private UserRepository userRepo;

  private String apiKey;
  private String sessionId;
  private URI serverUri;
  private String qrCodePath;
  private static QrZahlteilWebSocketClient webSocketClient;

  public String connect() {
    String result = "";
    try {
      this.apiKey = this.appQrZahlteilService.getAppQrZahlteil().getApiKey();
      this.serverUri = new URI(AppQrZahlteilRepository.QR_ZAHLTEIL_WEBSOCKET_URI);
      this.sessionId = AuthUtils.getUser().getQrZahlteilSessionId();

      QrZahlteilApiServiceImpl.webSocketClient = new QrZahlteilWebSocketClient(this.serverUri);

      QrZahlteilApiServiceImpl.webSocketClient.setListener(this);

      QrZahlteilApiServiceImpl.webSocketClient.startConnection(
          this.apiKey,
          this.sessionId,
          "",
          this.appQrZahlteilService.getAppQrZahlteil().getImageFormat().getValue(),
          this.appQrZahlteilService.getAppQrZahlteil().getAcceptPayslip(),
          this.appQrZahlteilService.getAppQrZahlteil().getEncryptCommunication(),
          "",
          this.appQrZahlteilService.getAppQrZahlteil().getEncryptionKey(),
          this.appQrZahlteilService.getAppQrZahlteil().getInitializationVector());

      if (this.qrCodePath.length() > 0) {
        return this.qrCodePath;
      }
    } catch (URISyntaxException e) {
      e.printStackTrace();
    } catch (AxelorException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return result;
  }

  public void disconnect() {
    if (QrZahlteilApiServiceImpl.webSocketClient != null) {
      QrZahlteilApiServiceImpl.webSocketClient.close();
    }
  }

  @Override
  public void onConnected(String sessionId, String qrPath) {
    User user = userRepo.find(AuthUtils.getUser().getId());
    user.setQrZahlteilSessionId(sessionId);
    userRepo.save(user);
    qrCodePath = qrPath;
    log.debug("Event wurde gefeuert!");
  }
}
