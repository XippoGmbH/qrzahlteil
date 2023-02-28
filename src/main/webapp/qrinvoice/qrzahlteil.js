/* Copyright (C) radynamics Reto Steimen, All Rights Reserved
 * Feel free to copy and modify for your own integration of QR-Zahlteil API, v1.0.11
 * Written by Reto Steimen <rsteimen@radynamics.com>, 2022
 */
class QrZahlteil {
    static defaultServiceUri() { return "wss://qrzahlteil.ch/api1/"; }
    static keepAliveDisabled() { return 0; }

    constructor(apiKey, deviceName, serviceUri) {
        this._triggers = {};
        this._serviceUri = serviceUri == null ? QrZahlteil.defaultServiceUri() : serviceUri;
        this._apiKey = apiKey == null ? "" : apiKey;
        this._deviceName = deviceName == null ? "WebClient" : deviceName;
        this._prop = null;
        // Die verwendete Protokollversion dieser API. Es sollte immer die aktuellste Version verwendet werden. Ältere Versionen unterliegen Einschränkungen und unterstützen teilweise nur einen reduzierten Funktionsumfang.
        this._version = 2;
        this._encryption = null;
        this._createQrCodeImage = null;
    }

    connect(prop) {
        prop = (typeof prop !== "object") ? {} : prop;
        prop.sessionId = prop.sessionId || "";
        prop.pictureFormat = prop.pictureFormat || null;
        prop.identificationKey = prop.identificationKey || "";
        this._prop = prop;

        var instance = this
        this._socket = new WebSocket(this._serviceUri);
        this._socket.onopen = function(e) { instance.connectWs(); };
        this._socket.onmessage = function(event) { instance.handleMessage(event) };
        this._socket.onclose = function(event) {
            if (!event.wasClean) {
                // Kann bei Netzwerkunterbruch auftreten oder wenn der Server die Verbindung von seiner Seite her schliesst (normalerweise mit code 1006).
                instance.triggerHandler('error', { message: `Verbindung zum Server wurde unerwartet geschlossen`, error: { code: event.code, key: event.code, text: event.reason } });
            }
            instance.triggerHandler('disconnected', {});
        };
    }

    // Behandelt vom Server empfangene Antworten und Meldungen
    handleMessage(msg) {
        if(msg.data == null) {
            this.triggerHandler('error', { message: `Keine Daten erhalten`, error: null });
            return;
        }
        var json = JSON.parse(msg.data);
        if(json.error != null) {
            this.triggerHandler('error', { message: this.getErrorText(json.error), error: json.error });
            return;
        }
        var response = json.response;
        if(response == null || response.event == null) {
            this.triggerHandler('error', { message: `Keine Antwort oder kein Ereignis erhalten`, error: json.error });
            return;
        }

        // Die SmartphoneApp antwortet normalerweise in der Protokollversion, welche bei "connect" übergeben wurde. Wenn eine aktuelle Version der SmartphoneApp mit einer zu alten Version Ihrer Applikation verwendet wird, ist dies evtl. nicht mehr möglich. Der Anwender sollte in diesem Fall auf ein notwendiges Update aufmerksam gemacht werden.
        if(response.version != this._version) {
            this.triggerHandler('error', { message: `Bei '${response.event}' wurde die Datenstruktur in der Version ${this._version} erwartet aber ${response.version} geliefert. Bitte aktualisieren Sie Ihre empfangende Applikation.`, error: null });
            return;
        }

        // Es wurde erfolgreich eine Verbindung zum Server hergestellt
        if (response.event == "connected") {
            // Speichern Sie die [sessionId] und übergeben Sie diese bei künftigen Verbindungen an "connect". Damit wird für den Benutzer eine automatische Verbindung zwischen Ihrem Programm und dem Smartphone ermöglicht.
            this._prop.sessionId = response.sessionId;

            var pairingQrCodeImg = null;
            if(this.isEncrypted()) {
                // [pairingQrCodeData] enthält die Daten, mit welchen ein eigenes [pairingQrCodeImg] generiert werden muss, wenn [secret] definiert ist und damit Ende-zu-Ende Verschlüsselung eingesetzt wird.
                var qrCodeContent = JSON.parse(response.pairingQrCodeData);
                // Damit nur das Smartphone den verwendeten Schlüssel/Secret kennt, muss dieses manuell dem QR-Code zur Kopplung hingefügt werden. Die Werte werden als Hex übergeben.
                qrCodeContent.secret = this._encryption.secret;
                qrCodeContent.iv = this._encryption.iv;
                pairingQrCodeImg = this._createQrCodeImage(JSON.stringify(qrCodeContent));
            } else {
                // [pairingQrCodeImg] enthält ein Base64 kodiertes Bild als PNG, welches dem Benutzer zur Kopplung zwischen Smartphone und Ihrem Programm angezeigt werden muss. Es wird nur geliefert, wenn [secret] undefiniert ist und damit keine Ende-zu-Ende Verschlüsselung eingesetzt wird.
                pairingQrCodeImg = response.pairingQrCodeImg;
            }
            this.triggerHandler('connected', { sessionId: this._prop.sessionId, base64Png: pairingQrCodeImg });
        }

        // Der Benutzer hat das Smartphone erfolgreich mit Ihrem Programm verbunden
        if (response.event == "scanner_connected") {
            // Bei eingesetzter Ende-zu-Ende Verschlüsselung müssen erhaltene Daten vorgängig entschlüsselt werden.
            var data = this.getPlaintextData(response.data);
            // [scannerName] enthält den Base64 kodierten Namen des Smartphones.
            this.triggerHandler('scanner_connected', { scannerName: this.b64Decode(data.scannerName) });
        }

        // Der Benutzer hat eine QR-Rechnung erfolgreich gescannt
        if (response.event == "scanned") {
            // Bei eingesetzter Ende-zu-Ende Verschlüsselung müssen erhaltene Daten vorgängig entschlüsselt werden.
            var data = this.getPlaintextData(response.data);

            // [type] enthält den erkannten Typ der gescannten Daten
            var type = data.type;
            // [data_base64] enthält den Base64 kodierten QR-Zahlteil der QR-Rechnung
            var raw = this.b64Decode(data.data_base64);
            // [pictures_base64] enthält eine Liste von Base64 kodierten Bildern als JPEG, welche der Benutzer von der QR-Rechnung erstellt hat. Wenn bei "connect" Ihre Applikation mit dem Server [pictureFormat] nicht definiert wurde oder null war, wird eine leerer Auflistung geliefert.
            // [pdf_base64] enthält ein als Base64 kodiertes PDF der aufgenommenen Bilder, wenn bei "connect" für [pictureFormat] "pdf" definiert wurde.
            this.triggerHandler('scanned', { raw: raw, picturesBase64: data.pictures_base64, pdfBase64: data.pdf_base64 });
        }

        // Der Benutzer hat die App auf dem Smartphone geschlossen
        if (response.event == "scanner_disconnected") {
            this.triggerHandler('scanner_disconnected', {});
        }
    }

