package com.dji.GSDemo.GoogleMap;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private View.OnClickListener clickListener = v -> {
        switch (v.getId()) {
            case R.id.btn_waypoint1:
                startActivity(MainActivity.this, Waypoint1Activity.class);
                break;
            case R.id.btn_waypoint2:
                startActivity(MainActivity.this, Waypoint2Activity.class);
                break;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(MainActivity.this, Waypoint1Activity.class);
    }

    public static void startActivity(Context context, Class activity) {
        Intent intent = new Intent(context, activity);
        context.startActivity(intent);
    }
}
