package com.ex2tools.iv.status;

import android.app.Activity;
import android.os.Bundle;

public final class TripResetActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TripResetService.start(this);
        finish();
    }
}
