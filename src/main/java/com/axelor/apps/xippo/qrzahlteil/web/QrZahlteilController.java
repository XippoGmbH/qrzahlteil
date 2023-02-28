package com.axelor.apps.xippo.qrzahlteil.web;

import com.axelor.apps.xippo.qrzahlteil.service.QrZahlteilApiService;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class QrZahlteilController {

  public void connect(ActionRequest request, ActionResponse response) {

    try {
      Beans.get(QrZahlteilApiService.class).connect();
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void disconnect(ActionRequest request, ActionResponse response) {

    try {
      Beans.get(QrZahlteilApiService.class).disconnect();
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
