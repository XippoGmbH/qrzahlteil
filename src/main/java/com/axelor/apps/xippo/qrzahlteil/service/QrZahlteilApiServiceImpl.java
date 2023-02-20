package com.axelor.apps.xippo.qrzahlteil.service;

import com.axelor.apps.base.db.repo.AppQrZahlteilRepository;
import com.axelor.apps.xippo.qrzahlteil.service.app.AppQrZahlteilService;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QrZahlteilApiServiceImpl implements QrZahlteilApiService {
	protected Logger log = LoggerFactory.getLogger(getClass());

	@Inject
	private AppQrZahlteilService appQrZahlteilService;
	@Inject
	private AppQrZahlteilRepository appQrZahlteilRepo;

	private String apiKey;
	private String sessionId;
	private URI serverUri;
	private static QrZahlteilWebSocketClient webSocketClient;

	public void connect() {
		try {
			this.apiKey = this.appQrZahlteilService.getAppQrZahlteil().getApiKey();
			this.serverUri = new URI(AppQrZahlteilRepository.QR_ZAHLTEIL_WEBSOCKET_URI);
			this.sessionId = AuthUtils.getUser().getQrZahlteilSessionId();

			QrZahlteilApiServiceImpl.webSocketClient = new QrZahlteilWebSocketClient(this.serverUri);
			QrZahlteilApiServiceImpl.webSocketClient.startConnection(this.apiKey, this.sessionId, "", 
					this.appQrZahlteilService.getAppQrZahlteil().getImageFormat().getValue(), this.appQrZahlteilService.getAppQrZahlteil().getAcceptPayslip(),
					this.appQrZahlteilService.getAppQrZahlteil().getEncryptCommunication(), "");
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (AxelorException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void disconnect() {
		if (QrZahlteilApiServiceImpl.webSocketClient != null) {
			QrZahlteilApiServiceImpl.webSocketClient.close();
		}
	}
}
