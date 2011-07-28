package au.edu.melbuni.boldapp;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;

public class RecordActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set view.
        //
        setContentView(R.layout.base);
        
        // Configure view.
        //
        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        RelativeLayout menu = (RelativeLayout) findViewById(R.id.menu);
        
        // Menu.
        //
     	menu.addView(layoutInflater.inflate(R.layout.navigation, menu, false), 0);
     	menu.addView(layoutInflater.inflate(R.layout.user, menu, false), 1);
     	menu.addView(layoutInflater.inflate(R.layout.help, menu, false), 2);
     	
     	// Content.
     	//
     	
    }
}
