package com.piled.winnie;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.LinkedBlockingDeque;

import android.os.Environment;
import android.util.Log;

public class ItemLoader {
    private static final String TAG = "winnie::loader";

    // TODO should be changed to app local files space (but it's easier to debug in that location)
    public static String THUMBNAIL_PATH = Environment.getExternalStorageDirectory().getPath() + "/categories";

    private static LinkedBlockingDeque<Item> mRequests = new LinkedBlockingDeque<Item>();
    private static int sLoadersRunning = 0;
    private static itemReady mCallback;

    interface itemReady {
        void onReady(Item item);
    }

    public static void needItem(Item item) {
        Log.d(TAG, "needItem(): " + item);
        mRequests.offer(item);
        if (sLoadersRunning == 0) {
            sLoadersRunning++;
            Thread runner = new Thread(new Loader());
            runner.start();
        }
    }

    // right now it is a generic callback (one for all items), should be changed to individual
    public static void setCallback(itemReady callback) {
        Log.d(TAG, "setCallback " + callback);
        mCallback = callback;
    }

    private static long download(String filename, String path) {
        int code = 1;
        Log.d(TAG, "download() " + path + " -> " + filename);
        File dest = new File(filename);
        if (dest.exists()) {
            Log.d(TAG, "download(): already exist " + filename);
            return 0l;
        }
        File file = new File(filename + "." + System.currentTimeMillis() + ".tmp");
        try {
            int lastSlash = filename.lastIndexOf('/');
            if (lastSlash > 0) {
                new File(filename.substring(0, lastSlash + 1)).mkdirs();
            }
            URL url = new URL(path);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setUseCaches(false);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Connection", "Keep-Alive");
            code = connection.getResponseCode();
            Log.d(TAG, "download() ret code: " + code);
            InputStream input = connection.getInputStream();
            FileOutputStream output = new FileOutputStream(file);
            byte data[] = new byte[1024 * 16];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                output.write(data, 0, count);
            }
            output.flush();
            output.close();
            input.close();
            if (file.exists()) {
                file.setReadable(true, false);
            }
            file.renameTo(dest);
            return (code >= 200 && code < 400) ? total : -code;
        } catch (IOException e) {
            if (code == 415) {
                Log.d(TAG, "IOException: " + e);
            } else {
                Log.e(TAG, "", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
        file.delete();
        return -code;
    }

    private static class Loader implements Runnable {
        public void run() {
            try {
                while (true) {
                    final Item item = mRequests.take();
                    try {
                        int lastSlash = item.icon.lastIndexOf('/');
                        if (lastSlash < 0) {
                            continue;
                        }
                        String filename = THUMBNAIL_PATH + item.icon.substring(lastSlash);
                        File file = new File(filename);
                        if (!file.exists()) {
                            if (download(filename, item.icon) < 0) {
                                continue;
                            }
                        }
                        if (mCallback != null) {
                            Log.d(TAG, "mCallback " + item.name);
                            mCallback.onReady(item);
                        }
                        // TODO will need that to prune the cache
                        //file.setLastModified(System.currentTimeMillis());
                    } catch (Exception e) {
                        Log.e(TAG, "Loader: ", e);
                    }
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "We are done", e);
            }
            sLoadersRunning--;
        }
    }

}
