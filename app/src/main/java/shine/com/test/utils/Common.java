package shine.com.test.utils;

import android.util.Log;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Created by 李晓林 on 2016/12/6
 * qq:1220289215
 */

public class Common {
    private static final String TAG = "Common";

    public static void getAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            if (networkInterfaces != null) {
                while (networkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaces.nextElement();
                    Log.d(TAG, "networkInterface name "+networkInterface.getName());
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    if (!inetAddresses.hasMoreElements()) {
                        Log.d(TAG, "no address for this interface");
                    }
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress = inetAddresses.nextElement();
                        String prefix;
                        if (inetAddress instanceof Inet4Address) {
                            prefix = "v4:";
                        } else if (inetAddress instanceof Inet6Address) {
                            prefix="v6:";
                        }else {
                            prefix="?";
                        }
                        Log.d(TAG, prefix + inetAddress.getHostAddress());
                    }
                }
            }else{
                Log.d(TAG, "no interface found");
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public static byte[] hex2Bytes(String src){
        byte[] res = new byte[src.length()/2];
        char[] chs = src.toCharArray();
        int[] b = new int[2];

        for(int i=0,c=0; i<chs.length; i+=2,c++){
            for(int j=0; j<2; j++){
                if(chs[i+j]>='0' && chs[i+j]<='9'){
                    b[j] = (chs[i+j]-'0');
                }else if(chs[i+j]>='A' && chs[i+j]<='F'){
                    b[j] = (chs[i+j]-'A'+10);
                }else if(chs[i+j]>='a' && chs[i+j]<='f'){
                    b[j] = (chs[i+j]-'a'+10);
                }
            }
            b[0] = (b[0]&0x0f)<<4;
            b[1] = (b[1]&0x0f);
            res[c] = (byte) (b[0] | b[1]);
        }

        return res;
    }


}
