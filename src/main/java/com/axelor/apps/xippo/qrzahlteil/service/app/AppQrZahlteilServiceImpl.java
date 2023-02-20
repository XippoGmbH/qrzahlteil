package com.axelor.apps.xippo.qrzahlteil.service.app;

import com.axelor.apps.base.db.AppQrZahlteil;
import com.axelor.apps.base.db.repo.AppQrZahlteilRepository;
import com.axelor.apps.base.service.app.AppBaseServiceImpl;
import com.google.inject.Inject;

public class AppQrZahlteilServiceImpl extends AppBaseServiceImpl implements AppQrZahlteilService {

  @Inject private AppQrZahlteilRepository appQrZahlteilRepo;

  @Override
  public AppQrZahlteil getAppQrZahlteil() {
    return appQrZahlteilRepo.all().fetchOne();
  }
}