    connectWs() {
        var data = {
            // Base64 kodierter Name des Gerätes oder der Website
            deviceName: this.b64Encode(this._deviceName),
        };

        var json = {
            method: "connect",
            // Verwendete Version der Parameterstruktur
            version: this._version,
            // Gespeicherte sessionId oder leer ""
            sessionId: this._prop.sessionId,
            // Format [separatePictures, pdf], falls der Benutzer die gesamte QR-Rechnung fotografieren soll und diese Fotos von Ihrer Applikation empfangen werden können. null, falls keine Bilder aufgenommen werden sollen.
            pictureFormat: this._prop.pictureFormat,
            // True, falls die Daten zwischen Ihrer Applikation und der Smartphone App mit [secret] Ende-zu-Ende verschlüsselt werden sollen.
            encrypted: this.isEncrypted(),
            // Key als Freitext zur Identifikation einer Installation oder eines Anwenders, welcher zu jedem Scan im Klartext gespeichert wird.
            identificationKey: this._prop.identificationKey,
            // Ihr ApiKey (kann auf https://www.qrzahlteil.ch/ generiert werden)
            apiKey: this._apiKey,
            data: this.isEncrypted() ? this.encrypt(JSON.stringify(data)) : data
        }
        // Das JSON Objekt als Text senden
        this._socket.send(JSON.stringify(json));
    }

    close() {
        this._socket.close(1000, "client_close");
    }

    on(event, callback) {
        if(!this._triggers[event]) {
            this._triggers[event] = [];
        }
        this._triggers[event].push(callback);
    }

    // Setzt den Schlüssel/Secret in Hex für die Ver-/ und Entschlüsselung der übertragenen Nutzdaten.
    setEncryption(secretHex, ivHex, createQrCodeImage) {
        if(secretHex == null || secretHex == '') {
            this._encryption = null;
            this._createQrCodeImage = null;
        } else {
            if(secretHex.length != 32 * 2 && secretHex.length != 256 * 2) {
                throw new Error(`Bei AES muss das Secret 32 oder 256 Bytes sein.`);
            }
            const IV_LENGTH = 16;
            if(ivHex == null || ivHex.length != IV_LENGTH * 2) {
                throw new Error(`Bei AES muss der Initialisierungsvektor (IV) 16 Bytes sein.`);
            }
            if(createQrCodeImage == null) {
                throw new Error(`Bei eingesetzter Verschlüsselung muss mittels [createQrCodeImage] eine Funktion zur Generierung eines QR-Codes definiert werden.`);
            }
            this._encryption = { secret: secretHex, iv: ivHex};
            this._createQrCodeImage = createQrCodeImage;
        }
    }

