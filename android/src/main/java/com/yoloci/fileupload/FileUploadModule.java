package com.yoloci.fileupload;

import android.os.Bundle;
import android.net.Uri;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.modules.network.ForwardingCookieHandler;

import java.io.DataInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.io.OutputStreamWriter;

import com.facebook.react.bridge.WritableMap;
import java.io.FileInputStream;
import java.io.InputStream;

import org.json.JSONObject;

public class FileUploadModule extends ReactContextBaseJavaModule {

    private ForwardingCookieHandler cookieHandler;

    @Override
    public String getName() {
        return "FileUpload";
    }

    public FileUploadModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.cookieHandler = new ForwardingCookieHandler(reactContext);
    }

    @ReactMethod
    public void upload(final ReadableMap options, final Callback callback) {
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary =  "*****";

        String uploadUrl = options.getString("uploadUrl");
        String method;
        if (options.hasKey("method")) {
            method = options.getString("method");
        } else {
            method = "POST";
        }

        ReadableMap headers = options.getMap("headers");
        ReadableArray files = options.getArray("files");
        ReadableMap fields = options.getMap("fields");

        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;
        URL connectURL = null;
        URI connectURI = null;
        //FileInputStream fileInputStream = null;
        InputStream fileInputStream = null;

        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1*1024*1024;

        try {
            connectURL = new URL(uploadUrl);
            connectURI = new URI(uploadUrl);
            connection = (HttpURLConnection) connectURL.openConnection();

            // Allow Inputs &amp; Outputs.
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            connection.setRequestMethod(method);

            Map<String, List<String>> cookieMap = this.cookieHandler.get(connectURI,  new HashMap());
            List<String> cookieList = cookieMap.get("Cookie");
            if (cookieList != null) {
                connection.setRequestProperty("Cookie", cookieList.get(0));
            }

            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "multipart/form-data; charset=utf-8; boundary="+boundary);
            connection.setRequestProperty("Accept-Charset","utf-8");
            connection.setRequestProperty("Charset","utf-8");

            // set headers
            ReadableMapKeySetIterator iterator = headers.keySetIterator();
            while (iterator.hasNextKey()) {
                String key = iterator.nextKey();
                connection.setRequestProperty(key, headers.getString(key));
            }

            outputStream = new DataOutputStream( connection.getOutputStream() );

            // set fields
            ReadableMapKeySetIterator fieldIterator = fields.keySetIterator();
            while (fieldIterator.hasNextKey()) {
                outputStream.writeBytes(twoHyphens + boundary + lineEnd);
                String key = fieldIterator.nextKey();
                Log.i(this.getName(), "Key " + key + " " + fields.getString(key));
                outputStream.writeBytes("Content-Disposition: form-data; name=\"" + key +  "\"" + lineEnd + "Content-Type: text/plain; charset=utf-8" + lineEnd + lineEnd);
                connection.getOutputStream().write(fields.getString(key).getBytes("UTF-8"));
                outputStream.writeBytes(lineEnd);
            }


            for (int i = 0; i < files.size(); i++) {

                Log.i(this.getName(), "Load file");

                ReadableMap file = files.getMap(i);
                String filename = file.getString("filename");
                String filepath = file.getString("filepath");
                String filetype = file.getString("filetype");
                String fileId = file.getString("name");
                //filepath = filepath.replace("file://", "");
                //fileInputStream = new FileInputStream(filepath);
                fileInputStream = getReactApplicationContext().getContentResolver().openInputStream(Uri.parse(filepath));

                Log.i(this.getName(), "Loaded file");

                outputStream.writeBytes(twoHyphens + boundary + lineEnd);
                outputStream.writeBytes("Content-Disposition: form-data; name=\"" + fileId + "\";filename=\"" + filename + "\"" + lineEnd);
                outputStream.writeBytes("Content-Type: " + filetype + lineEnd);

                outputStream.writeBytes(lineEnd);

                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                // Read file
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {
                    outputStream.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }

                outputStream.writeBytes(lineEnd);
            }

            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            // Responses from the server (code and message)
            int serverResponseCode = connection.getResponseCode();
            String serverResponseMessage = connection.getResponseMessage();
            Log.i(this.getName(), "Got response");

            if (serverResponseCode != 200 && serverResponseCode != 201) {
                Log.i(this.getName(), "Not 200 " + serverResponseCode);
                callback.invoke("Error happened: " + serverResponseMessage, null);

                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                //out.close();
                outputStream.flush();
                outputStream.close();

            } else {
                Log.i(this.getName(), "Ok");

                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                String data = sb.toString();
                JSONObject mainObject = new JSONObject();
                mainObject.put("data", data);
                mainObject.put("status", serverResponseCode);

                BundleJSONConverter bjc = new BundleJSONConverter();
                Bundle bundle = bjc.convertToBundle(mainObject);
                WritableMap map = Arguments.fromBundle(bundle);

                callback.invoke(null, map);

                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                //out.close();
                outputStream.flush();
                outputStream.close();
            }


            Log.i(this.getName(), "End");

        } catch(Exception ex) {
            Log.i(this.getName(), "Exception");
            callback.invoke("Error happened: " + ex.getMessage(), null);
        }
    }
}
