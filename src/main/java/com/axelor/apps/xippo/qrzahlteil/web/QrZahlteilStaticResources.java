package com.axelor.apps.xippo.qrzahlteil.web;

import com.axelor.web.StaticResourceProvider;
import java.util.List;

public class QrZahlteilStaticResources implements StaticResourceProvider {

  @Override
  public void register(List<String> resources) {
    resources.add("qrinvoice/crypto-js/sha256.js");
    resources.add("qrinvoice/crypto-js/md5.js");
    resources.add("qrinvoice/crypto-js/aes.js");
    resources.add("qrinvoice/qrcode/qrcode.js");
    resources.add("qrinvoice/qrzahlteil.js");
    resources.add("qrinvoice/gisler.js");
  }
}
