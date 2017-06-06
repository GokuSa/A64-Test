package shine.com.test.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Xml;
import android.view.View;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import butterknife.ButterKnife;
import butterknife.OnClick;
import shine.com.test.R;

/**
 * 使用xml文件代替数据库存储结构化数据
 */
public class XmlActivity extends AppCompatActivity {
    private static final String TAG = "XmlActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xml);
        ButterKnife.bind(this);

    }


    @OnClick({R.id.btn_create_xml, R.id.modify_xml})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_create_xml:
                try {
                    create();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.modify_xml:
                break;
        }
    }


    private void create() throws IOException {
        File file = new File(getCacheDir(), "lxl.xml");
        Log.d(TAG, "file path " + file.getAbsolutePath());
        if (!file.exists()) {
            FileOutputStream fos = new FileOutputStream(file);
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(fos, StandardCharsets.UTF_8.name());
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startDocument(null, true);
            serializer.startTag(null, "settings");
            serializer.attribute(null, "version", "20");
            serializer.startTag(null, "setting");
            serializer.attribute(null, "id", "1");
            serializer.attribute(null, "name", "lxl");
            serializer.attribute(null, "package", "com.android.hei");
            serializer.endTag(null, "setting");
            serializer.endTag(null, "settings");
            serializer.endDocument();
            serializer.flush();
        } else {
            Log.d(TAG, "file already exists");
        }

    }


}
