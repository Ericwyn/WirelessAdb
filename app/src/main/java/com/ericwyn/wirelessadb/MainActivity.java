package com.ericwyn.wirelessadb;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.king.zxing.CameraScan;
import com.king.zxing.CaptureActivity;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Iterator;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "WirelessAdb";

    private Button btnScanQr;
    private Button btnGoToDev;
    private TextView textView;

    private int REQUEST_CODE_CR_SCAN = 10000;
    private int REQUEST_CODE_TO_DEV = 10001;

    private String deviceIp = "";
    private String adbPort = "";

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        textView = findViewById(R.id.tv_note);
        btnScanQr = findViewById(R.id.scanBtn);
        btnGoToDev = findViewById(R.id.scanGoToDev);

        btnScanQr.setOnClickListener(v -> {
            startActivityForResult(
                    new Intent(getApplicationContext(), CaptureActivity.class),REQUEST_CODE_CR_SCAN);
        });
        btnScanQr.setEnabled(false);

        btnGoToDev.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            startActivityForResult(intent,REQUEST_CODE_TO_DEV);
        });

        loadAdbMsg();
    }


    private void loadAdbMsg(){
        // 先从 sp 里面获取一次 root 权限结果, 避免多次请求 root 权限
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);

        // 如果有权限的话就是 true, 否则的话就是 false
        String rootPerm = preferences.getString("rootPerm", "null");
        if (!rootPerm.equals("true") && !RootUtils.haveRoot(preferences)) {
            textView.setText("尚未获取 Root 权限\n请确认权限");
            btnScanQr.setEnabled(false);
        } else {
            deviceIp = getIpAddress();
            int port = getAdbPort();
            if (port == -1) {
                textView.setText("无法获取无线 adb 端口\n 请尝试前往开发者模式中打开无线调试 \n 并且开启 root 权限");
                preferences.edit().putString("rootPerm", "true").apply();
                btnScanQr.setEnabled(false);
            } else {
                adbPort = "" + port;
                String text = "IP 地址: \n" + deviceIp + "\n\n";
                text += "无线 ADB 端口: \n " + port + "\n";
                textView.setText(text);
                btnScanQr.setEnabled(true);
            }
        }
    }

    private String getIpAddress(){
        return getDefaultIpAddresses(getApplication().getSystemService(ConnectivityManager.class));
    }

    private int getAdbPort(){
        // tcp6       0      0 :::39845                :::*                    LISTEN      13221/adbd
        String cmd = "netstat -nlp | grep adb | grep tcp";
        String result = RootUtils.execRootCmd(cmd);
        if (result.contains("LISTEN") && result.contains("adbd")) {
            for (int i = 0; i < 10; i++){
                result = result.replaceAll("  ", " ");
            }
            String[] split = result.split(" ");
            if (split.length < 4) {
                return -1;
            }

            if (!split[3].contains(":")) {
                return -1;
            }
            split = split[3].split(":");

            try {
                return Integer.parseInt(split[split.length - 1]);
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    private static String getDefaultIpAddresses(ConnectivityManager cm) {
        LinkProperties prop = cm.getLinkProperties(cm.getActiveNetwork());
        return formatIpAddresses(prop);
    }

    private static String formatIpAddresses(LinkProperties prop) {
        if (prop == null) {
            return null;
        }

        Iterator<LinkAddress> iter = prop.getLinkAddresses().iterator();
        // If there are no entries, return null
        if (!iter.hasNext()) {
            return null;
        }

        // Concatenate all available addresses, newline separated
        StringBuilder addresses = new StringBuilder();
        while (iter.hasNext()) {
            InetAddress addr = iter.next().getAddress();
            if (addr instanceof Inet4Address) {
                // adb only supports ipv4 at the moment
                addresses.append(addr.getHostAddress());
                break;
            }
        }
        return addresses.toString();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CR_SCAN) {
            String result = CameraScan.parseScanResult(data);
            if (data == null || result == null) {
                return;
            }
            Log.d(TAG, "扫描结果:" + result);

            String url = result + "?ip=" + deviceIp + "&port=" + adbPort;
            Log.d(TAG, "访问: " + url);

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
//                    Toast.makeText("")
                    Log.d(TAG, "连接错误" + e.getMessage());
                    showToast("连接错误" + e.getMessage());
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        if (response.code() == 200) {
                            showToast("发送 adb 信息成功");
                        } else {
                            showToast("发送 adb 信息失败, statusCode:" + response.code());
                        }
                    } else {
                        showToast("访问错误");
                    }
                }
            });
        } else if (requestCode == REQUEST_CODE_TO_DEV) {
            loadAdbMsg();
        }
    }
    private Toast toast;
    private void showToast(String msg) {
        runOnUiThread(() -> {
            if (toast != null){
                toast.cancel();
            }
            toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG);
            toast.show();
        });
    }
}