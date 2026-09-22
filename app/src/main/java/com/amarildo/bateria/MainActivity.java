package com.amarildo.bateria;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcBarcode;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {
    private static final int REQ_SAVE_JSON = 1001;

    private NfcAdapter nfcAdapter;
    private TextView statusView;
    private TextView jsonView;
    private String lastJson = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            statusView.setText("Este aparelho não possui NFC.");
        } else if (!nfcAdapter.isEnabled()) {
            statusView.setText("NFC está desligado. Ative o NFC e volte ao app.");
        } else {
            statusView.setText("Pronto. Encoste a tag/cartão NFC na traseira do aparelho.");
        }
    }

    private void buildUi() {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(15, 18, 24));

        TextView title = new TextView(this);
        title.setText("NFC Diagnóstico");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Lê somente informações que o Android disponibiliza legitimamente. Não tenta descobrir chaves, quebrar criptografia ou autenticar áreas protegidas.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(190, 195, 205));
        subtitle.setPadding(0, dp(8), 0, dp(14));
        root.addView(subtitle);

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setTextColor(Color.rgb(150, 220, 180));
        statusView.setPadding(0, 0, 0, dp(12));
        root.addView(statusView);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        Button copy = new Button(this);
        copy.setText("Copiar");
        copy.setAllCaps(false);
        copy.setOnClickListener(v -> copyJson());
        buttons.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button share = new Button(this);
        share.setText("Compartilhar");
        share.setAllCaps(false);
        share.setOnClickListener(v -> shareJson());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        bp.setMargins(dp(6), 0, dp(6), 0);
        buttons.addView(share, bp);

        Button save = new Button(this);
        save.setText("Salvar .json");
        save.setAllCaps(false);
        save.setOnClickListener(v -> saveJson());
        buttons.addView(save, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        root.addView(buttons);

        jsonView = new TextView(this);
        jsonView.setText("Nenhuma tag lida ainda.");
        jsonView.setTextSize(12);
        jsonView.setTextColor(Color.rgb(225, 228, 235));
        jsonView.setTextIsSelectable(true);
        jsonView.setTypeface(android.graphics.Typeface.MONOSPACE);
        jsonView.setPadding(0, dp(12), 0, dp(24));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(jsonView);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        root.addView(scroll, sp);

        TextView footer = new TextView(this);
        footer.setText("Depois de ler, compartilhe o JSON comigo para eu identificar a tecnologia e as limitações da credencial.");
        footer.setTextSize(12);
        footer.setTextColor(Color.rgb(160, 165, 175));
        root.addView(footer);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            if (!nfcAdapter.isEnabled()) {
                statusView.setText("NFC está desligado. Ative-o nas configurações.");
                return;
            }
            int flags = NfcAdapter.FLAG_READER_NFC_A
                    | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F
                    | NfcAdapter.FLAG_READER_NFC_V
                    | NfcAdapter.FLAG_READER_NFC_BARCODE;
            nfcAdapter.enableReaderMode(this, new NfcAdapter.ReaderCallback() {
                @Override
                public void onTagDiscovered(Tag tag) {
                    handleTag(tag);
                }
            }, flags, null);
        }
    }

    @Override
    protected void onPause() {
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableReaderMode(this);
            } catch (Exception ignored) {
            }
        }
        super.onPause();
    }

    private void handleTag(Tag tag) {
        try {
            JSONObject root = inspectTag(tag);
            lastJson = root.toString(2);
            runOnUiThread(() -> {
                jsonView.setText(lastJson);
                statusView.setText("Leitura concluída. JSON pronto para copiar, salvar ou compartilhar.");
            });
        } catch (Exception e) {
            final String msg = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            runOnUiThread(() -> statusView.setText("Erro ao interpretar a tag: " + msg));
        }
    }

    private JSONObject inspectTag(Tag tag) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", "com.amarildo.nfc-diagnostico/v1");
        root.put("capturedAtUtc", isoUtcNow());

        JSONObject app = new JSONObject();
        app.put("name", "NFC Diagnóstico");
        app.put("version", "1.0");
        app.put("purpose", "Inventário passivo das propriedades NFC expostas pela API pública do Android");
        app.put("securityNote", "Nenhuma chave é testada, derivada ou extraída; nenhuma autenticação protegida é tentada.");
        root.put("app", app);

        JSONObject device = new JSONObject();
        device.put("manufacturer", Build.MANUFACTURER);
        device.put("brand", Build.BRAND);
        device.put("model", Build.MODEL);
        device.put("device", Build.DEVICE);
        device.put("androidRelease", Build.VERSION.RELEASE);
        device.put("sdkInt", Build.VERSION.SDK_INT);
        device.put("nfcEnabled", nfcAdapter != null && nfcAdapter.isEnabled());
        device.put("androidId", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        root.put("device", device);

        JSONObject tagObj = new JSONObject();
        byte[] id = tag.getId();
        tagObj.put("uidHex", hex(id));
        tagObj.put("uidHexReversed", hexReverse(id));
        tagObj.put("uidLengthBytes", id == null ? 0 : id.length);
        tagObj.put("androidTagToString", String.valueOf(tag));

        JSONArray techs = new JSONArray();
        for (String tech : tag.getTechList()) {
            techs.put(tech);
        }
        tagObj.put("techList", techs);
        root.put("tag", tagObj);

        JSONObject tech = new JSONObject();

        NfcA nfca = NfcA.get(tag);
        if (nfca != null) {
            JSONObject o = new JSONObject();
            o.put("atqaHex", hex(nfca.getAtqa()));
            o.put("sak", nfca.getSak());
            o.put("sakHex", String.format(Locale.US, "%02X", nfca.getSak() & 0xFF));
            o.put("maxTransceiveLength", safeMaxNfcA(nfca));
            tech.put("NfcA", o);
        }

        NfcB nfcb = NfcB.get(tag);
        if (nfcb != null) {
            JSONObject o = new JSONObject();
            o.put("applicationDataHex", hex(nfcb.getApplicationData()));
            o.put("protocolInfoHex", hex(nfcb.getProtocolInfo()));
            o.put("maxTransceiveLength", safeMaxNfcB(nfcb));
            tech.put("NfcB", o);
        }

        NfcF nfcf = NfcF.get(tag);
        if (nfcf != null) {
            JSONObject o = new JSONObject();
            o.put("manufacturerHex", hex(nfcf.getManufacturer()));
            o.put("systemCodeHex", hex(nfcf.getSystemCode()));
            o.put("maxTransceiveLength", safeMaxNfcF(nfcf));
            tech.put("NfcF", o);
        }

        NfcV nfcv = NfcV.get(tag);
        if (nfcv != null) {
            JSONObject o = new JSONObject();
            o.put("dsfId", nfcv.getDsfId() & 0xFF);
            o.put("dsfIdHex", String.format(Locale.US, "%02X", nfcv.getDsfId() & 0xFF));
            o.put("responseFlags", nfcv.getResponseFlags() & 0xFF);
            o.put("responseFlagsHex", String.format(Locale.US, "%02X", nfcv.getResponseFlags() & 0xFF));
            o.put("maxTransceiveLength", safeMaxNfcV(nfcv));
            tech.put("NfcV", o);
        }

        IsoDep iso = IsoDep.get(tag);
        if (iso != null) {
            JSONObject o = new JSONObject();
            o.put("historicalBytesHex", nullableHex(iso.getHistoricalBytes()));
            o.put("hiLayerResponseHex", nullableHex(iso.getHiLayerResponse()));
            o.put("maxTransceiveLength", iso.getMaxTransceiveLength());
            o.put("extendedLengthApduSupported", iso.isExtendedLengthApduSupported());
            tech.put("IsoDep", o);
        }

        MifareClassic mc = MifareClassic.get(tag);
        if (mc != null) {
            JSONObject o = new JSONObject();
            o.put("type", mifareClassicType(mc.getType()));
            o.put("typeCode", mc.getType());
            o.put("sizeBytes", mc.getSize());
            o.put("sectorCount", mc.getSectorCount());
            o.put("blockCount", mc.getBlockCount());
            o.put("maxTransceiveLength", mc.getMaxTransceiveLength());
            o.put("note", "Somente metadados. Nenhuma autenticação de setor ou leitura de bloco protegido foi tentada.");
            tech.put("MifareClassic", o);
        }

        MifareUltralight mu = MifareUltralight.get(tag);
        if (mu != null) {
            JSONObject o = new JSONObject();
            o.put("type", mifareUltralightType(mu.getType()));
            o.put("typeCode", mu.getType());
            o.put("maxTransceiveLength", mu.getMaxTransceiveLength());
            o.put("note", "Somente identificação da tecnologia; nenhuma sequência proprietária de comandos foi enviada.");
            tech.put("MifareUltralight", o);
        }

        NfcBarcode barcode = NfcBarcode.get(tag);
        if (barcode != null) {
            JSONObject o = new JSONObject();
            o.put("type", barcode.getType());
            o.put("barcodeHex", nullableHex(barcode.getBarcode()));
            tech.put("NfcBarcode", o);
        }

        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            JSONObject o = new JSONObject();
            o.put("type", ndef.getType());
            o.put("maxSizeBytes", ndef.getMaxSize());
            o.put("writable", ndef.isWritable());
            o.put("canMakeReadOnly", ndef.canMakeReadOnly());

            NdefMessage message = ndef.getCachedNdefMessage();
            if (message != null) {
                o.put("messageByteLength", message.toByteArray().length);
                o.put("messageBase64", Base64.encodeToString(message.toByteArray(), Base64.NO_WRAP));
                JSONArray records = new JSONArray();
                for (NdefRecord record : message.getRecords()) {
                    JSONObject r = new JSONObject();
                    r.put("tnf", record.getTnf());
                    r.put("typeHex", hex(record.getType()));
                    r.put("typeAscii", asciiPreview(record.getType()));
                    r.put("idHex", hex(record.getId()));
                    r.put("payloadLength", record.getPayload() == null ? 0 : record.getPayload().length);
                    r.put("payloadBase64", Base64.encodeToString(
                            record.getPayload() == null ? new byte[0] : record.getPayload(), Base64.NO_WRAP));
                    r.put("payloadUtf8Preview", utf8Preview(record.getPayload()));
                    String mime = record.toMimeType();
                    if (mime != null) r.put("mimeType", mime);
                    Uri uri = record.toUri();
                    if (uri != null) r.put("uri", uri.toString());
                    String text = decodeNdefText(record);
                    if (text != null) r.put("decodedText", text);
                    records.put(r);
                }
                o.put("records", records);
            } else {
                o.put("message", JSONObject.NULL);
            }
            tech.put("Ndef", o);
        }

        if (NdefFormatable.get(tag) != null) {
            JSONObject o = new JSONObject();
            o.put("present", true);
            tech.put("NdefFormatable", o);
        }

        root.put("technologies", tech);

        JSONObject interpretationHints = new JSONObject();
        interpretationHints.put("isoDepPresent", iso != null);
        interpretationHints.put("mifareClassicPresent", mc != null);
        interpretationHints.put("mifareUltralightPresent", mu != null);
        interpretationHints.put("ndefPresent", ndef != null);
        interpretationHints.put("important", "A ausência de conteúdo legível não significa ausência de dados: credenciais seguras frequentemente expõem apenas identificação e parâmetros de protocolo sem autenticação.");
        root.put("interpretationHints", interpretationHints);

        return root;
    }

    private void copyJson() {
        if (!hasJson()) return;
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("NFC JSON", lastJson));
        Toast.makeText(this, "JSON copiado.", Toast.LENGTH_SHORT).show();
    }

    private void shareJson() {
        if (!hasJson()) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_SUBJECT, "Leitura NFC - JSON");
        i.putExtra(Intent.EXTRA_TEXT, lastJson);
        startActivity(Intent.createChooser(i, "Compartilhar JSON"));
    }

    private void saveJson() {
        if (!hasJson()) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "nfc-leitura-" + fileTimestamp() + ".json");
        startActivityForResult(i, REQ_SAVE_JSON);
    }

    private boolean hasJson() {
        if (lastJson == null || lastJson.isEmpty()) {
            Toast.makeText(this, "Leia uma tag NFC primeiro.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SAVE_JSON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out != null) {
                    out.write(lastJson.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Toast.makeText(this, "JSON salvo.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Falha ao salvar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private static String decodeNdefText(NdefRecord record) {
        try {
            byte[] type = record.getType();
            if (record.getTnf() != NdefRecord.TNF_WELL_KNOWN
                    || type == null
                    || type.length != 1
                    || type[0] != NdefRecord.RTD_TEXT[0]) {
                return null;
            }
            byte[] payload = record.getPayload();
            if (payload == null || payload.length == 0) return "";
            int status = payload[0] & 0xFF;
            int langLen = status & 0x3F;
            boolean utf16 = (status & 0x80) != 0;
            int start = 1 + langLen;
            if (start > payload.length) return null;
            return new String(payload, start, payload.length - start,
                    utf16 ? StandardCharsets.UTF_16 : StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String mifareClassicType(int type) {
        if (type == MifareClassic.TYPE_CLASSIC) return "CLASSIC";
        if (type == MifareClassic.TYPE_PLUS) return "PLUS";
        if (type == MifareClassic.TYPE_PRO) return "PRO";
        return "UNKNOWN";
    }

    private static String mifareUltralightType(int type) {
        if (type == MifareUltralight.TYPE_ULTRALIGHT) return "ULTRALIGHT";
        if (type == MifareUltralight.TYPE_ULTRALIGHT_C) return "ULTRALIGHT_C";
        return "UNKNOWN";
    }

    private static int safeMaxNfcA(NfcA x) {
        try { return x.getMaxTransceiveLength(); } catch (Exception e) { return -1; }
    }

    private static int safeMaxNfcB(NfcB x) {
        try { return x.getMaxTransceiveLength(); } catch (Exception e) { return -1; }
    }

    private static int safeMaxNfcF(NfcF x) {
        try { return x.getMaxTransceiveLength(); } catch (Exception e) { return -1; }
    }

    private static int safeMaxNfcV(NfcV x) {
        try { return x.getMaxTransceiveLength(); } catch (Exception e) { return -1; }
    }

    private static String hex(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format(Locale.US, "%02X", b & 0xFF));
        return sb.toString();
    }

    private static Object nullableHex(byte[] data) {
        return data == null ? JSONObject.NULL : hex(data);
    }

    private static String hexReverse(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (int i = data.length - 1; i >= 0; i--) {
            sb.append(String.format(Locale.US, "%02X", data[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String asciiPreview(byte[] data) {
        if (data == null || data.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            int c = b & 0xFF;
            sb.append(c >= 32 && c <= 126 ? (char) c : '.');
        }
        return sb.toString();
    }

    private static String utf8Preview(byte[] data) {
        if (data == null || data.length == 0) return "";
        String s = new String(data, StandardCharsets.UTF_8);
        s = s.replace("\u0000", "").replace("\r", "\\r").replace("\n", "\\n");
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }

    private static String isoUtcNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private static String fileTimestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
