package shine.com.test.applist;

import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import shine.com.test.R;

public class AppListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);
        FragmentManager fm = getFragmentManager();
        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(R.id.activity_app_list) == null) {
            AppListFragment list = new AppListFragment();
            fm.beginTransaction().add(R.id.activity_app_list, list).commit();
        }
    }
}