    // True, wenn ein Schlüssel/Secret und damit Ende-zu-Ende Verschlüsselung eingesetzt wird.
    isEncrypted() {
        return this._encryption != null;
    }

    // Liefert [data] als JSON Objekt im Klartext.
    getPlaintextData(data) {
        // Bei eingesetzter Ende-zu-Ende Verschlüsselung müssen erhaltene Daten vorgängig entschlüsselt werden.
        return this.isEncrypted() ? JSON.parse(this.decrypt(data)) : data;
    }

    // Liefert [value] mit dem [secret] verschlüsselt.
    encrypt(value) {
        try {
            var o = CryptoJS.AES.encrypt(value, CryptoJS.enc.Hex.parse(this._encryption.secret), this.createEncryptionParameter());
            // returns base64 encoded
            return o.toString();
        } catch (e) {
            throw e;
        }
    }

    // Liefert [value] mit dem [secret] entschlüsselt.
    decrypt(value) {
        try {
            var o = CryptoJS.AES.decrypt(value, CryptoJS.enc.Hex.parse(this._encryption.secret), this.createEncryptionParameter());
            return o.toString(CryptoJS.enc.Utf8);
        } catch (e) {
            throw e;
        }
    }

    // Liefert die verwendeten Parameter der Verschlüsselung
    createEncryptionParameter() {
        return {
            iv: CryptoJS.enc.Hex.parse(this._encryption.iv),
            padding: CryptoJS.pad.Pkcs7,
            mode: CryptoJS.mode.CBC
        };
    }

    // Kann alternative Service Uri liefern, sofern der produktive Server temporär nicht erreichbar ist.
    // Alternative Service Uri sind variabel und nicht ständig erreichbar. Versuchen Sie immer zuerst den produktiven Server zu erreichen.
    async getFailoverServer() {
        const fallbackServers = [
            { uri: "wss://qrzahlteilapp.azurewebsites.net/", keepAliveIntervalSecs: 60 },
            { uri: "wss://failover.qrzahlteil.ch/api/", keepAliveIntervalSecs: 0 }
        ];

        var servers = await this.getFailoverServerFromWebSource();
        if(servers == null) {
            servers = fallbackServers;
        }

        const defaultService = { uri: this._serviceUri, keepAliveIntervalSecs: QrZahlteil.keepAliveDisabled() };
        if(servers.length == 0) {
            return defaultService;
        }

        servers.unshift(defaultService);
        
        var current = this._serviceUri;
        // Currently used entry may not included (ex. testing)
        var indexCurrent = -1;
        for (var i = 0; i < servers.length; i++) {
            if (servers[i].uri == current) {
                indexCurrent = i;
            }
        }
        // Currently used last item -> take first entry again
        var usedLast = indexCurrent + 1 >= servers.length;
        if (indexCurrent == -1 || usedLast) {
            return servers[0];
        }

        return servers[indexCurrent + 1];
    }

    async getFailoverServerFromWebSource() {
        try {
            const response = await fetch('https://www.radynamics.com/qrzahlteilfailover/');
            if (!response.ok) {
                console.warn(`An error has occured: ${response.status}`);
                return null;
            }
            var uris = await response.json();

            var servers = [];
            for(var i = 0; i < uris.length; i++) {
                servers.push( { uri: uris[i].uri, keepAliveIntervalSecs: uris[i].keepAliveIntervalSecs } );
            }
            return servers;
        } catch(e) {
            // Exception occures if no internet connection is available
            // Exception thrown on timeout
            // Ignore all other issues (ex. malformed json)
            console.warn(`Failover Liste konnte nicht geladen werden: ${e}`);
        }
        return null;
    }

    getErrorText(error) {
        switch(error.key) {
            case "scanner_encryption_mismatch":
                return `Die SmartphoneApp hat Daten nicht in der erwarteten Verschlüsselung übertragen. Bitte aktualisieren Sie die App falls notwendig und setzen Sie die automatische Verbindung zwischen Smartphone und dieser Applikation zurück.`;
            default:
                return `Der Server hat einen Fehler gemeldet.`;
        }
    }

    triggerHandler(event,params) {
        if(this._triggers[event] ) {
            for(var i in this._triggers[event]) {
                this._triggers[event][i](params);
            }
        }
    }

    b64Decode(str) {
        return decodeURIComponent(atob(str).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
    }
    b64Encode(str) {
        return btoa(encodeURIComponent(str).replace(/%([0-9A-F]{2})/g,
            function toSolidBytes(match, p1) {
                return String.fromCharCode('0x' + p1);
        }));
    }
};