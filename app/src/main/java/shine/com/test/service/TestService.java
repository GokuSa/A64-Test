package shine.com.test.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import shine.com.test.utils.RootCommand;


/**
 *
 */
public class TestService extends IntentService {
    private static final String TAG = "TestService";

    public static final String ACTION_UNINSTALL = "action_uninstall";
    public static final String ACTION_GRANT = "action_grant";
    public static final String ACTION_CREATE_FILE = "action_create_file";
    /**
     * 给串口授权
     */
    public static final String ACTION_CHANGE_FILE = "action_change_file";
    public static final String ACTION_OPEN_WEB_SETTING = "openWebSetting";
    public static final String ACTION_CLOSE_WEB_SETTING = "closeWebSetting";
    //门头灯
    public static final String ACTION_LIGHT = "action_light";
    public static final String PARAM1 = "param1";
    public static final String PARAM2 = "param2";

    public TestService() {
        super("TestService");
    }


    public static Intent getIntent(Context context, String action) {
        Intent intent = new Intent(context, TestService.class);
        intent.setAction(action);
        return intent;
    }

    public static void start(Context context, String action, String param) {
        Intent intent = new Intent(context, TestService.class);
        intent.setAction(action);
        intent.putExtra(PARAM1, param);
        context.startService(intent);
    }
    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UNINSTALL.equals(action)) {
                new RootCommand().executeCommand("pm uninstall shine.com.doorscreen");
            } else if (ACTION_GRANT.equals(action)) {
                String path = intent.getStringExtra(PARAM1);
                grant(path);
            }  else if (ACTION_CREATE_FILE.equals(action)) {
                createAutoUpdateConfig();
            } else if (ACTION_CHANGE_FILE.equals(action)) {
                modifyFile();
            } else if (ACTION_OPEN_WEB_SETTING.equals(action)) {
                openWebSetting();
            } else if (ACTION_CLOSE_WEB_SETTING.equals(action)) {
                closeWebSetting();
            }
        }
    }

    private void grant(String path) {
        Log.d(TAG, "grant() called with: path = [" + path + "]");
        RootCommand rootCommand=new RootCommand();
        boolean result=rootCommand.executeCommand("chmod 666 " + path);
        //result 只表示是否正确执行命令 不表示授权结果
        Log.d(TAG, "result:" + result);
        Intent intent = new Intent("com.android.test.serial");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

    }

    private void openWebSetting() {
        Log.d(TAG, "openWebSetting() called");
        RootCommand rootCommand=new RootCommand();
        rootCommand.executeCommands("/system/var/reload_fcgi.sh &","/system/var/reload_lighttpd.sh &");
    }

    private void closeWebSetting() {
        Log.d(TAG, "closeWebSetting() called");
        RootCommand rootCommand=new RootCommand();
        String killOne = "kill -9 `ps | busybox grep php | busybox grep -v grep | busybox awk '{print $2}'` &";
        String killTwo = "kill -9 `ps | busybox grep lighttpd | busybox grep -v grep | busybox awk '{print $2}'` &";
        rootCommand.executeCommands(killOne,killTwo);
    }
    private void createAutoUpdateConfig() {
        File file = new File("/extdata/work/config/autoupdate.xml");
        if (!file.exists()) {
            File parentFile = file.getParentFile();
            //如果文件不存在，看看父文件是否存在，如果不存在创建父文件夹
            RootCommand rootCommand=new RootCommand();
            if (!parentFile.exists()) {
                String cmd1="mkdir -p "+parentFile.getAbsolutePath();
                String cmd2="busybox chmod 777  "+parentFile.getAbsolutePath();
                rootCommand.executeCommands(cmd1, cmd2);
                //开始写入内容
                writeConfig(file);
            }else{
                //父目录存在直接写入内容
                writeConfig(file);
            }
        }
    }

    private void writeConfig(File file) {
        BufferedWriter bw=null;
        try {
            boolean success = file.createNewFile();
            Log.d(TAG, "创建自动更新配置文件结果:" + success);
            if (success) {
                bw = new BufferedWriter(new FileWriter(file));
                bw.write("<?xml version='1.0' encoding='utf-8' ?>");
                bw.newLine();
                bw.write("<group>");
                bw.newLine();
                bw.write(String.format("<enable>%s</enable>", "1"));
                bw.newLine();
                bw.write("</group>");
                bw.newLine();
                bw.flush();
            }
        } catch (IOException e) {
            Log.d(TAG, "创建自动更新配置文件IOException:" );
        }finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 修改配置文件,
    private void modifyFile() {
        File file = new File("/extdata/work/config/autoupdate.xml");
        if (!file.exists()) {
            return;
        }
        RandomAccessFile ras = null;
//        char auto=b?'1':'0';
        try {
            ras = new RandomAccessFile(file, "rw");
            byte[] buffer = new byte[1024];
            int len = ras.read(buffer);
            String result = new String(buffer, 0, len);
            int index = result.indexOf("enable");
            System.out.println("index " + index);
            ras.seek(index + 7);
            ras.writeByte('0');
        } catch (IOException e) {
            Log.d(TAG, "fail to writie ");

        } finally {
            if (ras != null) {
                try {
                    ras.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called");
    }
}
