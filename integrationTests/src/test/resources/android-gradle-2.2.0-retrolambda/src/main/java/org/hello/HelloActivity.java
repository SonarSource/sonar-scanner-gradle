package org.hello;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class HelloActivity extends Activity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.hello_layout);
  }

  @Override
  public void onStart() {
    super.onStart();
    TextView textView = (TextView) findViewById(R.id.text_view);
    textView.setText("The current local time is: " + Util.time());
    textView.setOnClickListener(view -> ((TextView) view).setText("The current local time is: " + Util.time()));
  }

}