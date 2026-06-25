package com.enyu.autoledger;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class CopyCarrierReceiver extends BroadcastReceiver {
    public static final String ACTION_COPY_CARRIER = "com.enyu.autoledger.action.COPY_CARRIER";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        String carrier = BarcodeUtil.normalizeCarrier(AppSettings.getString(context, AppSettings.KEY_CARRIER_BARCODE, ""));
        if (carrier.isEmpty()) {
            Toast.makeText(context, "尚未設定載具號碼", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("載具條碼", carrier));
            Toast.makeText(context, "已複製載具號碼：" + carrier, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "複製失敗，請進 App 查看載具號碼", Toast.LENGTH_SHORT).show();
        }
    }
}
