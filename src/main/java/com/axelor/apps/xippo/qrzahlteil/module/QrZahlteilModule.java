package com.axelor.apps.xippo.qrzahlteil.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.xippo.qrzahlteil.service.QrZahlteilApiService;
import com.axelor.apps.xippo.qrzahlteil.service.QrZahlteilApiServiceImpl;
import com.axelor.apps.xippo.qrzahlteil.service.app.AppQrZahlteilService;
import com.axelor.apps.xippo.qrzahlteil.service.app.AppQrZahlteilServiceImpl;

public class QrZahlteilModule extends AxelorModule {
  @Override
  protected void configure() {
    bind(AppQrZahlteilService.class).to(AppQrZahlteilServiceImpl.class);
    bind(QrZahlteilApiService.class).to(QrZahlteilApiServiceImpl.class);
  }
}
